package com.stelliberty.android.data.backup

import com.stelliberty.android.domain.model.ConfigurationOverride
import com.stelliberty.android.domain.model.DnsOverride
import com.stelliberty.android.platform.StorageKeys
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

data class PortableSettingsImport(
    val override: ConfigurationOverride,
    val prefs: Map<String, String>,
)

// PC settings.json 的可迁移键与 Android 设置互转，只收两端语义一致的项。
// 覆写项为 null 表示跟随订阅，导出时省略，由 PC 保留自己的值；外部控制器不迁移，Android 自身靠它连内核。
object PortableSettings {

    fun export(override: ConfigurationOverride, prefs: Map<String, String>): JsonObject = buildJsonObject {
        prefs[StorageKeys.DARK_MODE]?.takeIf { it in THEMES }?.let { put(THEME, it.pascalCase()) }
        prefs[StorageKeys.THEME_MONET]?.let {
            put(ACCENT_COLOR_MODE, if (it == "true") ACCENT_SYSTEM else ACCENT_CUSTOM)
        }
        prefs[StorageKeys.PROXY_NODE_SORT_OPTION]?.toIntOrNull()
            ?.let { SORT_MODES.getOrNull(it / 2) }
            ?.let { put(PROXY_NODE_SORT_MODE, it) }

        override.mode?.takeIf { it in OUTBOUND_MODES }?.let { put(OUTBOUND_MODE, it.pascalCase()) }
        override.mixedPort?.let { put(MIXED_PORT, it) }
        override.httpPort?.let { put(HTTP_PORT, it) }
        override.socksPort?.let { put(SOCKS_PORT, it) }
        override.allowLan?.let { put(ALLOW_LAN, it) }
        override.ipv6?.let { put(IPV6, it) }
        override.tcpConcurrent?.let { put(TCP_CONCURRENT, it) }
        override.unifiedDelay?.let { put(UNIFIED_DELAY, it) }
        override.findProcessMode?.takeIf { it in FIND_PROCESS_MODES }?.let { put(FIND_PROCESS_MODE, it) }
        override.logLevel?.takeIf { it in LOG_LEVELS }?.let { put(CORE_LOG_LEVEL, it) }

        val dns = override.dns
        put(DNS_OVERRIDE, dns != null)
        if (dns != null) {
            dns.enable?.let { put(DNS_ENABLED, it) }
            dns.listen?.let { put(DNS_LISTEN, it) }
            dns.ipv6?.let { put(DNS_IPV6, it) }
            dns.preferH3?.let { put(DNS_PREFER_H3, it) }
            dns.useHosts?.let { put(DNS_USE_HOSTS, it) }
            dns.enhancedMode?.let { put(DNS_ENHANCED_MODE, it) }
            dns.nameserver?.let { putStrings(NAME_SERVERS, it) }
            dns.fallback?.let { putStrings(FALLBACK_NAME_SERVERS, it) }
            dns.defaultNameserver?.let { putStrings(DEFAULT_NAME_SERVERS, it) }
            dns.fakeIpFilter?.let { putStrings(FAKE_IP_FILTERS, it) }
        }
    }

    // 键缺失或取值超出 Android 的选项时保留当前值；枚举名按 PC 的解析方式忽略大小写。
    // PC 的端口 null 或非正数表示不开启。
    fun import(
        settings: JsonObject,
        override: ConfigurationOverride,
        prefs: Map<String, String>,
    ): PortableSettingsImport {
        val nextPrefs = mutableMapOf<String, String>()
        settings.string(THEME)?.lowercase()?.takeIf { it in THEMES }?.let { nextPrefs[StorageKeys.DARK_MODE] = it }
        when (settings.string(ACCENT_COLOR_MODE)?.lowercase()) {
            ACCENT_SYSTEM.lowercase() -> nextPrefs[StorageKeys.THEME_MONET] = "true"
            ACCENT_CUSTOM.lowercase() -> nextPrefs[StorageKeys.THEME_MONET] = "false"
        }
        settings.string(PROXY_NODE_SORT_MODE)
            ?.let { mode -> SORT_MODES.indexOfFirst { it.equals(mode, ignoreCase = true) } }
            ?.takeIf { it >= 0 }
            ?.let { key ->
            val reverse = (prefs[StorageKeys.PROXY_NODE_SORT_OPTION]?.toIntOrNull() ?: 0) % 2
            nextPrefs[StorageKeys.PROXY_NODE_SORT_OPTION] = (key * 2 + reverse).toString()
        }

        var next = override
        settings.string(OUTBOUND_MODE)?.lowercase()?.takeIf { it in OUTBOUND_MODES }
            ?.let { next = next.copy(mode = it) }
        settings.int(MIXED_PORT)?.takeIf { it in PORTS }?.let { next = next.copy(mixedPort = it) }
        if (HTTP_PORT in settings) next = next.copy(httpPort = settings.int(HTTP_PORT)?.takeIf { it in PORTS })
        if (SOCKS_PORT in settings) next = next.copy(socksPort = settings.int(SOCKS_PORT)?.takeIf { it in PORTS })
        settings.bool(ALLOW_LAN)?.let { next = next.copy(allowLan = it) }
        settings.bool(IPV6)?.let { next = next.copy(ipv6 = it) }
        settings.bool(TCP_CONCURRENT)?.let { next = next.copy(tcpConcurrent = it) }
        settings.bool(UNIFIED_DELAY)?.let { next = next.copy(unifiedDelay = it) }
        settings.string(FIND_PROCESS_MODE)?.takeIf { it in FIND_PROCESS_MODES }
            ?.let { next = next.copy(findProcessMode = it) }
        settings.string(CORE_LOG_LEVEL)?.takeIf { it in LOG_LEVELS }?.let { next = next.copy(logLevel = it) }

        when (settings.bool(DNS_OVERRIDE)) {
            true -> next = next.copy(dns = settings.dns().takeIf { it != DnsOverride() })
            false -> next = next.copy(dns = null)
            null -> Unit
        }
        return PortableSettingsImport(next, nextPrefs)
    }

    private fun JsonObject.dns() = DnsOverride(
        enable = bool(DNS_ENABLED),
        listen = string(DNS_LISTEN),
        ipv6 = bool(DNS_IPV6),
        preferH3 = bool(DNS_PREFER_H3),
        useHosts = bool(DNS_USE_HOSTS),
        enhancedMode = string(DNS_ENHANCED_MODE),
        nameserver = strings(NAME_SERVERS),
        fallback = strings(FALLBACK_NAME_SERVERS),
        defaultNameserver = strings(DEFAULT_NAME_SERVERS),
        fakeIpFilter = strings(FAKE_IP_FILTERS),
    )

    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.strings(key: String): List<String>? =
        (this[key] as? JsonArray)?.mapNotNull { element ->
            (element as? JsonPrimitive)?.takeIf { it.isString }?.content
        }

    private fun JsonObjectBuilder.putStrings(key: String, values: List<String>) {
        put(key, buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
    }

    private fun String.pascalCase() = replaceFirstChar { it.uppercaseChar() }

    private val THEMES = setOf("system", "light", "dark")
    private val OUTBOUND_MODES = setOf("rule", "global", "direct")
    private val FIND_PROCESS_MODES = setOf("off", "strict", "always")
    private val LOG_LEVELS = setOf("silent", "error", "warning", "info", "debug")
    // 下标即 Android 排序选项的 key（选项值 = key * 2 + 是否倒序）。
    private val SORT_MODES = listOf("Default", "Name", "Delay")
    private val PORTS = 1..65535
    private const val ACCENT_SYSTEM = "System"
    private const val ACCENT_CUSTOM = "Custom"

    const val THEME = "Theme"
    const val ACCENT_COLOR_MODE = "AccentColorMode"
    const val PROXY_NODE_SORT_MODE = "ProxyNodeSortMode"
    const val OUTBOUND_MODE = "OutboundMode"
    const val MIXED_PORT = "MixedPort"
    const val HTTP_PORT = "HttpPort"
    const val SOCKS_PORT = "SocksPort"
    const val ALLOW_LAN = "IsAllowLanEnabled"
    const val IPV6 = "IsIpv6Enabled"
    const val TCP_CONCURRENT = "IsTcpConcurrentEnabled"
    const val UNIFIED_DELAY = "IsUnifiedDelayEnabled"
    const val FIND_PROCESS_MODE = "FindProcessMode"
    const val CORE_LOG_LEVEL = "CoreLogLevel"
    const val DNS_OVERRIDE = "IsDnsOverrideEnabled"
    const val DNS_ENABLED = "IsDnsEnabled"
    const val DNS_LISTEN = "DnsListen"
    const val DNS_IPV6 = "IsDnsIpv6Enabled"
    const val DNS_PREFER_H3 = "IsDnsPreferH3Enabled"
    const val DNS_USE_HOSTS = "IsDnsUseHostsEnabled"
    const val DNS_ENHANCED_MODE = "DnsEnhancedMode"
    const val NAME_SERVERS = "NameServers"
    const val FALLBACK_NAME_SERVERS = "FallbackNameServers"
    const val DEFAULT_NAME_SERVERS = "DefaultNameServers"
    const val FAKE_IP_FILTERS = "FakeIpFilters"
}
