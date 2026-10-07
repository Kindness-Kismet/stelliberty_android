package com.stelliberty.android.util

import com.stelliberty.android.domain.model.LogLevel
import com.stelliberty.android.domain.model.LogMessage
import com.stelliberty.android.domain.model.LogSource
import java.io.File
import java.io.Writer
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// 文件按上限保存全部级别的日志供导出，内存只留最近 MAX_LOGS 条供页面查看。
// 两类日志各用一把锁：核心日志写文件、裁剪时，主线程的应用日志不必排队。
class DiagnosticLogStore(private val onStorageError: (Throwable) -> Unit = {}) {
    private class Channel {
        val buffer = ArrayDeque<LogEntry>()
        var file: FileLogStore? = null
    }

    private val channels = LogSource.entries.associateWith { Channel() }
    private val nextId = AtomicLong()
    private val _revision = MutableStateFlow(0L)
    val revision = _revision.asStateFlow()
    private val _coreConnected = MutableStateFlow(false)
    val coreConnected = _coreConnected.asStateFlow()

    fun initialize(directory: File) {
        channels.forEach { (source, channel) ->
            synchronized(channel) {
                val file = FileLogStore(File(directory, fileName(source)))
                channel.file = file
                channel.buffer.clear()
                runCatching {
                    LogFormatter.parse(file.readTail(RESTORE_BYTES)).takeLast(MAX_LOGS).forEach {
                        channel.buffer.addLast(it.copy(id = nextId.getAndIncrement()))
                    }
                }.onFailure(onStorageError)
            }
        }
        _revision.update { it + 1 }
    }

    fun append(
        source: LogSource,
        level: LogLevel,
        tag: String,
        message: String,
        receivedAt: Instant = Instant.now(),
    ): LogEntry {
        val channel = channels.getValue(source)
        val entry = synchronized(channel) {
            val entry = LogEntry(nextId.getAndIncrement(), LogMessage(level, LogRedactor.redact(message)), receivedAt, tag)
            channel.buffer.addLast(entry)
            while (channel.buffer.size > MAX_LOGS) channel.buffer.removeFirst()
            channel.file?.let { file ->
                runCatching { file.append(LogFormatter.format(entry)) }.onFailure(onStorageError)
            }
            entry
        }
        _revision.update { it + 1 }
        return entry
    }

    fun snapshot(source: LogSource, minimumLevel: LogLevel): List<LogEntry> {
        val channel = channels.getValue(source)
        return synchronized(channel) { channel.buffer.filter { it.message.type >= minimumLevel } }
    }

    // 导出是日志唯一的外发出口：文件里可能有升级前未脱敏的记录，逐行再过一遍。返回写出的行数。
    fun export(source: LogSource, output: Writer): Int {
        val channel = channels.getValue(source)
        val bytes = synchronized(channel) { channel.file }?.readBytes() ?: return 0
        var lines = 0
        bytes.inputStream().bufferedReader(Charsets.UTF_8).useLines { sequence ->
            sequence.forEach {
                output.appendLine(LogRedactor.redact(it))
                lines++
            }
        }
        return lines
    }

    fun clear(source: LogSource) {
        val channel = channels.getValue(source)
        synchronized(channel) {
            channel.file?.clear()
            channel.buffer.clear()
        }
        _revision.update { it + 1 }
    }

    fun setCoreConnected(connected: Boolean) {
        _coreConnected.value = connected
    }

    private companion object {
        const val MAX_LOGS = 500
        // 足够恢复 MAX_LOGS 条，启动耗时不随文件大小增长。
        const val RESTORE_BYTES = 256L * 1024L

        fun fileName(source: LogSource) = when (source) {
            LogSource.Application -> "stelliberty.log"
            LogSource.Core -> "core.log"
        }
    }
}
