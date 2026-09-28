package com.stelliberty.android.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConfigurationOverride(
    @SerialName("port") val httpPort: Int? = null,
    @SerialName("socks-port") val socksPort: Int? = null,
    @SerialName("redir-port") val redirPort: Int? = null,
    @SerialName("tproxy-port") val tproxyPort: Int? = null,
    @SerialName("mixed-port") val mixedPort: Int? = null,
    @SerialName("routing-mark") val routingMark: Int? = null,
    @SerialName("allow-lan") val allowLan: Boolean? = null,
    @SerialName("ipv6") val ipv6: Boolean? = null,
    @SerialName("bind-address") val bindAddress: String? = null,
    @SerialName("log-level") val logLevel: String? = null,
    @SerialName("mode") val mode: String? = null,
    @SerialName("external-controller") val externalController: String? = null,
    @SerialName("secret") val secret: String? = null,
    @SerialName("unified-delay") val unifiedDelay: Boolean? = null,
    @SerialName("geodata-mode") val geodataMode: Boolean? = null,
    @SerialName("tcp-concurrent") val tcpConcurrent: Boolean? = null,
    @SerialName("find-process-mode") val findProcessMode: String? = null,
    @SerialName("dns") val dns: DnsOverride? = null,
    @SerialName("sniffer") val sniffer: SnifferOverride? = null,
    @SerialName("tun") val tun: TunOverride? = null,
    @SerialName("profile") val profile: ProfileOverride? = null,
)

@Serializable
data class DnsOverride(
    @SerialName("enable") val enable: Boolean? = null,
    @SerialName("listen") val listen: String? = null,
    @SerialName("ipv6") val ipv6: Boolean? = null,
    @SerialName("prefer-h3") val preferH3: Boolean? = null,
    @SerialName("use-hosts") val useHosts: Boolean? = null,
    @SerialName("enhanced-mode") val enhancedMode: String? = null,
    @SerialName("nameserver") val nameserver: List<String>? = null,
    @SerialName("fallback") val fallback: List<String>? = null,
    @SerialName("default-nameserver") val defaultNameserver: List<String>? = null,
    @SerialName("fake-ip-filter") val fakeIpFilter: List<String>? = null,
)

@Serializable
data class SnifferOverride(
    @SerialName("enable") val enable: Boolean? = null,
    @SerialName("force-dns-mapping") val forceDnsMapping: Boolean? = null,
    @SerialName("parse-pure-ip") val parsePureIp: Boolean? = null,
    @SerialName("override-destination") val overrideDestination: Boolean? = null,
    @SerialName("force-domain") val forceDomain: List<String>? = null,
    @SerialName("skip-domain") val skipDomain: List<String>? = null,
)

@Serializable
data class TunOverride(
    @SerialName("enable") val enable: Boolean? = null,
    @SerialName("device") val device: String? = null,
    @SerialName("stack") val stack: String? = null,
    @SerialName("file-descriptor") val fileDescriptor: Int? = null,
    @SerialName("auto-route") val autoRoute: Boolean? = null,
    @SerialName("auto-detect-interface") val autoDetectInterface: Boolean? = null,
    @SerialName("route-exclude-address") val routeExcludeAddress: List<String>? = null,
    @SerialName("inet6-address") val inet6Address: List<String>? = null,
    @SerialName("dns-hijack") val dnsHijack: List<String>? = null,
    @SerialName("include-package") val includePackage: List<String>? = null,
    @SerialName("exclude-package") val excludePackage: List<String>? = null,
    @SerialName("iproute2-table-index") val iproute2TableIndex: Int? = null,
    @SerialName("iproute2-rule-index") val iproute2RuleIndex: Int? = null,
    @SerialName("mtu") val mtu: Int? = null,
    @SerialName("gso") val gso: Boolean? = null,
    @SerialName("gso-max-size") val gsoMaxSize: Int? = null,
)

@Serializable
data class ProfileOverride(
    @SerialName("store-selected") val storeSelected: Boolean? = null,
    @SerialName("store-fake-ip") val storeFakeIp: Boolean? = null,
)

fun ConfigurationOverride.resolveExternalController(): String =
    (externalController?.trim()?.takeIf { it.isNotEmpty() } ?: "127.0.0.1:9090")
        .replace("0.0.0.0", "127.0.0.1")

fun ConfigurationOverride.resolveSecretOrNull(): String? =
    secret?.trim()?.takeIf { it.isNotEmpty() }
