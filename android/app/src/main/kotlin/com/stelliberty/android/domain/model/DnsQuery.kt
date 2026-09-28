package com.stelliberty.android.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class DnsQueryResponse(
    val Status: Int = 0,
    val Answer: List<DnsAnswer> = emptyList(),
)

@Serializable
data class DnsAnswer(
    val name: String = "",
    val TTL: Int = 0,
    val data: String = "",
    val type: Int = 0,
)

val DnsQueryTypes = listOf("A", "AAAA", "CNAME", "MX", "TXT", "NS")

fun dnsTypeName(type: Int): String = when (type) {
    1 -> "A"
    2 -> "NS"
    5 -> "CNAME"
    6 -> "SOA"
    15 -> "MX"
    16 -> "TXT"
    28 -> "AAAA"
    33 -> "SRV"
    else -> "TYPE$type"
}
