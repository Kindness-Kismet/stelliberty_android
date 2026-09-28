package com.stelliberty.android.data.repository

import com.stelliberty.android.data.api.MihomoApiClient
import com.stelliberty.android.domain.model.SubscriptionUpdateProxyMode
import com.stelliberty.android.platform.ProxyServiceBridge
import com.stelliberty.android.platform.ProxyServiceStatus
import com.stelliberty.android.platform.ProxyState

class SubscriptionProxyResolver(
    private val overrideStore: OverrideJsonStore,
) {
    // 订阅下载按该订阅的 UpdateProxyMode 决定；Direct 之外都经 mixed-port（Android 没有系统代理）。
    suspend fun resolveForSubscription(mode: SubscriptionUpdateProxyMode): String? =
        if (mode == SubscriptionUpdateProxyMode.Direct) null else resolve()

    suspend fun resolve(): String? {
        val bridge = ProxyServiceBridge.state.value
        if (bridge.state != ProxyState.Running) return null

        val port = overrideStore.load().mixedPort
            ?: queryMixedPortFromApi(bridge)
        if (port == null || port <= 0) return null
        return "http://127.0.0.1:$port"
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
