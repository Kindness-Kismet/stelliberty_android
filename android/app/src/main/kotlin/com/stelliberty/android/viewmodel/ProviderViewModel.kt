package com.stelliberty.android.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stelliberty.android.domain.repository.MihomoRepository
import com.stelliberty.android.util.describe
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProviderItemUi(
    val name: String,
    val type: String,
    val vehicleType: String,
    val updatedAt: String,
    val isRuleProvider: Boolean,
)

@Immutable
data class ProviderErrorKey(
    val name: String,
    val isRuleProvider: Boolean,
)

data class RefreshProgress(
    val completed: Int,
    val total: Int,
    val singleName: String? = null,
)

@Immutable
data class ProviderUiState(
    val providers: ImmutableList<ProviderItemUi> = persistentListOf(),
    val isLoading: Boolean = false,
    val providerErrors: ImmutableMap<ProviderErrorKey, String> = persistentMapOf(),
    val refresh: RefreshProgress? = null,
    // 不能拿「订阅源列表为空」反推代理没在跑：节点直接写在配置里的订阅一个源都没有，
    // 那样会在代理正常运行时谎报「请先启动服务」。
    val isProxyRunning: Boolean = false,
)

class ProviderViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ProviderUiState())
    val uiState: StateFlow<ProviderUiState> = _uiState.asStateFlow()

    private var repository: MihomoRepository? = null

    private var loadJob: Job? = null
    private var refreshJob: Job? = null

    fun setRepository(repo: MihomoRepository?) {
        if (repository === repo) return
        loadJob?.cancel()
        refreshJob?.cancel()
        repository = repo
        if (repo != null) {
            _uiState.value = ProviderUiState(isLoading = true, isProxyRunning = true)
            loadProviders()
        } else {
            _uiState.value = ProviderUiState()
        }
    }

    fun loadProviders() {
        val repo = repository ?: return
        _uiState.update { it.copy(isLoading = true) }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val items = mutableListOf<ProviderItemUi>()

            repo.getProviders().onSuccess { response ->
                response.providers.values
                    .filter { it.vehicleType != "Compatible" }
                    .forEach { info ->
                        items.add(
                            ProviderItemUi(
                                name = info.name,
                                type = info.type,
                                vehicleType = info.vehicleType,
                                updatedAt = info.updatedAt,
                                isRuleProvider = false,
                            )
                        )
                    }
            }

            repo.getRuleProviders().onSuccess { response ->
                response.providers.values
                    .filter { it.vehicleType != "Compatible" }
                    .forEach { info ->
                        items.add(
                            ProviderItemUi(
                                name = info.name,
                                type = info.vehicleType,
                                vehicleType = info.vehicleType,
                                updatedAt = info.updatedAt,
                                isRuleProvider = true,
                            )
                        )
                    }
            }

            if (repository !== repo) return@launch
            val liveKeys = items.map { ProviderErrorKey(it.name, it.isRuleProvider) }.toSet()
            _uiState.update { state ->
                state.copy(
                    providers = items.sortedWith(compareBy({ it.isRuleProvider }, { it.name })).toPersistentList(),
                    isLoading = false,
                    providerErrors = state.providerErrors.filterKeys { it in liveKeys }.toPersistentMap(),
                )
            }
        }
    }

    fun updateProvider(name: String, isRuleProvider: Boolean) {
        val repo = repository ?: return
        if (_uiState.value.refresh != null) return
        val errorKey = ProviderErrorKey(name, isRuleProvider)

        _uiState.update {
            it.copy(
                refresh = RefreshProgress(0, 1, singleName = name),
                providerErrors = it.providerErrors.toPersistentMap().removing(errorKey),
            )
        }

        refreshJob = viewModelScope.launch {
            val result = if (isRuleProvider) repo.updateRuleProvider(name) else repo.updateProvider(name)
            if (repository !== repo) return@launch
            val error = result.exceptionOrNull()?.describe()
            _uiState.update {
                it.copy(
                    refresh = null,
                    providerErrors = if (error == null) {
                        it.providerErrors.toPersistentMap().removing(errorKey)
                    } else {
                        it.providerErrors.toPersistentMap().putting(errorKey, error)
                    },
                )
            }
            if (result.isSuccess) loadProviders()
        }
    }

    fun updateAll() {
        val repo = repository ?: return
        val snapshot = _uiState.value.providers
        if (snapshot.isEmpty()) return
        if (_uiState.value.refresh != null) return

        _uiState.update {
            it.copy(
                refresh = RefreshProgress(0, snapshot.size),
                providerErrors = persistentMapOf(),
            )
        }

        refreshJob = viewModelScope.launch {
            snapshot.map { provider ->
                async {
                    val res = if (provider.isRuleProvider) repo.updateRuleProvider(provider.name)
                    else repo.updateProvider(provider.name)
                    if (repository !== repo) return@async
                    val error = res.exceptionOrNull()?.describe()
                    val errorKey = ProviderErrorKey(provider.name, provider.isRuleProvider)
                    _uiState.update { state ->
                        val cur = state.refresh ?: return@update state
                        state.copy(
                            refresh = cur.copy(completed = cur.completed + 1),
                            providerErrors = if (error == null) {
                                state.providerErrors.toPersistentMap().removing(errorKey)
                            } else {
                                state.providerErrors.toPersistentMap().putting(errorKey, error)
                            },
                        )
                    }
                }
            }.awaitAll()

            if (repository !== repo) return@launch
            _uiState.update {
                it.copy(
                    refresh = null,
                )
            }
            loadProviders()
        }
    }
}
