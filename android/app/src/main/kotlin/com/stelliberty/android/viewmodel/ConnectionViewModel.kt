package com.stelliberty.android.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stelliberty.android.domain.model.ConnectionInfo
import com.stelliberty.android.domain.repository.MihomoRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
data class ConnectionUiState(
    val connections: ImmutableList<ConnectionInfo> = persistentListOf(),
    val downloadTotal: Long = 0,
    val uploadTotal: Long = 0,
    val searchQuery: String = "",
    val isConnected: Boolean = false,
    val error: String = "",
)

class ConnectionViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectionUiState())
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    private var repository: MihomoRepository? = null
    private var connectionJob: Job? = null
    private var connectionStateJob: Job? = null

    private var observing = false

    // 这个 ViewModel 是全进程单例，这里无条件开始采集会让代理一跑就常驻解析全量连接列表。
    // 所以采集的起停由连接页自己控制，这里只在页面正在看的时候才接上。
    fun setRepository(repo: MihomoRepository?) {
        if (repository === repo) return
        repository = repo
        connectionStateJob?.cancel()
        connectionJob?.cancel()
        connectionJob = null
        if (repo == null) {
            connectionStateJob = null
            _uiState.value = ConnectionUiState(searchQuery = _uiState.value.searchQuery)
            return
        }
        connectionStateJob = viewModelScope.launch {
            repo.connectionState.collect { connected ->
                _uiState.value = _uiState.value.copy(isConnected = connected)
            }
        }
        if (observing) startConnectionCollection()
    }

    fun startObserving() {
        if (observing) return
        observing = true
        startConnectionCollection()
    }

    fun stopObserving() {
        observing = false
        connectionJob?.cancel()
        connectionJob = null
        _uiState.value = _uiState.value.copy(connections = persistentListOf())
    }

    private fun startConnectionCollection() {
        connectionJob?.cancel()
        val repo = repository ?: return

        connectionJob = viewModelScope.launch {
            repo.connectionsFlow().collect { response ->
                if (repository !== repo) return@collect
                _uiState.value = _uiState.value.copy(
                    connections = response.connections.toPersistentList(),
                    downloadTotal = response.downloadTotal,
                    uploadTotal = response.uploadTotal,
                )
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun filteredConnections(searchQuery: String = _uiState.value.searchQuery): ImmutableList<ConnectionInfo> {
        val query = searchQuery.lowercase()
        if (query.isBlank()) return _uiState.value.connections
        return _uiState.value.connections.filter { conn ->
            conn.metadata.host.lowercase().contains(query) ||
                    conn.metadata.process.lowercase().contains(query) ||
                    conn.rule.lowercase().contains(query) ||
                    conn.metadata.destinationIP.contains(query) ||
                    conn.chains.any { it.lowercase().contains(query) }
        }.toPersistentList()
    }

    fun closeConnection(id: String) {
        val repo = repository ?: return
        viewModelScope.launch {
            repo.closeConnection(id)
        }
    }

    fun closeAllConnections() {
        val repo = repository ?: return
        viewModelScope.launch {
            repo.closeAllConnections()
        }
    }
}
