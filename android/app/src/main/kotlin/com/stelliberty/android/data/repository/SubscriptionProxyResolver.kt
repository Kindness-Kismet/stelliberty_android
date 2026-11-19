package com.stelliberty.android.data.repository

import com.stelliberty.android.data.api.MihomoApiClient
import com.stelliberty.android.domain.model.SubscriptionUpdateProxyMode
import com.stelliberty.android.platform.ProxyServiceBridge
import com.stelliberty.android.platform.ProxyServiceStatus
import com.stelliberty.android.platform.ProxyState
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SubscriptionProxyResolver {
    // 核心已停止时允许直连；运行中无法确认出口则中止，避免把订阅请求发到错误的网络。
    suspend fun resolveForSubscription(mode: SubscriptionUpdateProxyMode, url: String): String? = when (mode) {
        SubscriptionUpdateProxyMode.Direct -> null
        SubscriptionUpdateProxyMode.SystemProxy -> withContext(Dispatchers.IO) { systemProxy(url) }
        SubscriptionUpdateProxyMode.Core -> resolveCoreSubscription()
    }

    private suspend fun resolveCoreSubscription(): String? {
        val bridge = ProxyServiceBridge.state.value
        if (bridge.state == ProxyState.Stopped || bridge.state == ProxyState.Error) return null
        if (bridge.state != ProxyState.Running) throw IOException("Core proxy is changing state")
        val port = queryMixedPortFromApi(bridge)
        if (port <= 0) throw IOException("Core mixed-port is disabled")
        return "http://127.0.0.1:$port"
    }

    suspend fun resolve(): String? {
        val bridge = ProxyServiceBridge.state.value
        if (bridge.state != ProxyState.Running) return null

        val port = try {
            queryMixedPortFromApi(bridge)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return null
        }
        if (port <= 0) return null
        return "http://127.0.0.1:$port"
    }

    // 系统按本应用的默认网络下发代理（Wi-Fi 手动代理 / PAC / 全局代理）；本应用不走自己的 VPN，VPN 上的 HTTP 代理不在其中。
    private fun systemProxy(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val address = ProxySelector.getDefault()?.select(uri)
            ?.firstOrNull()?.takeIf { it.type() == Proxy.Type.HTTP }
            ?.address() as? InetSocketAddress
            ?: return null
        val host = address.hostString.let { if (':' in it) "[$it]" else it }
        return "http://$host:${address.port}"
    }

    private suspend fun queryMixedPortFromApi(bridge: ProxyServiceStatus): Int {
        val baseUrl = "http://${bridge.externalController}"
        val client = MihomoApiClient(baseUrl = baseUrl, secret = bridge.secret)
        return try {
            client.getConfig(timeoutMillis = 3_000).mixedPort
        } finally {
            client.close()
        }
    }
}
