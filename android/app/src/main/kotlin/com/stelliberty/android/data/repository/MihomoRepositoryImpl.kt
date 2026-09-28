package com.stelliberty.android.data.repository

import com.stelliberty.android.data.api.MihomoApiClient
import com.stelliberty.android.data.api.MihomoWebSocket
import com.stelliberty.android.domain.model.ConnectionsResponse
import com.stelliberty.android.domain.model.DelayResult
import com.stelliberty.android.domain.model.DnsQueryResponse
import com.stelliberty.android.domain.model.GroupsResponse
import com.stelliberty.android.domain.model.LogEvent
import com.stelliberty.android.domain.model.MemoryData
import com.stelliberty.android.domain.model.MihomoConfig
import com.stelliberty.android.domain.model.MihomoVersion
import com.stelliberty.android.domain.model.ProvidersResponse
import com.stelliberty.android.domain.model.ProxiesResponse
import com.stelliberty.android.domain.model.RuleProvidersResponse
import com.stelliberty.android.domain.model.RulesResponse
import com.stelliberty.android.domain.model.TrafficData
import com.stelliberty.android.domain.repository.MihomoRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class MihomoRepositoryImpl(
    private val apiClient: MihomoApiClient,
    private val webSocket: MihomoWebSocket,
) : MihomoRepository {

    override val connectionState: StateFlow<Boolean> get() = webSocket.connectionState

    override fun trafficFlow(): Flow<TrafficData> = webSocket.trafficFlow()
    override fun logsFlow(): Flow<LogEvent> = webSocket.logsFlow()
    override fun memoryFlow(): Flow<MemoryData> = webSocket.memoryFlow()

    override suspend fun getVersion(): Result<MihomoVersion> = io { apiClient.getVersion() }
    override suspend fun getConfig(): Result<MihomoConfig> = io { apiClient.getConfig() }
    override suspend fun getProxies(): Result<ProxiesResponse> = io { apiClient.getProxies() }
    override suspend fun getGroups(): Result<GroupsResponse> = io { apiClient.getGroups() }
    override suspend fun selectProxy(group: String, name: String): Result<Unit> = io { apiClient.selectProxy(group, name) }
    override suspend fun unfixProxy(group: String): Result<Unit> = io { apiClient.unfixProxy(group) }
    override suspend fun getProxyDelay(
        name: String,
        testUrl: String,
        timeout: Int,
    ): Result<DelayResult> =
        io { apiClient.getProxyDelay(name, testUrl, timeout) }

    override suspend fun getProviderProxyDelay(
        provider: String,
        name: String,
        testUrl: String,
        timeout: Int,
    ): Result<DelayResult> =
        io { apiClient.getProviderProxyDelay(provider, name, testUrl, timeout) }

    override suspend fun testDelays(nodes: Collection<String>, providerOf: Map<String, String>) {
        val semaphore = Semaphore(DELAY_CONCURRENCY)
        coroutineScope {
            nodes.map { name ->
                async {
                    semaphore.withPermit {
                        val provider = providerOf[name]
                        if (provider != null) getProviderProxyDelay(provider, name) else getProxyDelay(name)
                    }
                }
            }.awaitAll()
        }
    }

    override suspend fun getRules(): Result<RulesResponse> = io { apiClient.getRules() }
    override fun connectionsFlow(): Flow<ConnectionsResponse> = webSocket.connectionsFlow()
    override suspend fun getConnections(): Result<ConnectionsResponse> = io { apiClient.getConnections() }
    override suspend fun closeAllConnections(): Result<Unit> = io { apiClient.closeAllConnections() }
    override suspend fun closeConnection(id: String): Result<Unit> = io { apiClient.closeConnection(id) }
    override suspend fun getProviders(): Result<ProvidersResponse> = io { apiClient.getProviders() }
    override suspend fun updateProvider(name: String): Result<Unit> = io { apiClient.updateProvider(name) }
    override suspend fun getRuleProviders(): Result<RuleProvidersResponse> =
        io { apiClient.getRuleProviders() }

    override suspend fun updateRuleProvider(name: String): Result<Unit> = io { apiClient.updateRuleProvider(name) }
    override suspend fun queryDns(name: String, type: String): Result<DnsQueryResponse> = io { apiClient.queryDns(name, type) }
    override suspend fun flushFakeIp(): Result<Unit> = io { apiClient.flushFakeIp() }
    override suspend fun flushDnsCache(): Result<Unit> = io { apiClient.flushDnsCache() }

    override fun close() {
        apiClient.close()
        webSocket.close()
    }

    // Ktor 的 body() 反序列化在调用方上下文执行，主线程调用时几千节点的 JSON 会直接卡住动画帧。
    // runCatching 会吞掉 CancellationException，取消 loadJob 时必须再抛出去，否则旧请求会跑完把界面写成旧订阅。
    private suspend inline fun <T> io(crossinline block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private companion object {
        const val DELAY_CONCURRENCY = 5
    }
}
