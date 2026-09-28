package com.stelliberty.android.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class LogMessage(
    val type: LogLevel,
    val payload: String,
)
