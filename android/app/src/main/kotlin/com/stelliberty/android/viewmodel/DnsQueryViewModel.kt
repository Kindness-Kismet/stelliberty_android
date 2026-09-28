package com.stelliberty.android.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stelliberty.android.domain.model.DnsAnswer
import com.stelliberty.android.domain.repository.MihomoRepository
import com.stelliberty.android.util.describe
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
data class DnsQueryUiState(
    val queryName: String = "",
    val queryType: String = "A",
    val answers: ImmutableList<DnsAnswer> = persistentListOf(),
    val status: Int? = null,
    val isQuerying: Boolean = false,
    val error: String = "",
)

class DnsQueryViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DnsQueryUiState())
    val uiState: StateFlow<DnsQueryUiState> = _uiState.asStateFlow()

    private var repository: MihomoRepository? = null
    private var queryJob: Job? = null

    // 一次性请求同样要取消旧协程，否则旧连接的失败结果会覆盖新界面，还会把「查询中」的状态卡住。
    fun setRepository(repo: MihomoRepository?) {
        if (repository === repo) return
        queryJob?.cancel()
        queryJob = null
        repository = repo
        _uiState.value = _uiState.value.copy(isQuerying = false)
    }

    fun setQueryName(name: String) {
        _uiState.value = _uiState.value.copy(queryName = name)
    }

    fun setQueryType(type: String) {
        _uiState.value = _uiState.value.copy(queryType = type)
    }

    fun queryDns() {
        val repo = repository ?: return
        val state = _uiState.value
        if (state.queryName.isBlank() || state.isQuerying) return

        _uiState.value = state.copy(isQuerying = true, error = "", answers = persistentListOf(), status = null)

        queryJob = viewModelScope.launch {
            val result = repo.queryDns(state.queryName, state.queryType)
            if (repository !== repo) return@launch
            result
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(
                        answers = response.Answer.toPersistentList(),
                        status = response.Status,
                        isQuerying = false,
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isQuerying = false,
                        error = it.describe(),
                    )
                }
        }
    }

    fun flushDnsCache() {
        val repo = repository ?: return
        viewModelScope.launch {
            repo.flushDnsCache()
        }
    }

    fun flushFakeIp() {
        val repo = repository ?: return
        viewModelScope.launch {
            repo.flushFakeIp()
        }
    }
}
