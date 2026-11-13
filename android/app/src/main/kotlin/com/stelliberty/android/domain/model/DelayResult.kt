package com.stelliberty.android.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class DelayResult(
    val delay: Int = 0,
)

@Serializable
data class TestedDelay(
    val delay: Int,
    val revision: Long,
)
