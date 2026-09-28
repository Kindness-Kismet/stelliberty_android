package com.stelliberty.android.domain.model

import kotlin.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

// ISO 8601：写 UTC `Z`，读取接受 PC 写出的时区偏移与 7 位小数。
object IsoInstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.stelliberty.android.IsoInstant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}

// 按声明序号读写（PC System.Text.Json 的默认枚举格式）。
// 未知序号落到 fallback，PC 新增枚举值时 Android 不会因此整份文件读取失败。
internal abstract class OrdinalEnumSerializer<E : Enum<E>>(
    serialName: String,
    private val values: List<E>,
    private val fallback: E,
) : KSerializer<E> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.stelliberty.android.$serialName", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: E) = encoder.encodeInt(value.ordinal)

    override fun deserialize(decoder: Decoder): E = values.getOrNull(decoder.decodeInt()) ?: fallback
}
