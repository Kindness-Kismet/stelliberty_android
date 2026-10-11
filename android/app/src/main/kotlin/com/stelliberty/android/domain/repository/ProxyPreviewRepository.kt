package com.stelliberty.android.domain.repository

import com.stelliberty.android.domain.model.ProxyPreview

interface ProxyPreviewRepository {
    // 订阅不存在、配置缺失或解析失败时返回 null，调用方按空列表渲染。
    suspend fun load(subscriptionId: String): ProxyPreview?
}
