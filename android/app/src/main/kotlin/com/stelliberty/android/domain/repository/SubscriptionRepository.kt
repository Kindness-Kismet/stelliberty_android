package com.stelliberty.android.domain.repository

import com.stelliberty.android.domain.model.Subscription
import com.stelliberty.android.domain.model.SubscriptionAutoUpdateMode
import com.stelliberty.android.domain.model.SubscriptionInfo
import com.stelliberty.android.domain.model.SubscriptionUpdateProxyMode
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.StateFlow

interface SubscriptionRepository {

    // 已导入的订阅：存在草稿时展示草稿里的可编辑字段，流量取运行中的实测值。
    val subscriptions: StateFlow<ImmutableList<Subscription>>

    val currentSubscriptionId: StateFlow<String?>

    val activeSubscription: StateFlow<Subscription?>

    fun setLiveProviderInfo(subscriptionId: String?, info: SubscriptionInfo?)

    fun setActive(id: String)

    fun getActive(): Subscription?

    suspend fun create(
        name: String,
        sourceLocation: String,
        isLocalFile: Boolean,
        autoUpdateMode: SubscriptionAutoUpdateMode = SubscriptionAutoUpdateMode.Disabled,
        autoUpdateIntervalMinutes: Int = 0,
        userAgent: String = "",
        ageSecretKey: String = "",
        updateProxyMode: SubscriptionUpdateProxyMode = SubscriptionUpdateProxyMode.Direct,
        autoTestDelayIntervalMinutes: Int = 0,
    ): Subscription

    suspend fun patch(
        uuid: String,
        name: String,
        sourceLocation: String,
        autoUpdateMode: SubscriptionAutoUpdateMode,
        autoUpdateIntervalMinutes: Int,
        userAgent: String,
        ageSecretKey: String,
        updateProxyMode: SubscriptionUpdateProxyMode,
        autoTestDelayIntervalMinutes: Int,
    )

    suspend fun release(uuid: String)

    suspend fun delete(uuid: String)

    suspend fun validatePendingForCommit(uuid: String): Boolean

    suspend fun commitPendingProfile(uuid: String)
}
