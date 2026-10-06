package com.stelliberty.android.util

import androidx.compose.runtime.Immutable
import com.stelliberty.android.domain.model.LogLevel
import com.stelliberty.android.domain.model.LogMessage
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Immutable
data class LogEntry(val id: Long, val message: LogMessage, val receivedAt: Instant, val tag: String)

object LogFormatter {
    private val timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)
    private val record = Regex("^\\[([DIWE])] (.{23}) \\[(.*?)] (.*)$")
    private val legacyRecord = Regex("^(.{23}) ([DIWE])/(.*?): (.*)$")

    fun time(instant: Instant): String = timeFormat.format(instant.atZone(ZoneId.systemDefault()))

    fun format(entry: LogEntry): String =
        "[${entry.message.type.name.first()}] ${time(entry.receivedAt)} [${entry.tag}] ${entry.message.payload}"

    fun parse(text: String): List<LogEntry> {
        val entries = mutableListOf<LogEntry>()
        text.removeSuffix("\n").lineSequence().forEach { line ->
            val match = record.matchEntire(line)
            val legacy = if (match == null) legacyRecord.matchEntire(line) else null
            val fields = match?.groupValues ?: legacy?.groupValues
            if (fields != null) {
                val level = fields[if (legacy != null) 2 else 1]
                val timestamp = fields[if (legacy != null) 1 else 2]
                val instant = runCatching {
                    LocalDateTime.parse(timestamp, timeFormat).atZone(ZoneId.systemDefault()).toInstant()
                }.getOrNull()
                if (instant != null) {
                    entries += LogEntry(
                        entries.size.toLong(),
                        LogMessage(LogLevel.entries.first { it.name.startsWith(level) }, fields[4]),
                        instant,
                        fields[3],
                    )
                    return@forEach
                }
            }
            if (entries.isNotEmpty()) {
                val previous = entries.last()
                entries[entries.lastIndex] = previous.copy(
                    message = previous.message.copy(payload = previous.message.payload + "\n" + line),
                )
            }
        }
        return entries
    }
}
