package com.stelliberty.android.domain.model

import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// 与 PC 的 Stelliberty.Domain.Subscriptions.Subscription 同一契约：键名、枚举编号、时间格式一致。
// PC 读取时拒绝未知键，这里新增字段前先确认 PC 已有同名字段。
@Serializable
data class Subscription(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String,
    @SerialName("SourceLocation") val sourceLocation: String,
    @SerialName("IsLocalFile") val isLocalFile: Boolean,
    @SerialName("CreatedAt")
    @Serializable(with = IsoInstantSerializer::class)
    val createdAt: Instant,
    @SerialName("LastUpdatedAt")
    @Serializable(with = IsoInstantSerializer::class)
    val lastUpdatedAt: Instant? = null,
    @SerialName("UserAgent") val userAgent: String = "",
    @SerialName("AutoTestDelayIntervalMinutes") val autoTestDelayIntervalMinutes: Int = 0,
    @SerialName("AutoUpdateMode") val autoUpdateMode: SubscriptionAutoUpdateMode = SubscriptionAutoUpdateMode.Disabled,
    @SerialName("AutoUpdateIntervalMinutes") val autoUpdateIntervalMinutes: Int = 0,
    @SerialName("UpdateProxyMode") val updateProxyMode: SubscriptionUpdateProxyMode = SubscriptionUpdateProxyMode.Direct,
    @SerialName("AgeSecretKey") val ageSecretKey: String = "",
    @SerialName("OverrideIds") val overrideIds: List<String> = emptyList(),
    @SerialName("OverrideSortPreference") val overrideSortPreference: List<String> = emptyList(),
    @SerialName("LastError") val lastError: String? = null,
    @SerialName("LastErrorAt")
    @Serializable(with = IsoInstantSerializer::class)
    val lastErrorAt: Instant? = null,
    @SerialName("TrafficInfo") val trafficInfo: SubscriptionTrafficInfo? = null,
    @SerialName("BuiltinChainProxyNames") val builtinChainProxyNames: List<String> = emptyList(),
    @SerialName("DisabledBuiltinChainProxyNames") val disabledBuiltinChainProxyNames: List<String> = emptyList(),
    @SerialName("CustomChainProxies") val customChainProxies: List<SubscriptionCustomChainProxy> = emptyList(),
    @SerialName("SourceFormat") val sourceFormat: SubscriptionSourceFormat = SubscriptionSourceFormat.StandardClash,
) {
    // 只有核心代理经 mixed-port 下载；本地文件不下载。
    val updatesViaCore: Boolean
        get() = !isLocalFile && updateProxyMode == SubscriptionUpdateProxyMode.Core

    // 覆写应用顺序与 PC 相同：用户排序优先，其余选中项保持 OverrideIds 里的顺序。
    val orderedOverrideIds: List<String>
        get() {
            val selected = overrideIds.toSet()
            val ordered = overrideSortPreference.filter { it in selected }.distinct().toMutableList()
            overrideIds.forEach { if (it !in ordered) ordered += it }
            return ordered
        }
}

@Serializable
data class SubscriptionTrafficInfo(
    @SerialName("Upload") val upload: Long = 0,
    @SerialName("Download") val download: Long = 0,
    @SerialName("Total") val total: Long = 0,
    // Unix 秒，0 表示无到期时间。
    @SerialName("Expire") val expire: Long = 0,
) {
    val used: Long
        get() = if (upload > Long.MAX_VALUE - download) Long.MAX_VALUE else upload + download

    companion object {
        // 订阅响应没有流量头时各项都是 0，与 PC 一样记为 null。
        fun ofOrNull(upload: Long, download: Long, total: Long, expire: Long): SubscriptionTrafficInfo? =
            if (upload == 0L && download == 0L && total == 0L && expire == 0L) null
            else SubscriptionTrafficInfo(upload, download, total, expire)
    }
}

@Serializable
data class SubscriptionCustomChainProxy(
    @SerialName("Id") val id: String,
    @SerialName("DisplayName") val displayName: String,
    @SerialName("ProxyGroupName") val proxyGroupName: String,
    @SerialName("Hops") val hops: List<SubscriptionChainProxyHop> = emptyList(),
    @SerialName("IsEnabled") val isEnabled: Boolean = true,
)

@Serializable
data class SubscriptionChainProxyHop(
    @SerialName("Kind") val kind: SubscriptionChainProxyHopKind,
    @SerialName("Name") val name: String,
)

// 以下枚举按声明顺序编号写入，顺序与 PC 同名枚举一致，不能调整。
@Serializable(with = SubscriptionAutoUpdateModeSerializer::class)
enum class SubscriptionAutoUpdateMode { Disabled, Startup, Interval }

@Serializable(with = SubscriptionUpdateProxyModeSerializer::class)
enum class SubscriptionUpdateProxyMode { Direct, SystemProxy, Core }

@Serializable(with = SubscriptionSourceFormatSerializer::class)
enum class SubscriptionSourceFormat { StandardClash, NonStandard }

@Serializable(with = SubscriptionChainProxyHopKindSerializer::class)
enum class SubscriptionChainProxyHopKind { Proxy, ProxyGroup }

internal object SubscriptionAutoUpdateModeSerializer : OrdinalEnumSerializer<SubscriptionAutoUpdateMode>(
    "SubscriptionAutoUpdateMode", SubscriptionAutoUpdateMode.entries, SubscriptionAutoUpdateMode.Disabled,
)

internal object SubscriptionUpdateProxyModeSerializer : OrdinalEnumSerializer<SubscriptionUpdateProxyMode>(
    "SubscriptionUpdateProxyMode", SubscriptionUpdateProxyMode.entries, SubscriptionUpdateProxyMode.Direct,
)

internal object SubscriptionSourceFormatSerializer : OrdinalEnumSerializer<SubscriptionSourceFormat>(
    "SubscriptionSourceFormat", SubscriptionSourceFormat.entries, SubscriptionSourceFormat.StandardClash,
)

internal object SubscriptionChainProxyHopKindSerializer : OrdinalEnumSerializer<SubscriptionChainProxyHopKind>(
    "SubscriptionChainProxyHopKind", SubscriptionChainProxyHopKind.entries, SubscriptionChainProxyHopKind.Proxy,
)
