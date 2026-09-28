package com.stelliberty.android.domain.model

import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// 与 PC 的 Stelliberty.Domain.Overrides.OverrideProfile 同一契约：键名、枚举编号、时间格式一致。
// 本地覆写的 SourceLocation 在 PC 上是文件路径，Android 记导入时的文件名，只作展示。
@Serializable
data class OverrideProfile(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String,
    @SerialName("SourceType") val sourceType: OverrideSourceType,
    @SerialName("Format") val format: OverrideFormat,
    @SerialName("SourceLocation") val sourceLocation: String,
    @SerialName("CreatedAt")
    @Serializable(with = IsoInstantSerializer::class)
    val createdAt: Instant,
    @SerialName("LastUpdatedAt")
    @Serializable(with = IsoInstantSerializer::class)
    val lastUpdatedAt: Instant? = null,
    // PC 的 OverrideUpdateProxyMode 与订阅的同名枚举编号相同，这里直接复用。
    @SerialName("UpdateProxyMode") val updateProxyMode: SubscriptionUpdateProxyMode = SubscriptionUpdateProxyMode.Direct,
) {
    val isRemote: Boolean get() = sourceType == OverrideSourceType.Remote

    val fileName: String get() = "$id.${format.extension}"
}

// 以下枚举按声明顺序编号写入，顺序与 PC 同名枚举一致，不能调整。
@Serializable(with = OverrideSourceTypeSerializer::class)
enum class OverrideSourceType { Local, Remote }

@Serializable(with = OverrideFormatSerializer::class)
enum class OverrideFormat(val extension: String) {
    Yaml("yaml"),
    JavaScript("js"),
}

internal object OverrideSourceTypeSerializer : OrdinalEnumSerializer<OverrideSourceType>(
    "OverrideSourceType", OverrideSourceType.entries, OverrideSourceType.Local,
)

internal object OverrideFormatSerializer : OrdinalEnumSerializer<OverrideFormat>(
    "OverrideFormat", OverrideFormat.entries, OverrideFormat.Yaml,
)
