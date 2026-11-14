package com.stelliberty.android.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stelliberty.android.domain.repository.MihomoRepository
import com.stelliberty.android.R
import com.stelliberty.android.util.AppLogger
import com.stelliberty.android.util.OperationFeedback
import kotlinx.coroutines.CancellationException
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
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

data class RefreshProgress(
    val completed: Int,
    val total: Int,
    val singleName: String? = null,
)

@Immutable
data class ProviderUiState(
    val providers: ImmutableList<ProviderItemUi> = persistentListOf(),
    val isLoading: Boolean = false,
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
            _uiState.update { state ->
                state.copy(
                    providers = items.sortedWith(compareBy({ it.isRuleProvider }, { it.name })).toPersistentList(),
                    isLoading = false,
                )
            }
        }
    }

    fun updateProvider(name: String, isRuleProvider: Boolean) {
        val repo = repository ?: return
        if (_uiState.value.refresh != null) {
            OperationFeedback.show(R.string.operation_in_progress)
            return
        }
        _uiState.update { it.copy(refresh = RefreshProgress(0, 1, singleName = name)) }
        refreshJob = viewModelScope.launch {
            val result = if (isRuleProvider) repo.updateRuleProvider(name) else repo.updateProvider(name)
            if (repository !== repo) return@launch
            result.exceptionOrNull()?.let {
                if (it is CancellationException) throw it
                AppLogger.warn("ProviderViewModel", "Provider update failed: $name", it)
            }
            _uiState.update { it.copy(refresh = null) }
            OperationFeedback.updateResult(if (result.isSuccess) 1 else 0, if (result.isFailure) 1 else 0)
            if (result.isSuccess) loadProviders()
        }
    }

    fun cancelRefresh() {
        if (_uiState.value.refresh != null) OperationFeedback.show(R.string.operation_cancelled)
        refreshJob?.cancel()
        refreshJob = null
        _uiState.update { it.copy(refresh = null) }
        loadProviders()
    }

    fun updateAll() {
        val repo = repository ?: return
        val snapshot = _uiState.value.providers
        if (snapshot.isEmpty()) {
            OperationFeedback.updateResult(0, 0)
            return
        }
        if (_uiState.value.refresh != null) {
            OperationFeedback.show(R.string.operation_in_progress)
            return
        }
        _uiState.update { it.copy(refresh = RefreshProgress(0, snapshot.size)) }
        refreshJob = viewModelScope.launch {
            val results = snapshot.map { provider ->
                async {
                    val result = if (provider.isRuleProvider) repo.updateRuleProvider(provider.name)
                    else repo.updateProvider(provider.name)
                    result.exceptionOrNull()?.let {
                        if (it is CancellationException) throw it
                        AppLogger.warn("ProviderViewModel", "Provider update failed: ${provider.name}", it)
                    }
                    if (repository === repo) {
                        _uiState.update { state ->
                            val progress = state.refresh ?: return@update state
                            state.copy(refresh = progress.copy(completed = progress.completed + 1))
                        }
                    }
                    result.isSuccess
                }
            }.awaitAll()
            if (repository !== repo) return@launch
            _uiState.update { it.copy(refresh = null) }
            val succeeded = results.count { it }
            OperationFeedback.updateResult(succeeded, results.size - succeeded)
            loadProviders()
        }
    }
}
