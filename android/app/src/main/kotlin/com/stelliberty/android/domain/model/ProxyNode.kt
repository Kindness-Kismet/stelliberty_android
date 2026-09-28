package com.stelliberty.android.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ProxyNode(
    val name: String = "",
    val type: String = "",
    val alive: Boolean = true,
    val now: String = "",
    val all: List<String> = emptyList(),
    val history: List<ProxyHistory> = emptyList(),
    val udp: Boolean = false,
    val xudp: Boolean = false,
    val tfo: Boolean = false,
    val icon: String = "",
    val fixed: String = "",
)

@Serializable
data class ProxyHistory(
    val time: String = "",
    val delay: Int = 0,
)
