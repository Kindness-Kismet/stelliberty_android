package com.stelliberty.android.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// 链式代理页面的候选项，由 Go 侧从套完覆写的配置中解析；带 dialer-proxy 的节点只作为内置链列出。
@Serializable
data class ChainProxyContext(
    @SerialName("builtinNames") val builtinNames: List<String> = emptyList(),
    @SerialName("proxyGroups") val proxyGroups: List<ChainProxyOption> = emptyList(),
    @SerialName("candidates") val candidates: List<ChainProxyOption> = emptyList(),
)

@Serializable
data class ChainProxyOption(
    @SerialName("kind") val kind: SubscriptionChainProxyHopKind,
    @SerialName("name") val name: String,
    @SerialName("type") val type: String = "",
) {
    val hop: SubscriptionChainProxyHop get() = SubscriptionChainProxyHop(kind, name)
}
