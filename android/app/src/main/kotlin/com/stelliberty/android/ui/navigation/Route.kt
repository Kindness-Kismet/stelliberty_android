package com.stelliberty.android.ui.navigation

import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey

// 整条导航栈靠序列化整体保存，新增页面只要加上可序列化标注就自动获得进程被杀后的恢复能力。
// 下面这行标注不能删：多态序列化器由基类注册，删掉照样编译，但恢复导航栈时会崩。
@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Main : Route

    @Serializable
    data object SubscriptionAdd : Route

    @Serializable
    data class SubscriptionAddUrl(
        val initialUrl: String = "",
        val initialName: String = "",
        val initialIntervalMinutes: Long = 0,
    ) : Route

    @Serializable
    data object Log : Route

    @Serializable
    data object Provider : Route

    @Serializable
    data object DnsQuery : Route

    @Serializable
    data object Connection : Route

    @Serializable
    data object ClashFeatures : Route

    @Serializable
    data object NetworkSettings : Route

    @Serializable
    data object PortControl : Route

    @Serializable
    data object SystemIntegration : Route

    @Serializable
    data object DnsSettings : Route

    @Serializable
    data object PerformanceSettings : Route

    @Serializable
    data object VpnSettings : Route

    @Serializable
    data object RootSettings : Route

    @Serializable
    data object AppBehavior : Route

    @Serializable
    data object FileManager : Route

    @Serializable
    data object DataManagement : Route

    @Serializable
    data object AgeKey : Route

    @Serializable
    data class FileManagerEditor(val uuid: String, val relativePath: String) : Route

    @Serializable
    data object AppProxy : Route

    @Serializable
    data object WifiPolicy : Route

    @Serializable
    data object ThemeSettings : Route

    @Serializable
    data class SubscriptionEdit(val uuid: String) : Route

    @Serializable
    data class SubscriptionOverrides(val uuid: String) : Route

    // session 标识一次进入：从编辑页返回时沿用未保存的改动，重新进入时从订阅读取。
    @Serializable
    data class SubscriptionChainProxies(val uuid: String, val session: String) : Route

    // chainId 为 null 时是添加。
    @Serializable
    data class ChainProxyEdit(val uuid: String, val chainId: String? = null) : Route

    // session 语义同 SubscriptionChainProxies。
    @Serializable
    data class SubscriptionRuleOverrides(val uuid: String, val session: String) : Route

    // ruleId 为 null 时是添加。
    @Serializable
    data class RuleOverrideEdit(val uuid: String, val ruleId: String? = null) : Route

    @Serializable
    data object OverrideList : Route

    // id 为 null 时是添加页。
    @Serializable
    data class OverrideEdit(val id: String? = null) : Route

    @Serializable
    data class OverrideFileEditor(val id: String) : Route

    @Serializable
    data object About : Route
}
