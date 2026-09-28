package com.stelliberty.android.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class LogLevel {
    @SerialName("debug")
    Debug,
    @SerialName("info")
    Info,
    @SerialName("warning")
    Warning,
    @SerialName("error")
    Error,
}
