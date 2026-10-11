package com.stelliberty.android.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// 内核未运行时代理页的数据来源：Go 侧从套完覆写与链式代理的订阅配置静态解析，延迟信息不存在。
@Serializable
data class ProxyPreview(
    @SerialName("nodes") val nodes: List<ProxyPreviewNode> = emptyList(),
    @SerialName("groups") val groups: List<ProxyPreviewGroup> = emptyList(),
)

@Serializable
data class ProxyPreviewNode(
    @SerialName("name") val name: String = "",
    @SerialName("type") val type: String = "",
)

@Serializable
data class ProxyPreviewGroup(
    @SerialName("name") val name: String = "",
    @SerialName("type") val type: String = "",
    @SerialName("icon") val icon: String = "",
    @SerialName("all") val all: List<String> = emptyList(),
)
