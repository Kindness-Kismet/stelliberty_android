package com.stelliberty.android.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stelliberty.android.data.api.MihomoConnectionManager
import com.stelliberty.android.data.api.RuleLatencyTester
import com.stelliberty.android.data.repository.OverrideJsonStore
import com.stelliberty.android.domain.model.ConnectionInfo
import com.stelliberty.android.domain.model.MihomoConfig
import com.stelliberty.android.domain.model.ProvidersResponse
import com.stelliberty.android.domain.model.Subscription
import com.stelliberty.android.domain.model.SubscriptionInfo
import com.stelliberty.android.domain.model.TunOverride
import com.stelliberty.android.domain.repository.MihomoRepository
import com.stelliberty.android.platform.PlatformSystemInfo
import com.stelliberty.android.platform.ProxyServiceController
import com.stelliberty.android.platform.ProxyState
import com.stelliberty.android.platform.TunMode
import com.stelliberty.android.platform.showToast
import com.stelliberty.android.util.FormatUtils
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class HomeUiState(
    val isRunning: Boolean = false,
    val isStarting: Boolean = false,
    val isStopping: Boolean = false,
    val mode: String = "--",
    val tunStack: String = "",
    val tunMode: TunMode = TunMode.Vpn,
    val ipv6: Boolean = false,
    val config: MihomoConfig? = null,
    val subscription: SubscriptionInfo? = null,
    val providerTraffic: ImmutableList<ProviderTrafficInfo> = persistentListOf(),
    val isProviderTrafficLoading: Boolean = false,
    val providerTrafficLoadFailed: Boolean = false,
    val latencyBaidu: Int = -1,
    val latencyCloudflare: Int = -1,
    val latencyGoogle: Int = -1,
    val isTestingLatency: Boolean = false,
    val latencyViaRules: Boolean = true,
    val version: String = "",
    val profileName: String = "",
    val errorMessage: String = "",
    val needsVpnPermission: Boolean = false,
)

@Immutable
data class ProviderTrafficInfo(
    val id: String,
    val name: String,
    val nodeCount: Int,
    val updatedAt: String,
    val hasTraffic: Boolean,
    val upload: Long,
    val download: Long,
    val total: Long,
    val expire: Long,
)

private const val VEHICLE_TYPE_COMPATIBLE = "Compatible"

@Immutable
data class TrafficHistory(
    val up: ImmutableList<Long> = persistentListOf(),
    val down: ImmutableList<Long> = persistentListOf(),
    val seq: Int = 0,
)

@Immutable
data class SpeedSnapshot(
    val uploadSpeed: String = "-- B/s",
    val downloadSpeed: String = "-- B/s",
    val history: TrafficHistory = TrafficHistory(),
)

enum class LatencyProbe(val url: String) {
    Baidu("http://www.baidu.com"),
    Cloudflare("http://www.cloudflare.com/cdn-cgi/trace"),
    Google("http://www.google.com/generate_204"),
}

@Immutable
data class MemorySnapshot(
    val ramUsage: String = "-- MB",
    val ramTotal: String = "-- MB",
)

@Immutable
data class ConnectionRate(
    val id: String,
    val host: String,
    val node: String,
    val uploadRate: Long,
    val downloadRate: Long,
)

@Immutable
data class SystemInfoSnapshot(
    val localIp: String = "0.0.0.0",
    val interfaceName: String = "--",
    val cpuUsage: String = "--%",
)

class HomeViewModel(
    private val serviceController: ProxyServiceController,
    private val overrideStore: OverrideJsonStore,
    private val connectionManager: MihomoConnectionManager,
    private val latencyTester: RuleLatencyTester,
    private val getActiveSubscriptionId: () -> String? = { null },
    private val activeSubscription: StateFlow<Subscription?> = MutableStateFlow(null).asStateFlow(),
    private val onLiveProviderInfo: (subscriptionId: String?, info: SubscriptionInfo?) -> Unit = { _, _ -> },
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(mode = persistedMode()))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _speedState = MutableStateFlow(SpeedSnapshot())
    val speedState: StateFlow<SpeedSnapshot> = _speedState.asStateFlow()

    private val _memoryState = MutableStateFlow(MemorySnapshot())
    val memoryState: StateFlow<MemorySnapshot> = _memoryState.asStateFlow()

    private val _systemInfoState = MutableStateFlow(SystemInfoSnapshot())
    val systemInfoState: StateFlow<SystemInfoSnapshot> = _systemInfoState.asStateFlow()

    private val _uptimeState = MutableStateFlow(-1L)
    val uptimeState: StateFlow<Long> = _uptimeState.asStateFlow()

    private val upHistory = ArrayDeque<Long>(TRAFFIC_HISTORY_CAPACITY)
    private val downHistory = ArrayDeque<Long>(TRAFFIC_HISTORY_CAPACITY)
    private var trafficSeq = 0

    private val _topConnectionRates = MutableStateFlow<ImmutableList<ConnectionRate>?>(null)
    val topConnectionRates: StateFlow<ImmutableList<ConnectionRate>?> = _topConnectionRates.asStateFlow()

    private var prevConnectionBytes = emptyMap<String, LongArray>()
    private var prevConnectionAtMillis = 0L

    private var repository: MihomoRepository? = null
    private var latencyJob: Job? = null
    private var connectionRateJob: Job? = null
    private var trafficJob: Job? = null
    private var memoryJob: Job? = null
    private var systemInfoJob: Job? = null
    private var runtimeConfigJob: Job? = null
    private var initialLoadJob: Job? = null
    private var providerTrafficJob: Job? = null
    private var providerTrafficRequestId = 0L
    private var repositorySubscriptionId: String? = null
    private var startTime: Long = 0
    private var uptimeJob: Job? = null
    private var mihomoPid: Int = -1
    private val systemInfo = PlatformSystemInfo()

    private val uiVisible = MutableStateFlow(true)

    private var lastErrorToast: String? = null

    init {
        viewModelScope.launch {
            serviceController.status.collect { status ->
                when (status.state) {
                    ProxyState.Starting -> {
                        _uiState.value = _uiState.value.copy(
                            isStarting = true,
                            isStopping = false,
                            tunMode = status.tunMode,
                            subscription = activeSubscriptionInfo(),
                        )
                    }

                    ProxyState.Running -> {
                        _uiState.value = _uiState.value.copy(
                            isStarting = false,
                            isRunning = true,
                            tunMode = status.tunMode,
                            subscription = activeSubscriptionInfo(),
                        )
                        startTime = if (status.startTime > 0) status.startTime else Clock.System.now().toEpochMilliseconds()
                        mihomoPid = status.mihomoPid
                    }

                    ProxyState.Stopping -> {
                        _uiState.value = _uiState.value.copy(isRunning = false, isStopping = true)
                    }

                    // 这两个分支整体重建状态来清掉运行期的热数据，因此每个要留下的字段都得显式带上。
                    // tunMode 漏了就退回构造默认的 VPN，用户选着 ROOT 却在首页看到 VPN。
                    ProxyState.Stopped -> {
                        _uiState.value = HomeUiState(
                            mode = persistedMode(),
                            profileName = _uiState.value.profileName,
                            tunMode = status.tunMode,
                        )
                        lastErrorToast = null
                        resetHotStates()
                    }

                    ProxyState.Error -> {
                        val message = status.errorMessage
                        if (message.isNotBlank() && !status.errorNotified && message != lastErrorToast) {
                            lastErrorToast = message
                            showToast(message, long = true)
                        }
                        _uiState.value = HomeUiState(
                            errorMessage = message,
                            mode = persistedMode(),
                            profileName = _uiState.value.profileName,
                            tunMode = status.tunMode,
                        )
                        resetHotStates()
                    }
                }
            }
        }
        viewModelScope.launch {
            connectionManager.repository.collect { repo ->
                repository = repo
                if (repo != null) {
                    connectToMihomo()
                } else {
                    disconnectStreams()
                }
            }
        }
        viewModelScope.launch {
            activeSubscription.collect { sub ->
                _uiState.value = _uiState.value.copy(profileName = sub?.name.orEmpty())
                if (_uiState.value.isRunning || _uiState.value.isStarting) {
                    _uiState.value = _uiState.value.copy(subscription = activeSubscriptionInfo())
                }
                if (repository != null && getActiveSubscriptionId() != repositorySubscriptionId) {
                    clearProviderTraffic()
                }
            }
        }
    }

    private fun connectToMihomo() {
        val repo = repository ?: return
        val subscriptionId = getActiveSubscriptionId()
        repositorySubscriptionId = subscriptionId
        _uiState.value = _uiState.value.copy(subscription = activeSubscriptionInfo())
        startTrafficCollection()
        startMemoryCollection()
        startSystemInfoCollection()
        startRuntimeConfigRefresh()
        startUptimeCounter()
        initialLoadJob?.cancel()
        initialLoadJob = viewModelScope.launch {
            loadConfig(repo)
            if (repository === repo) testLatency()
        }
        refreshProviderTraffic(repo, subscriptionId)
    }

    private suspend fun loadConfig(repo: MihomoRepository) {
        repo.getConfig().onSuccess { config ->
            if (repository !== repo) return@onSuccess
            _uiState.update {
                it.copy(
                    isRunning = true,
                    mode = config.mode,
                    tunStack = config.tun?.stack ?: "",
                    ipv6 = config.ipv6,
                    config = config,
                )
            }
        }
        repo.getVersion().onSuccess { version ->
            if (repository !== repo) return@onSuccess
            _uiState.update { it.copy(version = version.version) }
        }
    }

    fun refreshProviderTraffic() {
        val repo = repository ?: return
        val subscriptionId = repositorySubscriptionId
        if (getActiveSubscriptionId() != subscriptionId) return
        refreshProviderTraffic(repo, subscriptionId)
    }

    private fun refreshProviderTraffic(repo: MihomoRepository, subscriptionId: String?) {
        providerTrafficJob?.cancel()
        val requestId = ++providerTrafficRequestId
        setProviderTrafficLoading(repo, subscriptionId, requestId)
        providerTrafficJob = viewModelScope.launch {
            loadProviderTrafficSnapshot(repo, subscriptionId, requestId)
        }
    }

    // mihomo 只在重新拉取订阅源时才刷新用量信息，光查询读到的永远是上次的旧数据。
    // 所以逐个触发更新（单个失败不影响其他），全部完成后再统一读一遍。
    fun updateAllProviders() {
        val repo = repository ?: return
        val subscriptionId = repositorySubscriptionId
        if (getActiveSubscriptionId() != subscriptionId) return
        providerTrafficJob?.cancel()
        val requestId = ++providerTrafficRequestId
        setProviderTrafficLoading(repo, subscriptionId, requestId)
        providerTrafficJob = viewModelScope.launch {
            val names = repo.getProviders().getOrNull()?.providers.orEmpty()
                .filterValues { !it.vehicleType.equals(VEHICLE_TYPE_COMPATIBLE, ignoreCase = true) }
                .map { (fallbackName, provider) -> provider.name.ifBlank { fallbackName } }
            if (!isCurrentProviderTrafficRequest(repo, subscriptionId, requestId)) return@launch
            names.map { name -> async { repo.updateProvider(name) } }.awaitAll()
            if (!isCurrentProviderTrafficRequest(repo, subscriptionId, requestId)) return@launch
            loadProviderTrafficSnapshot(repo, subscriptionId, requestId)
        }
    }

    private fun setProviderTrafficLoading(repo: MihomoRepository, subscriptionId: String?, requestId: Long) {
        _uiState.update { state ->
            if (!isCurrentProviderTrafficRequest(repo, subscriptionId, requestId)) state else state.copy(
                isProviderTrafficLoading = true,
                providerTrafficLoadFailed = false,
            )
        }
    }

    private suspend fun loadProviderTrafficSnapshot(
        repo: MihomoRepository,
        subscriptionId: String?,
        requestId: Long,
    ) {
        val result = repo.getProviders()
        if (!isCurrentProviderTrafficRequest(repo, subscriptionId, requestId)) return
        result.onSuccess { providers ->
            _uiState.update { state ->
                if (!isCurrentProviderTrafficRequest(repo, subscriptionId, requestId)) state else state.copy(
                    providerTraffic = providerTrafficInfo(providers),
                    isProviderTrafficLoading = false,
                    providerTrafficLoadFailed = false,
                )
            }
            if (isCurrentProviderTrafficRequest(repo, subscriptionId, requestId)) {
                onLiveProviderInfo(subscriptionId, aggregateProviderInfo(providers))
            }
        }.onFailure {
            _uiState.update { state ->
                if (!isCurrentProviderTrafficRequest(repo, subscriptionId, requestId)) state else state.copy(
                    isProviderTrafficLoading = false,
                    providerTrafficLoadFailed = true,
                )
            }
        }
    }

    private fun isCurrentProviderTrafficRequest(
        repo: MihomoRepository,
        subscriptionId: String?,
        requestId: Long,
    ): Boolean = repository === repo &&
            repositorySubscriptionId == subscriptionId &&
            getActiveSubscriptionId() == subscriptionId &&
            providerTrafficRequestId == requestId

    private fun clearProviderTraffic() {
        providerTrafficJob?.cancel()
        providerTrafficRequestId++
        _uiState.update {
            it.copy(
                providerTraffic = persistentListOf(),
                isProviderTrafficLoading = false,
                providerTrafficLoadFailed = false,
            )
        }
    }

    // 必须把所有订阅源的用量加起来。用量信息是每个源各自解析响应头得来的，
    // 一份配置里有多个源时随便取第一个，拿到的是哪个全看遍历顺序。
    private fun aggregateProviderInfo(providers: ProvidersResponse): SubscriptionInfo? {
        val valid = providers.providers.values.mapNotNull { info ->
            info.subscriptionInfo?.takeIf { it.Total > 0 }
        }
        if (valid.isEmpty()) return null
        if (valid.size == 1) return valid.first()
        return SubscriptionInfo(
            Upload = valid.sumOf { it.Upload },
            Download = valid.sumOf { it.Download },
            Total = valid.sumOf { it.Total },
            Expire = valid.filter { it.Expire > 0 }.minOfOrNull { it.Expire } ?: 0,
        )
    }

    private fun providerTrafficInfo(providers: ProvidersResponse): ImmutableList<ProviderTrafficInfo> {
        return providers.providers
            .mapNotNull { (fallbackName, provider) ->
                if (provider.vehicleType.equals(VEHICLE_TYPE_COMPATIBLE, ignoreCase = true)) return@mapNotNull null
                val info = provider.subscriptionInfo
                ProviderTrafficInfo(
                    id = fallbackName,
                    name = provider.name.ifBlank { fallbackName },
                    nodeCount = provider.proxies.size,
                    updatedAt = provider.updatedAt,
                    hasTraffic = info != null,
                    upload = info?.Upload ?: 0,
                    download = info?.Download ?: 0,
                    total = info?.Total ?: 0,
                    expire = info?.Expire ?: 0,
                )
            }
            .sortedBy { it.name.lowercase() }
            .toPersistentList()
    }

    // 总量小于等于 0 时（不限量套餐，或者服务端根本没给总量）配额的概念不成立，
    // 返回空让界面显示两个横线、不画用量条。各处卡片一律以此为准，别再各写一份判断。
    private fun activeSubscriptionInfo(): SubscriptionInfo? {
        val traffic = activeSubscription.value?.trafficInfo ?: return null
        if (traffic.total <= 0) return null
        return SubscriptionInfo(
            Upload = traffic.upload,
            Download = traffic.download,
            Total = traffic.total,
            Expire = traffic.expire,
        )
    }

    private fun startTrafficCollection() {
        trafficJob?.cancel()
        primeTrafficHistory()
        trafficJob = viewModelScope.launch {
            repository?.trafficFlow()
                ?.collect { traffic ->
                    upHistory.addLast(traffic.up)
                    downHistory.addLast(traffic.down)
                    while (upHistory.size > TRAFFIC_HISTORY_CAPACITY) upHistory.removeFirst()
                    while (downHistory.size > TRAFFIC_HISTORY_CAPACITY) downHistory.removeFirst()
                    _speedState.value = SpeedSnapshot(
                        uploadSpeed = FormatUtils.formatSpeed(traffic.up),
                        downloadSpeed = FormatUtils.formatSpeed(traffic.down),
                        history = TrafficHistory(
                            up = upHistory.toPersistentList(),
                            down = downHistory.toPersistentList(),
                            seq = ++trafficSeq,
                        ),
                    )
                    if (!_uiState.value.isRunning) {
                        _uiState.value = _uiState.value.copy(isRunning = true)
                    }
                }
        }
    }

    // 只在速度详情打开时才订阅：这条流每秒推送全部连接的完整列表，几百条常驻解析代价不小。
    fun startConnectionRateTracking() {
        val repo = repository ?: return
        if (connectionRateJob?.isActive == true) return
        prevConnectionBytes = emptyMap()
        prevConnectionAtMillis = 0L
        _topConnectionRates.value = null
        connectionRateJob = viewModelScope.launch {
            repo.connectionsFlow()
                .collect { response ->
                    if (repository !== repo) return@collect
                    updateConnectionRates(response.connections)
                }
        }
    }

    fun stopConnectionRateTracking() {
        connectionRateJob?.cancel()
        connectionRateJob = null
        prevConnectionBytes = emptyMap()
        prevConnectionAtMillis = 0L
        _topConnectionRates.value = null
    }

    // mihomo 每条连接只给累计字节数，不给速度，照累计量排序会让长连接永远压在最前面。
    // 所以拿前后两次快照相减再除以实际间隔。第一轮只留基准不出结果，否则会把历史总量当成瞬时速度。
    private fun updateConnectionRates(connections: List<ConnectionInfo>) {
        val now = Clock.System.now().toEpochMilliseconds()
        val snapshot = connections.associate { it.id to longArrayOf(it.upload, it.download) }
        val elapsedMillis = now - prevConnectionAtMillis
        if (prevConnectionAtMillis == 0L || elapsedMillis <= 0) {
            prevConnectionBytes = snapshot
            prevConnectionAtMillis = now
            return
        }

        val perSecond = elapsedMillis / 1000.0
        val rates = connections.mapNotNull { conn ->
            val prev = prevConnectionBytes[conn.id]
            val upDelta = (conn.upload - (prev?.get(0) ?: 0L)).coerceAtLeast(0L)
            val downDelta = (conn.download - (prev?.get(1) ?: 0L)).coerceAtLeast(0L)
            if (upDelta == 0L && downDelta == 0L) return@mapNotNull null
            ConnectionRate(
                id = conn.id,
                host = conn.metadata.host.ifEmpty {
                    "${conn.metadata.destinationIP}:${conn.metadata.destinationPort}"
                },
                node = conn.chains.firstOrNull().orEmpty(),
                uploadRate = (upDelta / perSecond).toLong(),
                downloadRate = (downDelta / perSecond).toLong(),
            )
        }

        prevConnectionBytes = snapshot
        prevConnectionAtMillis = now
        _topConnectionRates.value = rates
            .sortedByDescending { it.uploadRate + it.downloadRate }
            .take(TOP_CONNECTION_COUNT)
            .toPersistentList()
    }

    private fun startMemoryCollection() {
        memoryJob?.cancel()
        memoryJob = viewModelScope.launch {
            repository?.memoryFlow()
                ?.collect { memory ->
                    _memoryState.value = MemorySnapshot(
                        ramUsage = FormatUtils.formatBytes(memory.inuse),
                        ramTotal = if (memory.oslimit > 0) FormatUtils.formatBytes(memory.oslimit) else "-- MB",
                    )
                }
        }
    }

    // 这些轮询挂在 ViewModel 的作用域上，不感知界面前后台。不按可见性掐掉，
    // 就是在后台每天几万次读系统文件和打本地请求。
    private fun pollWhileVisible(interval: Duration, block: suspend () -> Unit): Job =
        viewModelScope.launch {
            while (true) {
                uiVisible.first { it }
                block()
                delay(interval)
            }
        }

    fun setUiVisible(visible: Boolean) {
        uiVisible.value = visible
    }

    private fun startUptimeCounter() {
        uptimeJob?.cancel()
        uptimeJob = pollWhileVisible(1000.milliseconds) {
            _uptimeState.value = (Clock.System.now().toEpochMilliseconds() - startTime) / 1000
        }
    }

    private fun startSystemInfoCollection() {
        systemInfoJob?.cancel()
        systemInfoJob = pollWhileVisible(2000.milliseconds) {
            val snapshot = withContext(Dispatchers.IO) {
                val networkInfo = systemInfo.getNetworkInfo()
                val cpu = systemInfo.getCpuUsage(mihomoPid)
                SystemInfoSnapshot(
                    localIp = networkInfo.localIp,
                    interfaceName = networkInfo.interfaceName,
                    cpuUsage = if (cpu >= 0) "${cpu.toInt()}%" else "--%",
                )
            }
            _systemInfoState.value = snapshot
        }
    }

    private fun startRuntimeConfigRefresh() {
        runtimeConfigJob?.cancel()
        runtimeConfigJob = pollWhileVisible(2000.milliseconds) { refreshRuntimeConfig() }
    }

    private suspend fun refreshRuntimeConfig() {
        repository?.getConfig()?.onSuccess { config ->
            val current = _uiState.value
            if (!current.isRunning || current.config == config) return@onSuccess
            _uiState.value = current.copy(
                mode = config.mode,
                tunStack = config.tun?.stack ?: "",
                ipv6 = config.ipv6,
                config = config,
            )
        }
    }

    // 先用 0 填满窗口：横向间距按窗口容量算，点数不够时曲线只占右边一小条（每秒一点即头
    // 一分钟都这样）。没流量时填 0 也是如实反映。
    private fun primeTrafficHistory() {
        upHistory.clear()
        downHistory.clear()
        repeat(TRAFFIC_HISTORY_CAPACITY) {
            upHistory.addLast(0L)
            downHistory.addLast(0L)
        }
    }

    private fun resetHotStates() {
        upHistory.clear()
        downHistory.clear()
        trafficSeq = 0
        _speedState.value = SpeedSnapshot()
        _memoryState.value = MemorySnapshot()
        _systemInfoState.value = SystemInfoSnapshot()
        _uptimeState.value = -1L
    }

    fun startProxy() {
        if (!serviceController.hasVpnPermission()) {
            _uiState.value = _uiState.value.copy(needsVpnPermission = true)
            serviceController.requestVpnPermission()
            return
        }
        serviceController.start(getActiveSubscriptionId())
    }

    fun stopProxy() {
        serviceController.stop()
    }

    fun restartProxy() {
        serviceController.restart(getActiveSubscriptionId())
    }

    fun onActiveSubscriptionChanged() {
        serviceController.onActiveSubscriptionChanged()
    }

    private fun persistedMode(): String = overrideStore.load().mode?.takeIf { it.isNotBlank() } ?: "--"

    // 停止状态下也能改：写设置和代理是否在跑没关系，重启的事交给控制器判断。
    // 别改成直接重启，那会把用户没打算启动的代理拉起来。
    fun switchMode(mode: String) {
        val current = overrideStore.load()
        overrideStore.save(current.copy(mode = mode))
        _uiState.value = _uiState.value.copy(mode = mode)
        serviceController.restartWhenReady(getActiveSubscriptionId())
    }

    fun switchTunStack(stack: String) {
        if (_uiState.value.tunMode == TunMode.RootTproxy) return
        val current = overrideStore.load()
        val nextTun = (current.tun ?: TunOverride()).copy(stack = stack)
        overrideStore.save(current.copy(tun = nextTun))
        _uiState.value = _uiState.value.copy(tunStack = stack)
        serviceController.restart(getActiveSubscriptionId())
    }

    fun reloadConfig() {
        serviceController.restart(getActiveSubscriptionId())
    }

    fun testLatency() {
        if (latencyJob?.isActive == true) return
        if (repository == null) return
        _uiState.value = _uiState.value.copy(isTestingLatency = true)

        latencyJob = viewModelScope.launch {
            try {
                val viaRules = LatencyProbe.entries.map { probe ->
                    async {
                        val delay = latencyTester.measure(probe.url, LATENCY_TIMEOUT_MILLIS)
                        if (delay == RuleLatencyTester.Unavailable) {
                            fallbackProbe(probe)
                            false
                        } else {
                            applyLatency(probe, delay)
                            true
                        }
                    }
                }.awaitAll()
                _uiState.value = _uiState.value.copy(latencyViaRules = viaRules.all { it })
            } finally {
                _uiState.value = _uiState.value.copy(isTestingLatency = false)
            }
        }
    }

    private suspend fun fallbackProbe(probe: LatencyProbe) {
        val result = repository?.getProxyDelay("GLOBAL", probe.url, LATENCY_TIMEOUT_MILLIS.toInt())
        applyLatency(probe, result?.getOrNull()?.delay ?: -1)
    }

    private fun applyLatency(probe: LatencyProbe, delay: Int) {
        _uiState.update {
            when (probe) {
                LatencyProbe.Baidu -> it.copy(latencyBaidu = delay)
                LatencyProbe.Cloudflare -> it.copy(latencyCloudflare = delay)
                LatencyProbe.Google -> it.copy(latencyGoogle = delay)
            }
        }
    }

    private fun disconnectStreams() {
        initialLoadJob?.cancel()
        clearProviderTraffic()
        repositorySubscriptionId = null
        trafficJob?.cancel()
        memoryJob?.cancel()
        systemInfoJob?.cancel()
        runtimeConfigJob?.cancel()
        uptimeJob?.cancel()
        latencyJob?.cancel()
        _uiState.update { it.copy(isTestingLatency = false) }
        stopConnectionRateTracking()
        onLiveProviderInfo(null, null)
        mihomoPid = -1
    }

    companion object {
        const val TRAFFIC_HISTORY_CAPACITY = 60

        private const val LATENCY_TIMEOUT_MILLIS = 5000L

        private const val TOP_CONNECTION_COUNT = 5
    }
}
