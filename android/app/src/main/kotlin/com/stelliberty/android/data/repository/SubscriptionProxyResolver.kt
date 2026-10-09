package com.stelliberty.android.data.repository

import com.stelliberty.android.data.api.MihomoApiClient
import com.stelliberty.android.domain.model.SubscriptionUpdateProxyMode
import com.stelliberty.android.platform.ProxyServiceBridge
import com.stelliberty.android.platform.ProxyServiceStatus
import com.stelliberty.android.platform.ProxyState
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI

class SubscriptionProxyResolver(
    private val overrideStore: OverrideJsonStore,
) {
    // 与 PC 相同按 UpdateProxyMode 选出口；系统代理与核心代理都不可用时直连。
    suspend fun resolveForSubscription(mode: SubscriptionUpdateProxyMode, url: String): String? = when (mode) {
        SubscriptionUpdateProxyMode.Direct -> null
        SubscriptionUpdateProxyMode.SystemProxy -> systemProxy(url)
        SubscriptionUpdateProxyMode.Core -> resolve()
    }

    suspend fun resolve(): String? {
        val bridge = ProxyServiceBridge.state.value
        if (bridge.state != ProxyState.Running) return null

        val port = overrideStore.load().mixedPort
            ?: queryMixedPortFromApi(bridge)
        if (port == null || port <= 0) return null
        return "http://127.0.0.1:$port"
    }

    // 系统按本应用的默认网络下发代理（Wi-Fi 手动代理 / PAC / 全局代理）；本应用不走自己的 VPN，VPN 上的 HTTP 代理不在其中。
    private fun systemProxy(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val address = ProxySelector.getDefault()?.select(uri)
            ?.firstOrNull { it.type() == Proxy.Type.HTTP }
            ?.address() as? InetSocketAddress
            ?: return null
        val host = address.hostString.let { if (':' in it) "[$it]" else it }
        return "http://$host:${address.port}"
    }

    private suspend fun queryMixedPortFromApi(bridge: ProxyServiceStatus): Int? {
        val baseUrl = "http://${bridge.externalController}"
        val client = MihomoApiClient(baseUrl = baseUrl, secret = bridge.secret)
        return try {
            client.getConfig().mixedPort.takeIf { it > 0 }
        } catch (_: Throwable) {
            null
        } finally {
            client.close()
        }
    }
}
