package com.stelliberty.android.data.repository

import com.stelliberty.android.domain.model.AppUpdateInfo
import com.stelliberty.android.domain.model.AppUpdateResult
import com.stelliberty.android.domain.model.UpdateChannel
import com.stelliberty.android.domain.repository.AppUpdateRepository
import com.stelliberty.android.util.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class AppUpdateRepositoryImpl(
    private val proxyResolver: SubscriptionProxyResolver,
) : AppUpdateRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun checkForUpdate(
        currentVersion: String,
        channel: UpdateChannel,
    ): AppUpdateResult = withContext(Dispatchers.IO) {
        try {
            val installedVersion = requireNotNull(parseVersion(currentVersion))
            val proxyUrl = proxyResolver.resolve()
            HttpClient(OkHttp) {
                engine { proxy = proxyUrl?.let { ProxyBuilder.http(Url(it)) } }
                install(HttpTimeout) {
                    connectTimeoutMillis = 10_000
                    socketTimeoutMillis = 10_000
                    requestTimeoutMillis = 15_000
                }
                defaultRequest {
                    header("Accept", "application/vnd.github+json")
                    header("User-Agent", "Stelliberty")
                }
            }.use { client ->
                // 稳定版单独查询，避免被大量测试版挤出发布列表首页。
                val stable = client.fetch("$RELEASES_URL/latest", allowMissing = true)
                    ?.let { json.decodeFromString<GitHubRelease>(it) }
                    ?.takeUnless { it.prerelease }
                    ?.toRemoteRelease()
                    ?.takeIf { it.version.betaNumber == null }
                val candidate = when (channel) {
                    UpdateChannel.STABLE -> stable
                    UpdateChannel.TEST -> {
                        val releases = json.decodeFromString<List<GitHubRelease>>(
                            requireNotNull(client.fetch("$RELEASES_URL?per_page=100")),
                        ).mapNotNull { it.toRemoteRelease() }
                        (releases + listOfNotNull(stable)).maxByOrNull { it.version }
                    }
                }
                requireNotNull(candidate) { "No published release for channel $channel" }
                if (candidate.version > installedVersion) {
                    AppUpdateResult.Available(candidate.info)
                } else {
                    AppUpdateResult.UpToDate
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.error("AppUpdate", "Failed to check for updates", e)
            AppUpdateResult.Failed
        }
    }

    private suspend fun HttpClient.fetch(url: String, allowMissing: Boolean = false): String? {
        val response = get(url)
        if (allowMissing && response.status == HttpStatusCode.NotFound) return null
        check(response.status == HttpStatusCode.OK) { "Unexpected release response: ${response.status}" }
        return response.bodyAsText()
    }

    private companion object {
        const val RELEASES_URL = "https://api.github.com/repos/Kindness-Kismet/stelliberty_android/releases"
    }
}

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
    val body: String?,
    val draft: Boolean,
    val prerelease: Boolean,
) {
    fun toRemoteRelease(): RemoteRelease? {
        if (draft) return null
        val version = parseVersion(tagName) ?: return null
        return RemoteRelease(
            version = version,
            info = AppUpdateInfo(tagName.removePrefix("v"), htmlUrl, body.orEmpty()),
        )
    }
}

private data class RemoteRelease(val version: ParsedVersion, val info: AppUpdateInfo)

private data class ParsedVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val betaNumber: Int?,
) : Comparable<ParsedVersion> {
    override fun compareTo(other: ParsedVersion): Int {
        val core = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
        if (core != 0) return core
        // 同版本稳定版高于测试版，测试序号按数值比较。
        return when {
            betaNumber == null && other.betaNumber == null -> 0
            betaNumber == null -> 1
            other.betaNumber == null -> -1
            else -> betaNumber.compareTo(other.betaNumber)
        }
    }
}

private fun parseVersion(raw: String): ParsedVersion? {
    val match = VERSION_PATTERN.matchEntire(raw.removePrefix("v")) ?: return null
    val beta = match.groupValues[4].takeIf { it.isNotEmpty() }
    return ParsedVersion(
        major = match.groupValues[1].toIntOrNull() ?: return null,
        minor = match.groupValues[2].toIntOrNull() ?: return null,
        patch = match.groupValues[3].toIntOrNull() ?: return null,
        betaNumber = if (beta != null) beta.toIntOrNull() ?: return null else null,
    )
}

private val VERSION_PATTERN = Regex("""(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-beta([1-9]\d*))?""")
