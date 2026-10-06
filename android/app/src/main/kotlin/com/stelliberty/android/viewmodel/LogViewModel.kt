package com.stelliberty.android.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stelliberty.android.domain.model.LogLevel
import com.stelliberty.android.domain.model.LogSource
import com.stelliberty.android.util.AppLogger
import com.stelliberty.android.util.DiagnosticLogStore
import com.stelliberty.android.util.LogEntry
import com.stelliberty.android.util.LogFormatter
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class LogUiState(
    val source: LogSource = LogSource.Application,
    val isConnected: Boolean = false,
    val minimumLevel: LogLevel = LogLevel.Info,
)

@Immutable
data class LogExport(val fileName: String, val content: String)

class LogViewModel(private val store: DiagnosticLogStore = AppLogger.logs) : ViewModel() {
    private val _uiState = MutableStateFlow(LogUiState())
    val uiState: StateFlow<LogUiState> = _uiState.asStateFlow()
    private val _logs = MutableStateFlow<ImmutableList<LogEntry>>(persistentListOf())
    val logs: StateFlow<ImmutableList<LogEntry>> = _logs.asStateFlow()
    private var collectionJob: Job? = null
    private var lastRevision = -1L

    fun startObserving() {
        if (collectionJob != null) return
        collectionJob = viewModelScope.launch {
            while (isActive) {
                refreshLogs()
                delay(120)
            }
        }
    }

    fun stopObserving() {
        collectionJob?.cancel()
        collectionJob = null
    }

    fun setSource(source: LogSource) {
        if (_uiState.value.source == source) return
        _uiState.value = _uiState.value.copy(source = source)
        refreshLogs(force = true)
    }

    fun setMinimumLevel(level: LogLevel) {
        if (_uiState.value.minimumLevel == level) return
        _uiState.value = _uiState.value.copy(minimumLevel = level)
        refreshLogs(force = true)
    }

    // 采集独立于页面生命周期，界面只按固定间隔发布快照。
    private fun refreshLogs(force: Boolean = false) {
        _uiState.value = _uiState.value.copy(isConnected = store.coreConnected.value)
        val revision = store.revision.value
        if (!force && lastRevision == revision) return
        lastRevision = revision
        _logs.value = snapshot().toPersistentList()
    }

    private fun snapshot(): List<LogEntry> = store.snapshot(_uiState.value.source, _uiState.value.minimumLevel)

    suspend fun clearLogs(): Result<Unit> {
        val source = _uiState.value.source
        return withContext(Dispatchers.IO) { runCatching { store.clear(source) } }
            .onSuccess { refreshLogs(force = true) }
            .onFailure { AppLogger.error("LogExport", "Failed to clear $source logs", it) }
    }

    fun exportLogs(): LogExport {
        val source = _uiState.value.source
        val snapshot = snapshot()
        val content = buildString {
            appendLine("Stelliberty ${source.name} logs")
            appendLine("Time zone: ${ZoneId.systemDefault()}; minimum level: ${_uiState.value.minimumLevel}")
            snapshot.forEach { appendLine(LogFormatter.format(it)) }
            if (snapshot.isEmpty()) appendLine("No matching log entries.")
        }
        return LogExport(
            fileName = "Stelliberty-${source.name.lowercase(Locale.ROOT)}-${LocalDateTime.now().format(EXPORT_TIME_FORMAT)}.txt",
            content = content,
        )
    }

    private companion object {
        val EXPORT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT)
    }
}
