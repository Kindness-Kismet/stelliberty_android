package com.stelliberty.android.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stelliberty.android.domain.model.LogMessage
import com.stelliberty.android.domain.repository.MihomoRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Immutable
data class LogUiState(
    val isConnected: Boolean = false,
    val level: String = "info",
)

@Immutable
data class IndexedLog(val id: Long, val message: LogMessage)

class LogViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(LogUiState())
    val uiState: StateFlow<LogUiState> = _uiState.asStateFlow()

    private var nextLogId = 0L
    private val buffer = ArrayDeque<IndexedLog>(MAX_LOGS)

    private var logsDirty = false
    private val _logs = MutableStateFlow<ImmutableList<IndexedLog>>(persistentListOf())
    val logs: StateFlow<ImmutableList<IndexedLog>> = _logs.asStateFlow()

    private var repository: MihomoRepository? = null
    private var logJob: Job? = null
    private var flushJob: Job? = null
    private var connectionStateJob: Job? = null

    fun setRepository(repo: MihomoRepository?) {
        if (repo !== repository) {
            disconnect()
            resetLogs()
        }
        repository = repo
        connectionStateJob?.cancel()
        connectionStateJob = repo?.let {
            viewModelScope.launch {
                it.connectionState.collect { connected ->
                    _uiState.value = _uiState.value.copy(isConnected = connected)
                }
            }
        }
    }

    fun connect() {
        if (logJob?.isActive == true) return
        startLogCollection()
    }

    fun disconnect() {
        logJob?.cancel()
        logJob = null
        flushJob?.cancel()
        flushJob = null
    }

    private fun startLogCollection() {
        logJob?.cancel()
        flushJob?.cancel()
        val repo = repository ?: return

        logJob = viewModelScope.launch {
            repo.logsFlow(_uiState.value.level)
                .buffer(capacity = BUFFER_CAPACITY)
                .collect { log ->
                    appendLog(log)
                }
        }
        flushJob = viewModelScope.launch {
            while (isActive) {
                if (logsDirty) {
                    logsDirty = false
                    _logs.value = buffer.toPersistentList()
                }
                delay(FLUSH_INTERVAL_MS)
            }
        }
    }

    // 日志刷屏时能有每秒几百行。这里只写缓冲、不立刻发布，由另一条协程按显示帧率发布，
    // 把界面重组从「日志行速率」降到「屏幕刷新率」。不要改回每来一行就发布一次。
    private fun appendLog(log: LogMessage) {
        buffer.addLast(IndexedLog(nextLogId++, log))
        while (buffer.size > MAX_LOGS) {
            buffer.removeFirst()
        }
        logsDirty = true
    }

    fun clearLogs() {
        resetLogs()
    }

    private fun resetLogs() {
        buffer.clear()
        nextLogId = 0L
        logsDirty = false
        _logs.value = persistentListOf()
    }

    companion object {
        private const val MAX_LOGS = 500
        private const val BUFFER_CAPACITY = 64
        private const val FLUSH_INTERVAL_MS = 120L
    }
}
