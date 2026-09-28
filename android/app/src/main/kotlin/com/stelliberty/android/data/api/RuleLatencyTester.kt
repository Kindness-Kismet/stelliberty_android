package com.stelliberty.android.data.api

import com.stelliberty.android.data.api.RuleLatencyTester.Companion.Failed
import com.stelliberty.android.data.api.RuleLatencyTester.Companion.Unavailable
import com.stelliberty.android.data.repository.SubscriptionProxyResolver
import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.http.Url
import kotlin.time.TimeSource

class RuleLatencyTester(
    private val proxyResolver: SubscriptionProxyResolver,
) {
    // 每次都新建客户端，复用连接池会让反复刷新的结果一次比一次偏低；同时禁止跟随跳转，
    // 否则会把跳转的往返也算进耗时。测不出来时由调用方降级，绝不给一个看着像真的错数字。
    suspend fun measure(url: String, timeoutMillis: Long): Int {
        val proxyUrl = proxyResolver.resolve() ?: return Unavailable
        val client = buildClient(proxyUrl, timeoutMillis)
        return try {
            val start = TimeSource.Monotonic.markNow()
            client.get(url)
            start.elapsedNow().inWholeMilliseconds.toInt()
        } catch (_: Throwable) {
            Failed
        } finally {
            client.close()
        }
    }

    private fun buildClient(proxyUrl: String, timeoutMillis: Long) = HttpClient {
        install(HttpTimeout) {
            connectTimeoutMillis = timeoutMillis
            requestTimeoutMillis = timeoutMillis
        }
        followRedirects = false
        engine { proxy = ProxyBuilder.http(Url(proxyUrl)) }
    }

    companion object {
        const val Unavailable = -2

        const val Failed = -1
    }
}
