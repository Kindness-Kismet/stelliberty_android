package com.stelliberty.android.ui.navigation

import kotlinx.coroutines.flow.MutableSharedFlow

// 用可重放的事件流而不是状态流：同一个页面连开两次必须都生效，状态流会因为值相同而吞掉第二次。
object DebugNavBridge {

    val requests = MutableSharedFlow<String>(extraBufferCapacity = 8)

    fun request(pageId: String, waitMs: Long = COLLECTOR_WAIT_MS): Boolean {
        val deadline = System.currentTimeMillis() + waitMs
        while (requests.subscriptionCount.value == 0) {
            if (System.currentTimeMillis() >= deadline) return false
            Thread.sleep(COLLECTOR_POLL_MS)
        }
        return requests.tryEmit(pageId)
    }

    private const val COLLECTOR_WAIT_MS = 6_000L
    private const val COLLECTOR_POLL_MS = 25L

    fun routeOf(pageId: String): Route? = when (pageId) {
        "subscription.add" -> Route.SubscriptionAdd
        "log" -> Route.Log
        "provider" -> Route.Provider
        "dns" -> Route.DnsQuery
        "connection" -> Route.Connection
        "settings.theme" -> Route.ThemeSettings
        "settings.clash" -> Route.ClashFeatures
        "settings.network" -> Route.NetworkSettings
        "settings.port_control" -> Route.PortControl
        "settings.system_integration" -> Route.SystemIntegration
        "settings.dns" -> Route.DnsSettings
        "settings.performance" -> Route.PerformanceSettings
        "settings.vpn" -> Route.VpnSettings
        "settings.root" -> Route.RootSettings
        "settings.app_proxy" -> Route.AppProxy
        "settings.overrides" -> Route.OverrideList
        "settings.file_manager" -> Route.FileManager
        "settings.app_behavior" -> Route.AppBehavior
        "settings.wifi_policy" -> Route.WifiPolicy
        "settings.about" -> Route.About
        "settings.data" -> Route.DataManagement
        "settings.age_key" -> Route.AgeKey
        else -> null
    }

    fun mainTabIndexOf(pageId: String): Int? = when (pageId) {
        "home" -> 0
        "proxy" -> 1
        "subscription" -> 2
        "settings" -> 3
        else -> null
    }
}
