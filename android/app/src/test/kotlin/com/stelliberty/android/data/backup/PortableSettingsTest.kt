package com.stelliberty.android.data.backup

import com.stelliberty.android.domain.model.ConfigurationOverride
import com.stelliberty.android.domain.model.DnsOverride
import com.stelliberty.android.platform.StorageKeys
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class PortableSettingsTest {

    private val android = ConfigurationOverride(
        mode = "global",
        mixedPort = 7891,
        httpPort = 7892,
        socksPort = 7893,
        allowLan = true,
        ipv6 = false,
        tcpConcurrent = true,
        unifiedDelay = true,
        findProcessMode = "strict",
        logLevel = "warning",
        dns = DnsOverride(
            enable = true,
            listen = "0.0.0.0:1053",
            ipv6 = false,
            preferH3 = true,
            useHosts = false,
            enhancedMode = "redir-host",
            nameserver = listOf("https://dns.example.com/dns-query"),
            fallback = listOf("tls://dns.example.org"),
            defaultNameserver = listOf("223.5.5.5"),
            fakeIpFilter = listOf("+.example.com"),
        ),
    )

    private val prefs = mapOf(
        StorageKeys.DARK_MODE to "dark",
        StorageKeys.THEME_MONET to "true",
        StorageKeys.PROXY_NODE_SORT_OPTION to "5",
    )

    @Test
    fun exportThenImportRestoresMappedSettings() {
        val exported = PortableSettings.export(android, prefs)
        val imported = PortableSettings.import(exported, ConfigurationOverride(), emptyMap())

        assertEquals(android, imported.override)
        assertEquals(
            mapOf(
                StorageKeys.DARK_MODE to "dark",
                StorageKeys.THEME_MONET to "true",
                StorageKeys.PROXY_NODE_SORT_OPTION to "4",
            ),
            imported.prefs,
        )
    }

    @Test
    fun exportUsesPcKeysAndValues() {
        val exported = PortableSettings.export(android, prefs)

        assertEquals("\"Dark\"", exported[PortableSettings.THEME].toString())
        assertEquals("\"System\"", exported[PortableSettings.ACCENT_COLOR_MODE].toString())
        assertEquals("\"Delay\"", exported[PortableSettings.PROXY_NODE_SORT_MODE].toString())
        assertEquals("\"Global\"", exported[PortableSettings.OUTBOUND_MODE].toString())
        assertEquals("7891", exported[PortableSettings.MIXED_PORT].toString())
        assertEquals("true", exported[PortableSettings.DNS_OVERRIDE].toString())
    }

    @Test
    fun exportOmitsSettingsThatFollowTheSubscription() {
        val exported = PortableSettings.export(ConfigurationOverride(), emptyMap())

        assertEquals(setOf(PortableSettings.DNS_OVERRIDE), exported.keys)
        assertEquals("false", exported[PortableSettings.DNS_OVERRIDE].toString())
    }

    @Test
    fun importReadsPcDefaults() {
        val settings = Json.parseToJsonElement(
            """
            {
              "Theme": "System",
              "AccentColorMode": "Custom",
              "ProxyNodeSortMode": "Name",
              "OutboundMode": "Rule",
              "MixedPort": 8888,
              "HttpPort": null,
              "SocksPort": 0,
              "IsAllowLanEnabled": false,
              "IsIpv6Enabled": false,
              "IsTcpConcurrentEnabled": false,
              "IsUnifiedDelayEnabled": false,
              "FindProcessMode": "off",
              "CoreLogLevel": "silent",
              "IsDnsOverrideEnabled": false,
              "IsDnsEnabled": true,
              "GeoDataLoader": "standard",
              "ExternalControllerAddress": "127.0.0.1:9090"
            }
            """.trimIndent()
        ).jsonObject
        val current = ConfigurationOverride(
            httpPort = 7892,
            socksPort = 7893,
            externalController = "127.0.0.1:9091",
            dns = DnsOverride(enable = true),
        )

        val imported = PortableSettings.import(settings, current, mapOf(StorageKeys.PROXY_NODE_SORT_OPTION to "1"))

        assertEquals(
            ConfigurationOverride(
                mode = "rule",
                mixedPort = 8888,
                allowLan = false,
                ipv6 = false,
                tcpConcurrent = false,
                unifiedDelay = false,
                findProcessMode = "off",
                logLevel = "silent",
                externalController = "127.0.0.1:9091",
            ),
            imported.override,
        )
        assertEquals(
            mapOf(
                StorageKeys.DARK_MODE to "system",
                StorageKeys.THEME_MONET to "false",
                StorageKeys.PROXY_NODE_SORT_OPTION to "3",
            ),
            imported.prefs,
        )
    }

    @Test
    fun importKeepsCurrentValuesForMissingOrUnknownKeys() {
        val settings = Json.parseToJsonElement(
            """{"Theme": "Sepia", "OutboundMode": "Script", "CoreLogLevel": "trace", "MixedPort": 70000}"""
        ).jsonObject

        val imported = PortableSettings.import(settings, android, prefs)

        assertEquals(android, imported.override)
        assertFalse(imported.prefs.containsKey(StorageKeys.DARK_MODE))
        assertNull(imported.prefs[StorageKeys.PROXY_NODE_SORT_OPTION])
    }
}
