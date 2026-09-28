package com.stelliberty.android.data.api

import com.stelliberty.android.domain.repository.MihomoRepository
import com.stelliberty.android.domain.repository.SubscriptionRepository
import com.stelliberty.android.util.AppLogger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

// 同 PC 的 SubscriptionAutoDelayPlanner：按当前订阅的 AutoTestDelayIntervalMinutes 测全部节点，
// 首轮在一个间隔之后；内核重启、切订阅或改间隔都重新计时。与 PC 不同，测完不解除固定选择。
class AutoDelayTester(
    private val scope: CoroutineScope,
    private val connectionManager: MihomoConnectionManager,
    private val subscriptions: SubscriptionRepository,
) {
    private val _completed = MutableSharedFlow<MihomoRepository>(extraBufferCapacity = 1)

    // 每轮测完发出所用的 client，消费方只在它仍是自己当前的 client 时刷新。
    val completed: SharedFlow<MihomoRepository> = _completed.asSharedFlow()

    fun start() {
        val schedule = combine(subscriptions.subscriptions, subscriptions.currentSubscriptionId) { items, id ->
            id to (items.firstOrNull { it.id == id }?.autoTestDelayIntervalMinutes ?: 0)
        }.distinctUntilChanged()
        scope.launch {
            combine(connectionManager.repository, schedule) { repo, (id, interval) -> Triple(repo, id, interval) }
                .collectLatest { (repo, id, interval) ->
                    if (repo == null || id == null || interval <= 0) return@collectLatest
                    while (true) {
                        delay(interval.minutes)
                        try {
                            testAll(repo)
                            AppLogger.info(TAG, "Auto delay test finished for $id")
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            AppLogger.warn(TAG, "Auto delay test failed for $id", e)
                        }
                        _completed.tryEmit(repo)
                    }
                }
        }
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
