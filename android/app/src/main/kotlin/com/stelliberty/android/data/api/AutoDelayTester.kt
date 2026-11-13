package com.stelliberty.android.data.api

import android.os.SystemClock
import com.stelliberty.android.domain.repository.MihomoRepository
import com.stelliberty.android.domain.repository.SubscriptionRepository
import com.stelliberty.android.platform.AutoDelayAlarm
import com.stelliberty.android.util.AppLogger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// 当前订阅按配置间隔测全部节点，首轮与每轮结束后都等待完整间隔。
// 切内核、切订阅或改间隔重新计时，测速保持固定选择。
class AutoDelayTester(
    private val scope: CoroutineScope,
    private val connectionManager: MihomoConnectionManager,
    private val subscriptions: SubscriptionRepository,
    private val alarm: AutoDelayAlarm,
) {
    data class Snapshot(
        val subscriptionId: String?,
        val intervalMinutes: Int,
        val nextElapsedRealtime: Long?,
        val isTesting: Boolean,
    )

    private data class Schedule(val repo: MihomoRepository, val subscriptionId: String, val intervalMinutes: Int)

    private val _completed = MutableSharedFlow<MihomoRepository>(extraBufferCapacity = 1)
    private var observerJob: Job? = null
    private var testJob: Job? = null
    private var scheduled: Schedule? = null
    private var nextElapsedRealtime: Long? = null
    private var isTesting = false

    // 每轮测完发出所用的 client，消费方只在它仍是自己当前的 client 时刷新。
    val completed: SharedFlow<MihomoRepository> = _completed.asSharedFlow()

    @Synchronized
    fun start() {
        if (observerJob != null) return
        val schedule = combine(subscriptions.subscriptions, subscriptions.currentSubscriptionId) { items, id ->
            id to (items.firstOrNull { it.id == id }?.autoTestDelayIntervalMinutes ?: 0)
        }.distinctUntilChanged()
        observerJob = scope.launch {
            combine(connectionManager.repository, schedule) { repo, (id, interval) -> Triple(repo, id, interval) }
                .collect { (repo, id, interval) -> configure(repo, id, interval) }
        }
    }

    @Synchronized
    fun snapshot(): Snapshot = Snapshot(
        subscriptionId = scheduled?.subscriptionId,
        intervalMinutes = scheduled?.intervalMinutes ?: 0,
        nextElapsedRealtime = nextElapsedRealtime,
        isTesting = isTesting,
    )

    @Synchronized
    private fun configure(repo: MihomoRepository?, id: String?, interval: Int) {
        val previous = scheduled
        if (previous?.repo === repo && previous?.subscriptionId == id && previous?.intervalMinutes == interval) return
        alarm.cancel()
        testJob?.cancel()
        testJob = null
        scheduled = null
        nextElapsedRealtime = null
        isTesting = false
        if (repo == null || id == null || interval <= 0) return
        val next = Schedule(repo, id, interval)
        scheduled = next
        scheduleNext(next)
    }

    @Synchronized
    fun onAlarm() {
        val current = scheduled ?: return
        val due = nextElapsedRealtime ?: return
        if (isTesting || SystemClock.elapsedRealtime() < due || !isCurrent(current)) return
        nextElapsedRealtime = null
        isTesting = true
        // 广播返回前取得锁，后续请求由正在运行的代理前台服务承载。
        testJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var wakeLock: AutoCloseable? = null
            try {
                wakeLock = alarm.acquireWakeLock()
                AppLogger.info(TAG, "Auto delay test started for ${current.subscriptionId} (interval=${current.intervalMinutes}m)")
                testAll(current.repo)
                AppLogger.info(TAG, "Auto delay test finished for ${current.subscriptionId}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.warn(TAG, "Auto delay test failed for ${current.subscriptionId}", e)
            } finally {
                wakeLock?.close()
                finish(current, currentCoroutineContext().isActive)
            }
        }.takeIf { it.isActive }
    }

    @Synchronized
    private fun finish(current: Schedule, reschedule: Boolean) {
        if (scheduled !== current) return
        isTesting = false
        testJob = null
        if (!reschedule || !isCurrent(current)) return
        _completed.tryEmit(current.repo)
        scheduleNext(current)
    }

    private fun isCurrent(current: Schedule): Boolean =
        connectionManager.repository.value === current.repo &&
            subscriptions.currentSubscriptionId.value == current.subscriptionId &&
            subscriptions.subscriptions.value.firstOrNull { it.id == current.subscriptionId }
                ?.autoTestDelayIntervalMinutes == current.intervalMinutes

    private fun scheduleNext(current: Schedule) {
        val triggerAt = SystemClock.elapsedRealtime() + current.intervalMinutes * 60_000L
        nextElapsedRealtime = triggerAt
        alarm.schedule(triggerAt)
        AppLogger.info(TAG, "Schedule auto delay test for ${current.subscriptionId} in ${current.intervalMinutes}m (elapsed=$triggerAt)")
    }

    private suspend fun testAll(repo: MihomoRepository) {
        val groups = repo.getGroups().getOrThrow().proxies
        val runtime = repo.getProxies().getOrThrow().proxies
        val providerOf = HashMap<String, String>()
        repo.getProviders().getOrNull()?.providers?.forEach { (provider, detail) ->
            detail.proxies.forEach { node -> if (node.name !in runtime) providerOf.putIfAbsent(node.name, provider) }
        }
        repo.testDelays(groups.flatMapTo(LinkedHashSet()) { it.all }, providerOf)
    }

    private companion object {
        const val TAG = "AutoDelayTester"
    }
}
