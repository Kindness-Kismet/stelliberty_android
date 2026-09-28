package com.stelliberty.android.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stelliberty.android.domain.model.LogEvent
import com.stelliberty.android.domain.model.LogLevel
import com.stelliberty.android.domain.model.LogMessage
import com.stelliberty.android.domain.repository.MihomoRepository
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Immutable
data class LogUiState(
    val isConnected: Boolean = false,
    val minimumLevel: LogLevel = LogLevel.Info,
)

@Immutable
data class IndexedLog(val id: Long, val message: LogMessage, val receivedAt: Instant)

@Immutable
data class LogExport(val fileName: String, val content: String)

class LogViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(LogUiState())
    val uiState: StateFlow<LogUiState> = _uiState.asStateFlow()

    private var nextLogId = 0L
    private val buffer = ArrayDeque<IndexedLog>(MAX_LOGS)

    private var logsDirty = false
    private val _logs = MutableStateFlow<ImmutableList<IndexedLog>>(persistentListOf())
    val logs: StateFlow<ImmutableList<IndexedLog>> = _logs.asStateFlow()

    private var repository: MihomoRepository? = null
    private var observing = false
    private var collectionJob: Job? = null

    fun setRepository(repo: MihomoRepository?) {
        if (repository === repo) return
        stopCollection()
        repository = repo
        clearLogs()
        if (observing) startCollection()
    }

    fun startObserving() {
        if (observing) return
        observing = true
        startCollection()
    }

    fun stopObserving() {
        observing = false
        stopCollection()
        flushLogs()
    }

    fun setMinimumLevel(level: LogLevel) {
        if (_uiState.value.minimumLevel == level) return
        stopCollection()
        _uiState.value = _uiState.value.copy(minimumLevel = level)
        logsDirty = true
        flushLogs()
        if (observing) startCollection()
    }

    private fun stopCollection() {
        collectionJob?.cancel()
        collectionJob = null
        _uiState.value = _uiState.value.copy(isConnected = false)
    }

    private fun startCollection() {
        val repo = repository ?: return
        val level = _uiState.value.minimumLevel

        collectionJob = viewModelScope.launch {
            launch {
                repo.logsFlow(level).collect { event ->
                    if (repository !== repo || !observing || _uiState.value.minimumLevel != level) return@collect
                    when (event) {
                        LogEvent.Connected -> _uiState.value = _uiState.value.copy(isConnected = true)
                        LogEvent.Disconnected -> _uiState.value = _uiState.value.copy(isConnected = false)
                        is LogEvent.Message -> appendLog(event.message)
                    }
                }
            }
            while (isActive) {
                flushLogs()
                delay(FLUSH_INTERVAL_MS)
            }
        }
    }

    // 高频日志先缓冲，按固定间隔发布，避免每一行都触发列表重组。
    private fun appendLog(log: LogMessage) {
        buffer.addLast(IndexedLog(nextLogId++, log, Instant.now()))
        while (buffer.size > MAX_LOGS) {
            buffer.removeFirst()
        }
        logsDirty = true
    }

    private fun flushLogs() {
        if (!logsDirty) return
        logsDirty = false
        _logs.value = filteredLogs().toPersistentList()
    }

    private fun filteredLogs(): List<IndexedLog> =
        buffer.filter { it.message.type >= _uiState.value.minimumLevel }

    fun clearLogs() {
        buffer.clear()
        logsDirty = false
        _logs.value = persistentListOf()
    }

    fun exportLogs(): LogExport? {
        val snapshot = filteredLogs()
        if (snapshot.isEmpty()) return null
        val content = buildString {
            snapshot.forEach { log ->
                append(log.receivedAt)
                append(" [").append(log.message.type.name.uppercase(Locale.ROOT)).append("] ")
                appendLine(log.message.payload)
            }
        }
        return LogExport(
            fileName = "Stelliberty-logs-${LocalDateTime.now().format(EXPORT_TIME_FORMAT)}.txt",
            content = content,
        )
    }

    companion object {
        private const val MAX_LOGS = 500
        private const val FLUSH_INTERVAL_MS = 120L
        private val EXPORT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT)
    }
}
