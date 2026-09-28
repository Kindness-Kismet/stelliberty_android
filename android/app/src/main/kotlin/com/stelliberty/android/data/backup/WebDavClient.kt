package com.stelliberty.android.data.backup

import android.util.Base64
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.isSuccess
import io.ktor.util.cio.readChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import java.io.File
import java.io.InputStream
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element

class WebDavException(message: String) : Exception(message)

data class RemoteBackup(val name: String, val lastModified: Instant?)

// 远端目录、文件名与保留份数都与 PC 默认值一致，两端的备份在同一目录里轮换。
class WebDavClient(
    baseUrl: String,
    username: String,
    password: String,
) {
    private val root = baseUrl.trim().trimEnd('/')

    private val dirUrl = "$root/$BACKUP_DIR/"
    private val authorization = "Basic " + Base64.encodeToString(
        "$username:$password".toByteArray(Charsets.UTF_8), Base64.NO_WRAP,
    )

    // 目录地址一律带结尾斜杠：不少服务器会把不带斜杠的目录请求跳转到带斜杠的版本，
    // 而创建目录和上传这两种请求收到跳转会直接失败。
    private fun buildClient() = HttpClient(OkHttp) {
        expectSuccess = false
        followRedirects = false
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 300_000
        }
    }

    // 上传后按修改时间只留最近 RETENTION 份，PC 上传的也计入。
    suspend fun upload(file: File, name: String) {
        buildClient().use { client ->
            client.ensureDir()
            val resp = client.davRequest(fileUrl(name), HttpMethod.Put, payload = file)
            if (!resp.status.isSuccess()) {
                throw WebDavException("PUT failed: HTTP ${resp.status.value}")
            }
            client.list().drop(RETENTION).forEach { client.delete(it.name) }
        }
    }

    // 取修改时间最新的一份；远端没有备份时返回 false。
    suspend fun downloadLatest(target: File): Boolean {
        buildClient().use { client ->
            val latest = client.list().firstOrNull() ?: return false
            val resp = client.davRequest(fileUrl(latest.name), HttpMethod.Get)
            if (!resp.status.isSuccess()) {
                throw WebDavException("GET failed: HTTP ${resp.status.value}")
            }
            withContext(Dispatchers.IO) {
                resp.bodyAsChannel().toInputStream().use { input ->
                    target.outputStream().use { input.copyTo(it) }
                }
            }
            return true
        }
    }

    suspend fun testConnection() {
        buildClient().use { client ->
            client.ensureDir()
        }
    }

    private suspend fun HttpClient.ensureDir() {
        val resp = davRequest(dirUrl, HttpMethod("MKCOL"))
        val ok = resp.status.isSuccess() || resp.status == HttpStatusCode.MethodNotAllowed
        if (!ok) {
            val hint = when (resp.status) {
                HttpStatusCode.Unauthorized -> "unauthorized"
                HttpStatusCode.Conflict -> "parent directory missing"
                else -> "HTTP ${resp.status.value}"
            }
            throw WebDavException("MKCOL failed: $hint")
        }
    }

    // 目录还没建过时服务器回 404，按没有备份处理。
    private suspend fun HttpClient.list(): List<RemoteBackup> {
        val resp = davRequest(dirUrl, HttpMethod("PROPFIND"), depth = "1")
        return when {
            resp.status == HttpStatusCode.NotFound -> emptyList()
            resp.status.isSuccess() -> withContext(Dispatchers.IO) {
                resp.bodyAsChannel().toInputStream().use { parseBackupList(it) }
            }

            else -> throw WebDavException("PROPFIND failed: HTTP ${resp.status.value}")
        }
    }

    private suspend fun HttpClient.delete(name: String) {
        val resp = davRequest(fileUrl(name), HttpMethod.Delete)
        if (!resp.status.isSuccess() && resp.status != HttpStatusCode.NotFound) {
            throw WebDavException("DELETE failed: HTTP ${resp.status.value}")
        }
    }

    private fun fileUrl(name: String) = dirUrl + URLEncoder.encode(name, "UTF-8").replace("+", "%20")

    // 手动跟随跳转并保持原来的请求方法和内容体，而且只跟同一个主机的：
    // 请求头里带着账号密码，跨主机跟过去就等于把密码交给了别人的服务器。
    private suspend fun HttpClient.davRequest(
        url: String,
        httpMethod: HttpMethod,
        payload: File? = null,
        depth: String? = null,
    ): HttpResponse {
        var current = url
        var hops = 0
        while (true) {
            val resp = request(current) {
                method = httpMethod
                header(HttpHeaders.Authorization, authorization)
                if (depth != null) header("Depth", depth)
                if (payload != null) setBody(payload.fileContent())
            }
            if (resp.status.value !in REDIRECT_CODES || hops >= MAX_REDIRECTS) return resp
            val location = resp.headers[HttpHeaders.Location] ?: return resp
            val next = runCatching { URI(current).resolve(location) }.getOrNull() ?: return resp
            if (!next.carriesCredentialsSafelyFrom(URI(current))) return resp
            current = next.toString()
            hops++
        }
    }

    private fun File.fileContent(): OutgoingContent = object : OutgoingContent.ReadChannelContent() {
        override val contentType = ContentType.Application.OctetStream
        override val contentLength = length()
        override fun readFrom(): ByteReadChannel = readChannel()
    }

    private fun URI.carriesCredentialsSafelyFrom(from: URI): Boolean {
        if (host == null || !host.equals(from.host, ignoreCase = true)) return false
        val fromHttps = from.scheme.equals("https", ignoreCase = true)
        val toHttps = scheme.equals("https", ignoreCase = true)
        return toHttps || !fromHttps
    }

    companion object {
        const val BACKUP_DIR = "stelliberty-backups"
        private const val RETENTION = 5
        private val REDIRECT_CODES = setOf(301, 302, 307, 308)
        private const val MAX_REDIRECTS = 3
    }
}

// PROPFIND 多状态应答 → 备份文件列表，按修改时间从新到旧、同时间按文件名倒序，与 PC 的排序一致。
// 元素按本地名匹配，不同服务器的命名空间前缀各不相同。
internal fun parseBackupList(input: InputStream): List<RemoteBackup> {
    val document = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(input)
    val responses = document.getElementsByTagNameNS("*", "response")
    return (0 until responses.length)
        .mapNotNull { (responses.item(it) as Element).toRemoteBackup() }
        .sortedWith(compareByDescending<RemoteBackup> { it.lastModified }.thenByDescending { it.name })
}

private fun Element.toRemoteBackup(): RemoteBackup? {
    if (getElementsByTagNameNS("*", "collection").length > 0) return null
    val href = text("href") ?: return null
    // href 里的 + 是字面量，URLDecoder 会把它当空格。
    val name = runCatching {
        URLDecoder.decode(href.trimEnd('/').substringAfterLast('/').replace("+", "%2B"), "UTF-8")
    }.getOrNull() ?: return null
    if (!name.endsWith(BackupManager.BACKUP_EXTENSION)) return null
    val lastModified = text("getlastmodified")?.let {
        runCatching { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull()
    }
    return RemoteBackup(name, lastModified)
}

private fun Element.text(localName: String): String? =
    getElementsByTagNameNS("*", localName).item(0)?.textContent?.trim()
