package com.stelliberty.android.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class MemoryData(
    val inuse: Long = 0,
    val oslimit: Long = 0,
)
