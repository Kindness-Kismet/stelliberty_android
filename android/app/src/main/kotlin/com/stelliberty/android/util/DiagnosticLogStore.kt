package com.stelliberty.android.util

import com.stelliberty.android.domain.model.LogLevel
import com.stelliberty.android.domain.model.LogMessage
import com.stelliberty.android.domain.model.LogSource
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class DiagnosticLogStore(private val onStorageError: (Throwable) -> Unit = {}) {
    private val buffers = LogSource.entries.associateWith { ArrayDeque<LogEntry>() }
    private var files = emptyMap<LogSource, FileLogStore>()
    private var nextId = 0L
    private val _revision = MutableStateFlow(0L)
    val revision = _revision.asStateFlow()
    private val _coreConnected = MutableStateFlow(false)
    val coreConnected = _coreConnected.asStateFlow()

    @Synchronized
    fun initialize(directory: File) {
        files = mapOf(
            LogSource.Application to FileLogStore(File(directory, "stelliberty.log")),
            LogSource.Core to FileLogStore(File(directory, "core.log")),
        )
        files.forEach { (source, file) ->
            runCatching {
                val buffer = buffers.getValue(source)
                buffer.clear()
                LogFormatter.parse(file.read()).takeLast(MAX_LOGS).forEach {
                    buffer.addLast(it.copy(id = nextId++))
                }
            }.onFailure(onStorageError)
        }
        _revision.value++
    }

    @Synchronized
    fun append(source: LogSource, level: LogLevel, tag: String, message: String): LogEntry {
        val entry = LogEntry(nextId++, LogMessage(level, message), Instant.now(), tag)
        val buffer = buffers.getValue(source)
        buffer.addLast(entry)
        while (buffer.size > MAX_LOGS) buffer.removeFirst()
        runCatching { files[source]?.append(LogFormatter.format(entry)) }.onFailure(onStorageError)
        _revision.value++
        return entry
    }

    @Synchronized
    fun snapshot(source: LogSource, minimumLevel: LogLevel): List<LogEntry> =
        buffers.getValue(source).filter { it.message.type >= minimumLevel }

    @Synchronized
    fun clear(source: LogSource) {
        files[source]?.clear()
        buffers.getValue(source).clear()
        _revision.value++
    }

    fun setCoreConnected(connected: Boolean) {
        _coreConnected.value = connected
    }

    private companion object {
        const val MAX_LOGS = 500
    }
}
