package com.stelliberty.android.ui.platform

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.stelliberty.android.ui.platform.IconLoader.FAILURE_RETRY_INTERVAL
import com.stelliberty.android.ui.platform.IconLoader.setProxyResolver
import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.Url
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

// 图标地址多在境外而本应用流量永远绕开代理，运行中必须显式走 mihomo 的本机端口，否则图标加载极慢。
// 失败的地址一分钟内不再重试，免得列表滚回来又放一遍失败请求。
object IconLoader {

    private const val MAX_ENTRIES = 64
    private const val MAX_FAILURE_ENTRIES = 128
    private const val MAX_PARALLEL_DOWNLOADS = 3
    private val FAILURE_RETRY_INTERVAL = 60.seconds
    private val PROXY_RESOLVE_CACHE_TTL = 10.seconds

    private val semaphore = Semaphore(MAX_PARALLEL_DOWNLOADS)
    private val mutex = Mutex()

    private val cache = LinkedHashMap<String, ImageBitmap>()

    private val failures = LinkedHashMap<String, TimeMark>()

    private var proxyResolver: (suspend () -> String?)? = null

    private var resolvedProxy: Pair<TimeMark, String?>? = null

    private var directClient: HttpClient? = null
    private var proxyClient: Pair<String, HttpClient>? = null

    fun setProxyResolver(resolver: suspend () -> String?) {
        proxyResolver = resolver
    }

    suspend fun loadIcon(url: String): ImageBitmap? {
        if (url.isEmpty()) return null

        mutex.withLock {
            val hit = cache.remove(url)
            if (hit != null) {
                cache[url] = hit
                return hit
            }
            val failedAt = failures[url]
            if (failedAt != null) {
                if (failedAt.elapsedNow() < FAILURE_RETRY_INTERVAL) return null
                failures.remove(url)
            }
        }

        return withContext(Dispatchers.IO) {
            val diskBytes = runCatching { IconDiskCache.get(url) }.getOrNull()
            if (diskBytes != null) {
                val bitmap = runCatching { diskBytes.decodeToImageBitmap() }.getOrNull()
                if (bitmap != null) {
                    put(url, bitmap)
                    return@withContext bitmap
                }
            }

            semaphore.withPermit {
                mutex.withLock { cache[url] }?.let { return@withPermit it }
                try {
                    val client = obtainClient()
                    val bytes = client.get(url).readRawBytes()
                    val bitmap = bytes.decodeToImageBitmap()
                    runCatching { IconDiskCache.put(url, bytes) }
                    put(url, bitmap)
                    bitmap
                } catch (_: Exception) {
                    markFailed(url)
                    null
                }
            }
        }
    }

    private suspend fun obtainClient(): HttpClient {
        val proxyUrl = resolveProxy()
        return mutex.withLock {
            if (proxyUrl == null) {
                directClient ?: buildClient(null).also { directClient = it }
            } else {
                val current = proxyClient
                if (current != null && current.first == proxyUrl) {
                    current.second
                } else {
                    current?.second?.close()
                    buildClient(proxyUrl).also { proxyClient = proxyUrl to it }
                }
            }
        }
    }

    private suspend fun resolveProxy(): String? {
        val resolver = proxyResolver ?: return null
        mutex.withLock {
            val cached = resolvedProxy
            if (cached != null && cached.first.elapsedNow() < PROXY_RESOLVE_CACHE_TTL) {
                return cached.second
            }
        }
        val resolved = runCatching { resolver() }.getOrNull()
        mutex.withLock { resolvedProxy = TimeSource.Monotonic.markNow() to resolved }
        return resolved
    }

    private fun buildClient(proxyUrl: String?) = HttpClient {
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            requestTimeoutMillis = 15_000
        }
        if (proxyUrl != null) {
            engine { proxy = ProxyBuilder.http(Url(proxyUrl)) }
        }
    }

    private suspend fun put(url: String, bitmap: ImageBitmap) {
        mutex.withLock {
            cache[url] = bitmap
            while (cache.size > MAX_ENTRIES) {
                val eldest = cache.keys.iterator().next()
                cache.remove(eldest)
            }
        }
    }

    private suspend fun markFailed(url: String) {
        mutex.withLock {
            failures.remove(url)
            failures[url] = TimeSource.Monotonic.markNow()
            while (failures.size > MAX_FAILURE_ENTRIES) {
                val eldest = failures.keys.iterator().next()
                failures.remove(eldest)
            }
        }
    }

    suspend fun clear() {
        mutex.withLock {
            cache.clear()
            failures.clear()
        }
        runCatching { withContext(Dispatchers.IO) { IconDiskCache.clear() } }
    }
}

private fun ByteArray.decodeToImageBitmap(): ImageBitmap =
    BitmapFactory.decodeByteArray(this, 0, size)?.asImageBitmap()
        ?: error("decode image failed")
