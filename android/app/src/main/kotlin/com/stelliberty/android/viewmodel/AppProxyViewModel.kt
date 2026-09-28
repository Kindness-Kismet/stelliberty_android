package com.stelliberty.android.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stelliberty.android.domain.model.AppProxyMode
import com.stelliberty.android.platform.AppInfo
import com.stelliberty.android.platform.AppListProvider
import com.stelliberty.android.platform.PlatformStorage
import com.stelliberty.android.platform.ProxyServiceBridge
import com.stelliberty.android.platform.ProxyServiceController
import com.stelliberty.android.platform.ProxyState
import com.stelliberty.android.platform.StorageKeys
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class AppProxyUiState(
    val apps: ImmutableList<AppInfo> = persistentListOf(),
    val selectedPackages: PersistentSet<String> = persistentSetOf(),
    val mode: AppProxyMode = AppProxyMode.AllowAll,
    val searchQuery: String = "",
    val showSystemApps: Boolean = false,
    val isLoading: Boolean = true,
)

class AppProxyViewModel(
    private val storage: PlatformStorage,
    private val appListProvider: AppListProvider,
    private val serviceController: ProxyServiceController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppProxyUiState())
    val uiState: StateFlow<AppProxyUiState> = _uiState.asStateFlow()

    private var initialMode: AppProxyMode = AppProxyMode.AllowAll
    private var initialPackages: Set<String> = persistentSetOf()

    private val _sortAnchor = MutableStateFlow<Set<String>>(emptySet())

    private var appsLoadJob: Job? = null

    private data class ListInput(
        val apps: ImmutableList<AppInfo>,
        val query: String,
        val showSystemApps: Boolean,
    )

    // 有意不依赖已勾选的集合：勾选只该改变复选框的样子，不该让列表重新排序，那样看着会跳。
    // 应用列表在第一个订阅者出现时才去枚举——这个 ViewModel 随冷启动创建，而多数人不会打开这一页。
    val filteredAppsFlow: StateFlow<ImmutableList<AppInfo>> = combine(
        _uiState.map { ListInput(it.apps, it.searchQuery, it.showSystemApps) }.distinctUntilChanged(),
        _sortAnchor,
    ) { (apps, query, showSystem), anchor ->
        apps.filterApps(query, showSystem)
            .sortedWith(
                compareByDescending<AppInfo> { it.packageName in anchor }
                    .thenBy { it.appName.lowercase() }
            )
            .toPersistentList()
    }
        .onStart { ensureAppsLoaded() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), persistentListOf())

    init {
        loadSavedState()
    }

    private fun loadSavedState() {
        val mode = AppProxyMode.parse(storage.getString(StorageKeys.APP_PROXY_MODE, ""))

        val packages = storage.getStringSet(StorageKeys.APP_PROXY_PACKAGES, emptySet()).toPersistentSet()

        initialMode = mode
        initialPackages = packages
        _sortAnchor.value = packages

        _uiState.value = _uiState.value.copy(mode = mode, selectedPackages = packages)
    }

    private fun ensureAppsLoaded() {
        if (appsLoadJob != null) return
        appsLoadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val apps = appListProvider.getInstalledApps()
            _uiState.value = _uiState.value.copy(apps = apps, isLoading = false)
        }
    }

    fun filteredApps(searchQuery: String = _uiState.value.searchQuery): List<AppInfo> {
        val state = _uiState.value
        if (searchQuery == state.searchQuery) return filteredAppsFlow.value
        return state.apps.filterApps(searchQuery, state.showSystemApps)
    }

    fun toggleApp(packageName: String) {
        val current = _uiState.value.selectedPackages
        applySelection(
            if (packageName in current) current.removing(packageName) else current.adding(packageName)
        )
    }

    fun selectAll() {
        applySelection(_uiState.value.selectedPackages.addingAll(visiblePackages()))
    }

    fun deselectAll() {
        applySelection(persistentSetOf())
    }

    fun invertSelection() {
        val current = _uiState.value.selectedPackages
        val visible = visiblePackages()
        applySelection(current.removingAll(visible).addingAll(visible - current))
    }

    private fun visiblePackages(): Set<String> = filteredApps().mapTo(mutableSetOf()) { it.packageName }

    private fun applySelection(packages: PersistentSet<String>) {
        _uiState.value = _uiState.value.copy(selectedPackages = packages)
        storage.putStringSet(StorageKeys.APP_PROXY_PACKAGES, packages)
    }

    fun setMode(mode: AppProxyMode) {
        _uiState.value = _uiState.value.copy(mode = mode)
        storage.putString(StorageKeys.APP_PROXY_MODE, mode.name)
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun setShowSystemApps(show: Boolean) {
        _uiState.value = _uiState.value.copy(showSystemApps = show)
    }

    fun exportPackages(): String {
        return _uiState.value.selectedPackages.sorted().joinToString("\n")
    }

    fun importPackages(text: String) {
        applySelection(
            text.lines().map { it.trim() }.filter { it.isNotEmpty() }.toPersistentSet()
        )
    }

    fun applyIfChanged(): Boolean {
        val state = _uiState.value
        val changed = state.mode != initialMode || state.selectedPackages != initialPackages
        if (!changed) return false

        val proxyState = ProxyServiceBridge.state.value.state
        if (proxyState == ProxyState.Running || proxyState == ProxyState.Starting) {
            serviceController.restart()
        }

        initialMode = state.mode
        initialPackages = state.selectedPackages
        return true
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}

private fun List<AppInfo>.filterApps(query: String, showSystemApps: Boolean): List<AppInfo> {
    val q = query.lowercase()
    return filter { app ->
        (showSystemApps || !app.isSystemApp) &&
                (q.isBlank() ||
                        app.appName.lowercase().contains(q) ||
                        app.packageName.lowercase().contains(q))
    }
}
