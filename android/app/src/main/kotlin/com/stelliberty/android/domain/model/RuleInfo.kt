package com.stelliberty.android.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class RulesResponse(
    val rules: List<RuleInfo> = emptyList(),
)

@Serializable
data class RuleInfo(
    val type: String = "",
    val payload: String = "",
    val proxy: String = "",
    val size: Int = 0,
)
