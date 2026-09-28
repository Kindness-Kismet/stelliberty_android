package com.stelliberty.android.domain.model

import kotlinx.serialization.Serializable

@Serializable
sealed interface LogEvent {
    @Serializable
    data object Connected : LogEvent

    @Serializable
    data object Disconnected : LogEvent

    @Serializable
    data class Message(val message: LogMessage) : LogEvent
}
