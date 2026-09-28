package com.stelliberty.android.domain.repository

import com.stelliberty.android.domain.model.OverrideFormat
import com.stelliberty.android.domain.model.OverrideProfile
import com.stelliberty.android.domain.model.SubscriptionUpdateProxyMode
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.StateFlow

// 改动若落到当前订阅在用的覆写上，保存前先按运行时流程校验，失败抛 ConfigValidationException、不保存。
interface OverrideProfileRepository {

    val profiles: StateFlow<ImmutableList<OverrideProfile>>

    fun isUsedByCurrent(id: String): Boolean

    suspend fun readContent(id: String): String

    suspend fun addRemote(
        name: String,
        url: String,
        format: OverrideFormat,
        updateProxyMode: SubscriptionUpdateProxyMode,
    ): OverrideProfile

    suspend fun addLocal(name: String, fileName: String, format: OverrideFormat, content: String): OverrideProfile

    suspend fun addBlank(name: String, format: OverrideFormat): OverrideProfile

    suspend fun edit(
        id: String,
        name: String,
        url: String,
        format: OverrideFormat,
        updateProxyMode: SubscriptionUpdateProxyMode,
    )

    suspend fun saveContent(id: String, content: String)

    suspend fun update(id: String)

    // 同时清掉所有订阅里的引用。
    suspend fun delete(id: String)

    // overrideIds 按应用顺序排列；sortPreference 是选择页上全部覆写的顺序。
    suspend fun setSelection(subscriptionId: String, overrideIds: List<String>, sortPreference: List<String>)
}
