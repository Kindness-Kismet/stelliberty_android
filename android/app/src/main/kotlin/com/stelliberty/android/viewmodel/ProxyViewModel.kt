package com.stelliberty.android.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stelliberty.android.data.store.ProxySelectionStore
import com.stelliberty.android.domain.model.ProxyPreview
import com.stelliberty.android.domain.repository.MihomoRepository
import com.stelliberty.android.domain.repository.ProxyPreviewRepository
import com.stelliberty.android.platform.PlatformStorage
import com.stelliberty.android.platform.StorageKeys
import com.stelliberty.android.util.AppLogger
import com.stelliberty.android.util.describe
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Immutable
data class ProxyGroupUi(
    val name: String = "",
    val type: String = "",
    val now: String = "",
    val all: ImmutableList<String> = persistentListOf(),
    val delays: ImmutableMap<String, Int> = persistentMapOf(),
    val nodeTypes: ImmutableMap<String, String> = persistentMapOf(),
    val icon: String = "",
    val fixed: String = "",
) {
    val isSelectable: Boolean
        get() = isSelector ||
            type.equals("URLTest", ignoreCase = true) ||
            type.equals("Fallback", ignoreCase = true)

    val isSelector: Boolean get() = type.equals("Selector", ignoreCase = true)

    val isFixed: Boolean get() = fixed.isNotEmpty()
}

@Immutable
data class ProxyUiState(
    val groups: ImmutableList<ProxyGroupUi> = persistentListOf(),
    val testingGroups: ImmutableSet<String> = persistentSetOf(),
    val testingNodes: ImmutableSet<String> = persistentSetOf(),
    val error: String = "",
    val mode: String = "",
    val isProxyRunning: Boolean = false,
)

class ProxyViewModel(
    private val proxySelections: ProxySelectionStore? = null,
    private val getActiveUuid: () -> String? = { null },
    private val storage: PlatformStorage? = null,
    private val previewRepository: ProxyPreviewRepository? = null,
    activeUuid: Flow<String?> = emptyFlow(),
    autoDelayRuns: Flow<MihomoRepository> = emptyFlow(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProxyUiState())
    val uiState: StateFlow<ProxyUiState> = _uiState.asStateFlow()

    private val _sortOption = MutableStateFlow(loadInitialSortOption())
    val sortOption: StateFlow<Int> = _sortOption.asStateFlow()

    private val _singleColumn = MutableStateFlow(loadInitialSingleColumn())
    val singleColumn: StateFlow<Boolean> = _singleColumn.asStateFlow()

    private val _groupTabs = MutableStateFlow(
        storage?.getString(StorageKeys.PROXY_GROUP_TABS, "true") != "false",
    )
    val groupTabs: StateFlow<Boolean> = _groupTabs.asStateFlow()

    private val _selectedGroupName = MutableStateFlow(
        storage?.getString(StorageKeys.PROXY_SELECTED_GROUP, "").orEmpty(),
    )
    val selectedGroupName: StateFlow<String> = _selectedGroupName.asStateFlow()

    private val _expandedGroups = MutableStateFlow(persistentSetOf<String>())
    val expandedGroups: StateFlow<ImmutableSet<String>> = _expandedGroups.asStateFlow()

    fun toggleExpandedGroup(name: String) {
        val current = _expandedGroups.value
        _expandedGroups.value = if (name in current) current.removing(name) else current.adding(name)
    }

    // 宽屏外壳切换会重新创建页面，滚动位置不能只依赖组合树内的保存键。
    private val scrollPositions = mutableMapOf<String, Pair<Int, Int>>()

    fun scrollPosition(key: String): Pair<Int, Int> = scrollPositions[key] ?: (0 to 0)

    fun updateScrollPosition(key: String, index: Int, offset: Int) {
        scrollPositions[key] = index to offset
    }

    fun updateSelectedGroupName(name: String) {
        _selectedGroupName.value = name
        storage?.putString(StorageKeys.PROXY_SELECTED_GROUP, name)
    }

    fun updateGroupTabs(enabled: Boolean) {
        _groupTabs.value = enabled
        storage?.putString(StorageKeys.PROXY_GROUP_TABS, enabled.toString())
    }

    private val _showGlobalGroup = MutableStateFlow(loadInitialShowGlobalGroup())
    val showGlobalGroup: StateFlow<Boolean> = _showGlobalGroup.asStateFlow()

    private val _hideUnavailableNodes = MutableStateFlow(loadInitialHideUnavailable())
    val hideUnavailableNodes: StateFlow<Boolean> = _hideUnavailableNodes.asStateFlow()

    private class Session(val repository: MihomoRepository, val uuid: String?, val scope: CoroutineScope) {
        var selectionsRestored = false
    }

    private var session: Session? = null
    private var loadJob: Job? = null
    private var previewJob: Job? = null
    private val stateMutex = Mutex()

    private var nodeProviderMap: Map<String, String> = emptyMap()

    fun updateSortOption(option: Int) {
        _sortOption.value = option
        storage?.putString(StorageKeys.PROXY_NODE_SORT_OPTION, option.toString())
    }

    private fun loadInitialSortOption(): Int =
        storage?.getString(StorageKeys.PROXY_NODE_SORT_OPTION, "0")?.toIntOrNull() ?: 0

    fun updateSingleColumn(enabled: Boolean) {
        _singleColumn.value = enabled
        storage?.putString(StorageKeys.PROXY_NODE_SINGLE_COLUMN, if (enabled) "true" else "false")
    }

    private fun loadInitialSingleColumn(): Boolean =
        storage?.getString(StorageKeys.PROXY_NODE_SINGLE_COLUMN, "false") == "true"

    fun updateHideUnavailableNodes(enabled: Boolean) {
        _hideUnavailableNodes.value = enabled
        storage?.putString(StorageKeys.PROXY_HIDE_UNAVAILABLE_NODES, if (enabled) "true" else "false")
    }

    private fun loadInitialHideUnavailable(): Boolean =
        storage?.getString(StorageKeys.PROXY_HIDE_UNAVAILABLE_NODES, "false") == "true"

    fun updateShowGlobalGroup(enabled: Boolean) {
        _showGlobalGroup.value = enabled
        storage?.putString(StorageKeys.PROXY_SHOW_GLOBAL_GROUP, if (enabled) "true" else "false")
    }

    private fun loadInitialShowGlobalGroup(): Boolean =
        storage?.getString(StorageKeys.PROXY_SHOW_GLOBAL_GROUP, "true") != "false"

    init {
        viewModelScope.launch {
            autoDelayRuns.collect { repo -> if (session?.repository === repo) loadProxies() }
        }
        viewModelScope.launch {
            activeUuid.collect { uuid ->
                // 无内核时切订阅直接换预览；运行中的列表由 setRepository 与 restoreSelections 接手。
                if (session == null) loadPreview(uuid)
            }
        }
    }

    // 每个内核实例独占任务父节点；切换时取消请求和排队操作，响应再核对订阅归属。
    fun setRepository(repo: MihomoRepository?) {
        if (repo != null && session?.repository === repo) return
        if (repo == null && session == null) return
        session?.scope?.cancel()
        previewJob?.cancel()
        previewJob = null
        session = repo?.let {
            Session(
                it,
                getActiveUuid(),
                CoroutineScope(viewModelScope.coroutineContext + SupervisorJob(viewModelScope.coroutineContext[Job])),
            )
        }
        loadJob = null
        nodeProviderMap = emptyMap()
        _uiState.value = ProxyUiState(isProxyRunning = repo != null)
        if (repo != null) loadProxies() else loadPreview()
    }

    fun loadProxies() {
        val current = session ?: run {
            // 内核不在时的刷新请求按预览处理：进代理页时没有 repository 变更可依赖。
            loadPreview()
            return
        }

        loadJob?.cancel()
        loadJob = current.scope.launch {
            // 刷新、恢复、选择与解除固定共用一把锁，快照发布和内核写入保持同一顺序。
            stateMutex.withLock {
                if (!isCurrent(current)) return@withLock
                _uiState.value = _uiState.value.copy(error = "")
                loadProxies(current)
            }
        }
    }

    // 内核未运行时从当前订阅解析静态预览；选择沿用 ProxySelectionStore，内核启动后由 restoreSelections 落到内核。
    private fun loadPreview(uuid: String? = null) {
        val repository = previewRepository ?: return
        val id = uuid ?: getActiveUuid() ?: return
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            val preview = repository.load(id) ?: return@launch
            stateMutex.withLock {
                // 预览只服务「无内核 + 当前订阅」两个前提，任一变化都说明结果已过期。
                if (session != null || getActiveUuid() != id) return@withLock
                _uiState.value = ProxyUiState(groups = mapPreview(id, preview), isProxyRunning = false)
            }
        }
    }

    // 配置里的组类型是内核 CLI 写法（select / url-test），映射成运行时 API 的名字才能复用选中态判断。
    private fun mapPreview(uuid: String, preview: ProxyPreview): ImmutableList<ProxyGroupUi> {
        val selections = proxySelections?.selections(uuid).orEmpty()
        val nodeTypes = mutableMapOf<String, String>()
        preview.nodes.forEach { if (it.type.isNotEmpty()) nodeTypes[it.name] = it.type }
        return preview.groups.map { group ->
            val saved = selections[group.name]
            val type = PREVIEW_GROUP_TYPES[group.type.lowercase()] ?: group.type
            ProxyGroupUi(
                name = group.name,
                type = type,
                // 选择器没有保存过选择时与内核默认一致：取首个成员；URLTest/Fallback 的当前值内核才知道。
                now = when {
                    saved != null -> saved
                    type == "Selector" -> group.all.firstOrNull().orEmpty()
                    else -> ""
                },
                all = group.all.toPersistentList(),
                nodeTypes = nodeTypes.toPersistentMap(),
                icon = group.icon,
                fixed = if (saved != null && type != "Selector") saved else "",
            )
        }.toPersistentList()
    }

    private suspend fun loadProxies(current: Session) = coroutineScope {
        val groupsDeferred = async { current.repository.getGroups() }
        val proxiesDeferred = async { current.repository.getProxies() }
        val providersDeferred = async { current.repository.getProviders() }
        val configDeferred = async { current.repository.getConfig() }
        val groupsResult = groupsDeferred.await()
        val proxiesResult = proxiesDeferred.await()
        val providersResult = providersDeferred.await()
        val mode = configDeferred.await().getOrNull()?.mode?.lowercase().orEmpty()
        if (!isCurrent(current)) return@coroutineScope

        groupsResult.onSuccess { groupsResponse ->
            // 数千节点的延迟/类型映射在主线程要十余毫秒，正好落在切页动画期间；
            // 入参与产物都是不可变结构，搬到 Default 后只有 nodeProviderMap 要回主线程赋值。
            val mapped = withContext(Dispatchers.Default) {
                val runtimeProxies = proxiesResult.getOrNull()?.proxies ?: emptyMap()
                val allProxies = runtimeProxies.toMutableMap()
                val providerOf = mutableMapOf<String, String>()
                providersResult.getOrNull()?.providers?.forEach { (providerName, provider) ->
                    provider.proxies.forEach { node ->
                        if (node.name !in runtimeProxies) {
                            allProxies.putIfAbsent(node.name, node)
                            providerOf.putIfAbsent(node.name, providerName)
                        }
                    }
                }

                val globalGroup = groupsResponse.proxies.firstOrNull { it.name == GLOBAL_GROUP }
                val orderMap = globalGroup?.all
                    ?.mapIndexed { index, name -> name to index }
                    ?.toMap() ?: emptyMap()

                val orderedGroups = groupsResponse.proxies
                    .filter { it.name != GLOBAL_GROUP }
                    .sortedBy { orderMap[it.name] ?: Int.MAX_VALUE }
                val arrangedGroups = when {
                    globalGroup == null -> orderedGroups
                    mode == MODE_GLOBAL -> listOf(globalGroup) + orderedGroups
                    else -> orderedGroups + globalGroup
                }

                arrangedGroups
                    .map { node ->
                        val delays = mutableMapOf<String, Int>()
                        val nodeTypes = mutableMapOf<String, String>()
                        node.all.forEach { proxyName ->
                            val proxy = allProxies[proxyName]
                            val lastDelay = proxy?.history?.lastOrNull()?.delay
                            if (lastDelay != null && lastDelay > 0) {
                                delays[proxyName] = lastDelay
                            } else if (lastDelay == 0) {
                                delays[proxyName] = -1
                            } else if (proxy != null && proxy.now.isNotEmpty()) {
                                val nowProxy = allProxies[proxy.now]
                                val nowDelay = nowProxy?.history?.lastOrNull()?.delay
                                if (nowDelay != null && nowDelay > 0) {
                                    delays[proxyName] = nowDelay
                                } else if (nowDelay == 0) {
                                    delays[proxyName] = -1
                                }
                            }
                            if (proxy != null && proxy.type.isNotEmpty()) {
                                nodeTypes[proxyName] = proxy.type
                            }
                        }
                        ProxyGroupUi(
                            name = node.name,
                            type = node.type,
                            now = node.now,
                            all = node.all.toPersistentList(),
                            delays = delays.toPersistentMap(),
                            nodeTypes = nodeTypes.toPersistentMap(),
                            icon = node.icon,
                            fixed = node.fixed,
                        )
                    }
                    .toPersistentList() to providerOf
            }
            if (!isCurrent(current)) return@onSuccess
            val groups = mapped.first
            nodeProviderMap = mapped.second
            _uiState.value = _uiState.value.copy(groups = groups, mode = mode)
            if (!current.selectionsRestored) {
                current.selectionsRestored = restoreSelections(current, groups)
            }
        }.onFailure {
            AppLogger.warn(TAG, "loadProxies failed", it)
            _uiState.value = _uiState.value.copy(error = it.describe())
        }
    }

    fun selectProxy(group: String, proxy: String) {
        val current = session ?: run {
            selectPreviewProxy(group, proxy)
            return
        }
        current.scope.launch {
            stateMutex.withLock {
                if (!isCurrent(current)) return@withLock
                val target = groupOf(group) ?: return@withLock
                if (!target.isSelectable) return@withLock
                _uiState.value = _uiState.value.copy(error = "")
                val result = current.repository.selectProxy(group, proxy)
                if (!isCurrent(current)) return@withLock
                result.onSuccess {
                    _uiState.value = _uiState.value.copy(
                        groups = _uiState.value.groups
                            .map {
                                when {
                                    it.name != group -> it
                                    it.isSelector -> it.copy(now = proxy)
                                    else -> it.copy(now = proxy, fixed = proxy)
                                }
                            }
                            .toPersistentList(),
                    )
                    current.uuid?.let { proxySelections?.select(it, group, proxy) }
                }.onFailure {
                    AppLogger.warn(TAG, "selectProxy failed", it)
                    _uiState.value = _uiState.value.copy(error = it.describe())
                }
            }
        }
    }

    // 预览态的选择不经过内核：只更新本地状态并落 ProxySelectionStore，内核启动后 restoreSelections 会应用同一份选择。
    private fun selectPreviewProxy(group: String, proxy: String) {
        val uuid = getActiveUuid() ?: return
        viewModelScope.launch {
            stateMutex.withLock {
                if (session != null || getActiveUuid() != uuid) return@withLock
                val target = groupOf(group) ?: return@withLock
                if (!target.isSelectable || proxy !in target.all) return@withLock
                _uiState.value = _uiState.value.copy(
                    groups = _uiState.value.groups
                        .map {
                            when {
                                it.name != group -> it
                                it.isSelector -> it.copy(now = proxy)
                                else -> it.copy(now = proxy, fixed = proxy)
                            }
                        }
                        .toPersistentList(),
                )
                proxySelections?.select(uuid, group, proxy)
            }
        }
    }

    // 记录的选择也要一并删掉，它是恢复选择的数据来源，留着下次连接时会把刚解除的固定又推回去。
    fun unfixProxy(group: String) {
        val current = session ?: run {
            unfixPreviewProxy(group)
            return
        }
        current.scope.launch {
            stateMutex.withLock {
                if (!isCurrent(current)) return@withLock
                _uiState.value = _uiState.value.copy(error = "")
                val result = current.repository.unfixProxy(group)
                if (!isCurrent(current)) return@withLock
                result.onSuccess {
                    current.uuid?.let { proxySelections?.clear(it, group) }
                    loadProxies(current)
                }.onFailure {
                    AppLogger.warn(TAG, "unfixProxy failed", it)
                    _uiState.value = _uiState.value.copy(error = it.describe())
                }
            }
        }
    }

    private fun unfixPreviewProxy(group: String) {
        val uuid = getActiveUuid() ?: return
        viewModelScope.launch {
            stateMutex.withLock {
                if (session != null || getActiveUuid() != uuid) return@withLock
                _uiState.value = _uiState.value.copy(
                    groups = _uiState.value.groups
                        .map { if (it.name == group) it.copy(now = "", fixed = "") else it }
                        .toPersistentList(),
                )
                proxySelections?.clear(uuid, group)
            }
        }
    }

    fun testGroupDelay(group: String) {
        val current = session ?: return
        if (group in _uiState.value.testingGroups) return
        val nodes = groupOf(group)?.all ?: return
        if (nodes.isEmpty()) return
        _uiState.value = _uiState.value.copy(
            testingGroups = (_uiState.value.testingGroups + group).toPersistentSet(),
        )

        current.scope.launch {
            try {
                current.repository.testDelays(nodes, nodeProviderMap)
                if (!isCurrent(current)) return@launch
                loadProxies()
            } finally {
                if (isCurrent(current)) {
                    _uiState.value = _uiState.value.copy(
                        testingGroups = (_uiState.value.testingGroups - group).toPersistentSet(),
                    )
                }
            }
        }
    }

    fun testNodeDelay(nodeName: String) {
        val current = session ?: return
        if (nodeName in _uiState.value.testingNodes) return
        _uiState.value = _uiState.value.copy(
            testingNodes = (_uiState.value.testingNodes + nodeName).toPersistentSet(),
        )

        current.scope.launch {
            try {
                val provider = nodeProviderMap[nodeName]
                if (provider != null) {
                    current.repository.getProviderProxyDelay(provider, nodeName)
                } else {
                    current.repository.getProxyDelay(nodeName)
                }
                if (!isCurrent(current)) return@launch
                loadProxies()
            } finally {
                if (isCurrent(current)) {
                    _uiState.value = _uiState.value.copy(
                        testingNodes = (_uiState.value.testingNodes - nodeName).toPersistentSet(),
                    )
                }
            }
        }
    }

    private fun groupOf(name: String): ProxyGroupUi? =
        _uiState.value.groups.firstOrNull { it.name == name }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = "")
    }

    private fun isCurrent(current: Session): Boolean =
        session === current && getActiveUuid() == current.uuid

    private suspend fun restoreSelections(
        current: Session,
        groups: ImmutableList<ProxyGroupUi>,
    ): Boolean {
        val uuid = current.uuid ?: return true
        val selectionMap = proxySelections?.selections(uuid).orEmpty()
        if (selectionMap.isEmpty()) return true

        val updatedGroups = groups.toMutableList()
        var restored = true

        for ((index, group) in groups.withIndex()) {
            if (!isCurrent(current)) return false
            if (!group.isSelectable) continue
            val saved = selectionMap[group.name] ?: continue
            if (saved !in group.all) continue
            if (saved == (if (group.isSelector) group.now else group.fixed)) continue

            val result = current.repository.selectProxy(group.name, saved)
            if (!isCurrent(current)) return false
            result.onSuccess {
                updatedGroups[index] = if (group.isSelector) {
                    group.copy(now = saved)
                } else {
                    group.copy(now = saved, fixed = saved)
                }
            }.onFailure {
                restored = false
                AppLogger.warn(TAG, "restoreSelections failed", it)
                _uiState.value = _uiState.value.copy(error = it.describe())
            }
        }

        _uiState.value = _uiState.value.copy(groups = updatedGroups.toPersistentList())
        return restored
    }

    companion object {
        const val GLOBAL_GROUP = "GLOBAL"
        const val MODE_GLOBAL = "global"
        const val MODE_DIRECT = "direct"
        private const val TAG = "ProxyViewModel"

        // 预览拿到的是配置写法，这里补齐与运行时 /group 一致的类型名；未知类型原样展示（不可选）。
        private val PREVIEW_GROUP_TYPES = mapOf(
            "select" to "Selector",
            "url-test" to "URLTest",
            "fallback" to "Fallback",
            "load-balance" to "LoadBalance",
            "relay" to "Relay",
        )
    }
}
