package com.stelliberty.android.domain.repository

import com.stelliberty.android.domain.model.ChainProxyContext
import com.stelliberty.android.domain.model.SubscriptionCustomChainProxy

// 链式代理设置随订阅保存；页面候选项取自套完覆写的配置，与运行时看到的一致。
interface ChainProxyRepository {

    suspend fun loadContext(subscriptionId: String): ChainProxyContext

    // 保存前按运行时流程校验，失败抛 ConfigValidationException、不保存；返回变换后的配置是否含循环引用。
    suspend fun save(
        subscriptionId: String,
        disabledBuiltinNames: List<String>,
        customChainProxies: List<SubscriptionCustomChainProxy>,
    ): Boolean
}
