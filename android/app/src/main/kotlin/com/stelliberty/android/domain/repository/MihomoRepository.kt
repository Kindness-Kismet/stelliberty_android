package com.stelliberty.android.domain.repository

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface MihomoRepository {

    val connectionState: StateFlow<Boolean>

    fun trafficFlow(): Flow<TrafficData>
    fun logsFlow(): Flow<LogEvent>
    fun memoryFlow(): Flow<MemoryData>
    fun connectionsFlow(): Flow<ConnectionsResponse>

    suspend fun getVersion(): Result<MihomoVersion>
    suspend fun getConfig(): Result<MihomoConfig>
    suspend fun getProxies(): Result<ProxiesResponse>
    suspend fun getGroups(): Result<GroupsResponse>
    suspend fun selectProxy(group: String, name: String): Result<Unit>
    suspend fun unfixProxy(group: String): Result<Unit>
    suspend fun getProxyDelay(
        name: String,
        testUrl: String = "http://www.gstatic.com/generate_204",
        timeout: Int = 5000,
    ): Result<DelayResult>

    suspend fun getProviderProxyDelay(
        provider: String,
        name: String,
        testUrl: String = "http://www.gstatic.com/generate_204",
        timeout: Int = 5000,
    ): Result<DelayResult>

    // 逐个节点测，不用整组测速接口：后者会先把非手选类型的组解除固定。结果写进内核的延迟记录。
    // providerOf 给出 provider 节点所属的 provider，这些节点不在 /proxies 里，只能走 provider 的健康检查。
    suspend fun testDelays(nodes: Collection<String>, providerOf: Map<String, String>)

    suspend fun getRules(): Result<RulesResponse>
    suspend fun getConnections(): Result<ConnectionsResponse>
    suspend fun closeAllConnections(): Result<Unit>
    suspend fun closeConnection(id: String): Result<Unit>
    suspend fun getProviders(): Result<ProvidersResponse>
    suspend fun updateProvider(name: String): Result<Unit>
    suspend fun getRuleProviders(): Result<RuleProvidersResponse>
    suspend fun updateRuleProvider(name: String): Result<Unit>
    suspend fun queryDns(name: String, type: String = "A"): Result<DnsQueryResponse>
    suspend fun flushFakeIp(): Result<Unit>
    suspend fun flushDnsCache(): Result<Unit>

    fun close()
}
