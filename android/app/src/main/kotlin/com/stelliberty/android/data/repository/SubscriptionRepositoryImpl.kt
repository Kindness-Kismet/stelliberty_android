package com.stelliberty.android.data.repository

import com.stelliberty.android.data.store.ProxySelectionStore
import com.stelliberty.android.data.store.RuleOverrideStore
import com.stelliberty.android.data.store.SubscriptionStore
import com.stelliberty.android.domain.model.Subscription
import com.stelliberty.android.domain.model.SubscriptionAutoUpdateMode
import com.stelliberty.android.domain.model.SubscriptionCustomChainProxy
import com.stelliberty.android.domain.model.SubscriptionInfo
import com.stelliberty.android.domain.model.SubscriptionTrafficInfo
import com.stelliberty.android.domain.model.SubscriptionUpdateProxyMode
import com.stelliberty.android.domain.repository.SubscriptionRepository
import com.stelliberty.android.platform.PlatformStorage
import com.stelliberty.android.platform.ProxyServiceBridge
import com.stelliberty.android.platform.StorageKeys
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SubscriptionRepositoryImpl(
    private val store: SubscriptionStore,
    private val proxySelections: ProxySelectionStore,
    private val ruleOverrides: RuleOverrideStore,
    private val storage: PlatformStorage,
    private val scope: CoroutineScope,
) : SubscriptionRepository {

    private val profileLock = Mutex()

    private val _liveProvider = MutableStateFlow<LiveProviderSnapshot?>(null)

    // 订阅页和首页流量栏的数字必须完全一致，所以在这里统一合并三层来源：
    // 编辑中的草稿 > 运行中实测到的用量 > 列表里存的。少任何一层都会有场景显示错。
    override val subscriptions: StateFlow<ImmutableList<Subscription>> =
        combine(store.importedFlow, store.pendingFlow, _liveProvider) { imported, pending, live ->
            resolveAll(imported, pending, live)
        }.stateIn(scope, SharingStarted.Eagerly, resolveAll(store.imported(), store.pending(), null))

    override val currentSubscriptionId: StateFlow<String?> = store.currentIdState

    override val activeSubscription: StateFlow<Subscription?> =
        combine(subscriptions, currentSubscriptionId) { list, id -> list.firstOrNull { it.id == id } }
            .stateIn(scope, SharingStarted.Eagerly, subscriptions.value.firstOrNull { it.id == store.currentId() })

    override fun setLiveProviderInfo(subscriptionId: String?, info: SubscriptionInfo?) {
        _liveProvider.value = if (subscriptionId != null && info != null) {
            LiveProviderSnapshot(subscriptionId, info)
        } else null
    }

    suspend fun <T> withProfileLock(block: suspend () -> T): T = profileLock.withLock { block() }

    fun queryPending(uuid: String): Subscription? = store.findPending(uuid)

    fun queryImported(uuid: String): Subscription? = store.findImported(uuid)

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun create(
        name: String,
        sourceLocation: String,
        isLocalFile: Boolean,
        autoUpdateMode: SubscriptionAutoUpdateMode,
        autoUpdateIntervalMinutes: Int,
        userAgent: String,
        ageSecretKey: String,
        updateProxyMode: SubscriptionUpdateProxyMode,
        autoTestDelayIntervalMinutes: Int,
    ): Subscription = profileLock.withLock {
        val pending = Subscription(
            id = Uuid.random().toHexString(),
            name = name,
            sourceLocation = sourceLocation,
            isLocalFile = isLocalFile,
            createdAt = Clock.System.now(),
            userAgent = userAgent.trim(),
            autoUpdateMode = autoUpdateMode,
            autoUpdateIntervalMinutes = autoUpdateIntervalMinutes.coerceAtLeast(0),
            updateProxyMode = updateProxyMode,
            ageSecretKey = ageSecretKey.trim(),
            autoTestDelayIntervalMinutes = autoTestDelayIntervalMinutes.coerceAtLeast(0),
        )
        store.putPending(pending)
        pending
    }

    // 草稿以当前草稿或已导入版本为底，只替换编辑页能改的字段，Android 暂未实现的 PC 字段原样保留。
    override suspend fun patch(
        uuid: String,
        name: String,
        sourceLocation: String,
        autoUpdateMode: SubscriptionAutoUpdateMode,
        autoUpdateIntervalMinutes: Int,
        userAgent: String,
        ageSecretKey: String,
        updateProxyMode: SubscriptionUpdateProxyMode,
        autoTestDelayIntervalMinutes: Int,
    ) = profileLock.withLock {
        val base = store.findPending(uuid) ?: store.findImported(uuid)
            ?: throw IllegalArgumentException("Profile $uuid not found")
        store.putPending(
            base.copy(
                name = name,
                sourceLocation = sourceLocation,
                autoUpdateMode = autoUpdateMode,
                // 非定时模式保留原间隔，切回定时时沿用；定时模式下的空值交给 enforceFieldValid 拒绝。
                autoUpdateIntervalMinutes = if (autoUpdateMode == SubscriptionAutoUpdateMode.Interval) {
                    autoUpdateIntervalMinutes.coerceAtLeast(0)
                } else base.autoUpdateIntervalMinutes,
                userAgent = userAgent.trim(),
                ageSecretKey = ageSecretKey.trim(),
                updateProxyMode = updateProxyMode,
                autoTestDelayIntervalMinutes = autoTestDelayIntervalMinutes.coerceAtLeast(0),
            )
        )
    }

    // 只改列表，文件那边由处理器在这之前换好。必须在持有订阅锁的情况下调用——
    // 协程用的这种锁不可重入，自己再加一次会和外层锁死。
    // fetched 为 true 表示刚完成一次获取与校验，流量、更新时间与内置链式代理取本次结果并清掉失败记录；否则沿用已导入版本的值。
    suspend fun commitPending(
        uuid: String,
        fetched: Boolean,
        trafficInfo: SubscriptionTrafficInfo? = null,
        builtinChainProxyNames: List<String> = emptyList(),
        fallbackName: String = "",
    ) {
        val pending = store.findPending(uuid)
            ?: throw IllegalArgumentException("No pending profile for $uuid")
        val existing = store.findImported(uuid)
        val imported = pending.copy(
            name = pending.name.ifBlank { fallbackName },
            createdAt = existing?.createdAt ?: pending.createdAt,
            lastUpdatedAt = if (fetched) Clock.System.now() else existing?.lastUpdatedAt,
            trafficInfo = if (fetched) trafficInfo else existing?.trafficInfo,
            builtinChainProxyNames = if (fetched) builtinChainProxyNames
            else existing?.builtinChainProxyNames ?: pending.builtinChainProxyNames,
            lastError = if (fetched) null else existing?.lastError,
            lastErrorAt = if (fetched) null else existing?.lastErrorAt,
        )
        store.putImported(imported)
        store.removePending(uuid)
        if (store.imported().size == 1) setActive(uuid)
        syncActiveNameIfActive(uuid, imported.name)
    }

    override suspend fun release(uuid: String) = profileLock.withLock {
        store.removePending(uuid)
    }

    override suspend fun validatePendingForCommit(uuid: String): Boolean {
        val pending = store.findPending(uuid) ?: return false
        pending.enforceFieldValid()
        return !pending.isLocalFile && pending.sourceLocation.isNotBlank()
    }

    override suspend fun commitPendingProfile(uuid: String) = profileLock.withLock {
        commitPending(uuid, fetched = false)
    }

    // 必须由外层持有订阅锁后调用，自己不能再加锁——协程用的这种锁不可重入。
    suspend fun markUpdated(uuid: String, trafficInfo: SubscriptionTrafficInfo?, builtinChainProxyNames: List<String>) {
        val existing = store.findImported(uuid) ?: return
        store.putImported(
            existing.copy(
                lastUpdatedAt = Clock.System.now(),
                trafficInfo = trafficInfo,
                builtinChainProxyNames = builtinChainProxyNames,
                lastError = null,
                lastErrorAt = null,
            )
        )
    }

    // 同 PC：记下失败原因与时间，自动更新以此为起点顺延一个间隔。调用方持有订阅锁。
    suspend fun markFailed(uuid: String, message: String) {
        val existing = store.findImported(uuid) ?: return
        store.putImported(existing.copy(lastError = message, lastErrorAt = Clock.System.now()))
    }

    // 覆写选择不动配置文件，不走三阶段管线；草稿要同步写入，否则提交时会被草稿里的旧值盖回。
    suspend fun setOverrides(uuid: String, overrideIds: List<String>, sortPreference: List<String>): Unit =
        profileLock.withLock {
            val imported = store.findImported(uuid) ?: throw IllegalArgumentException("Profile $uuid not found")
            store.putImported(imported.copy(overrideIds = overrideIds, overrideSortPreference = sortPreference))
            store.findPending(uuid)?.let {
                store.putPending(it.copy(overrideIds = overrideIds, overrideSortPreference = sortPreference))
            }
        }

    // 与覆写选择相同，草稿同步写入。
    suspend fun setChainProxies(
        uuid: String,
        disabledBuiltinNames: List<String>,
        customChainProxies: List<SubscriptionCustomChainProxy>,
    ): Unit = profileLock.withLock {
        fun Subscription.updated() = copy(
            disabledBuiltinChainProxyNames = disabledBuiltinNames,
            customChainProxies = customChainProxies,
        )
        val imported = store.findImported(uuid) ?: throw IllegalArgumentException("Profile $uuid not found")
        store.putImported(imported.updated())
        store.findPending(uuid)?.let { store.putPending(it.updated()) }
    }

    suspend fun removeOverrideReferences(overrideId: String): Unit = profileLock.withLock {
        fun Subscription.stripped() = copy(
            overrideIds = overrideIds - overrideId,
            overrideSortPreference = overrideSortPreference - overrideId,
        )
        store.imported().filter { it.references(overrideId) }.forEach { store.putImported(it.stripped()) }
        store.pending().filter { it.references(overrideId) }.forEach { store.putPending(it.stripped()) }
    }

    // 列表文件损坏时列表不可信，返回 null 让调用方跳过孤儿目录清理。
    suspend fun knownUuidsOrNull(): Set<String>? = profileLock.withLock {
        if (!store.isComplete) return@withLock null
        (store.imported().map { it.id } + store.pending().map { it.id }).toSet()
    }

    override suspend fun delete(uuid: String) = profileLock.withLock {
        val wasActive = store.currentId() == uuid
        store.removeImported(uuid)
        store.removePending(uuid)
        proxySelections.removeSubscription(uuid)
        ruleOverrides.delete(uuid)
        if (wasActive) setActive(store.imported().firstOrNull()?.id.orEmpty())
    }

    override fun setActive(id: String) {
        store.setCurrentId(id)
        scope.launch {
            val name = store.findImported(id)?.name.orEmpty()
            storage.putString(StorageKeys.ACTIVE_PROFILE_NAME, name)
            ProxyServiceBridge.requestNotificationRefresh()
        }
    }

    override fun getActive(): Subscription? {
        val activeId = store.currentId() ?: return null
        return subscriptions.value.find { it.id == activeId }
    }

    // 通知栏标题只在启动时读一次设置项，提交和更新的末尾要同步名字，否则改完后通知栏停在旧名。
    // 名字没变不发信号，免得周期性流量刷新打断通知动画。
    private fun syncActiveNameIfActive(uuid: String, name: String) {
        if (store.currentId() != uuid) return
        if (storage.getString(StorageKeys.ACTIVE_PROFILE_NAME, "") == name) return
        storage.putString(StorageKeys.ACTIVE_PROFILE_NAME, name)
        ProxyServiceBridge.requestNotificationRefresh()
    }

    private fun resolveAll(
        imported: List<Subscription>,
        pending: List<Subscription>,
        live: LiveProviderSnapshot?,
    ): ImmutableList<Subscription> {
        val pendingById = pending.associateBy { it.id }
        return imported.map { resolveProfile(it, pendingById[it.id], live) }.toPersistentList()
    }

    private fun resolveProfile(
        imported: Subscription,
        pending: Subscription?,
        live: LiveProviderSnapshot?,
    ): Subscription {
        val liveTraffic = live?.takeIf { it.subscriptionId == imported.id }?.info?.let {
            SubscriptionTrafficInfo(it.Upload, it.Download, it.Total, it.Expire)
        }
        // 获取结果以已导入版本为准，编辑草稿只覆盖用户设置。
        return (pending ?: imported).copy(
            createdAt = imported.createdAt,
            lastUpdatedAt = imported.lastUpdatedAt,
            trafficInfo = liveTraffic ?: imported.trafficInfo,
            builtinChainProxyNames = imported.builtinChainProxyNames,
            lastError = imported.lastError,
            lastErrorAt = imported.lastErrorAt,
        )
    }
}

data class LiveProviderSnapshot(
    val subscriptionId: String,
    val info: SubscriptionInfo,
)

sealed class ImportError(message: String) : Exception(message) {
    class HttpStatus(val code: Int) : ImportError("HTTP $code")
    class EmptyBody : ImportError("empty response body")
    class InvalidScheme(val source: String) : ImportError("unsupported scheme: $source")
    class InvalidName : ImportError("empty profile name")
    class IntervalTooSmall : ImportError("auto-update interval below minimum")
}

const val MIN_AUTO_UPDATE_INTERVAL_MINUTES = 15

private fun Subscription.references(overrideId: String) =
    overrideId in overrideIds || overrideId in overrideSortPreference

fun Subscription.enforceFieldValid() {
    if (name.isBlank() && isLocalFile) throw ImportError.InvalidName()
    if (!isLocalFile) {
        val lower = sourceLocation.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw ImportError.InvalidScheme(sourceLocation)
        }
    }
    if (autoUpdateMode == SubscriptionAutoUpdateMode.Interval &&
        autoUpdateIntervalMinutes < MIN_AUTO_UPDATE_INTERVAL_MINUTES
    ) {
        throw ImportError.IntervalTooSmall()
    }
}
