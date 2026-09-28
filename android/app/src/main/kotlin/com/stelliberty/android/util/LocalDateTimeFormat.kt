package com.stelliberty.android.util

import android.text.format.DateUtils
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

fun formatIsoTimeAsLocalShort(isoTime: String): String {
    if (isoTime.isBlank() || isoTime.startsWith("0001-")) return ""
    return try {
        Instant.parse(isoTime).toLocalDateTime(TimeZone.currentSystemDefault()).formatShort()
    } catch (_: Exception) {
        isoTime
    }
}

fun formatIsoTimeRelative(isoTime: String): String {
    if (isoTime.isBlank() || isoTime.startsWith("0001-")) return ""
    return try {
        DateUtils.getRelativeTimeSpanString(
            Instant.parse(isoTime).toEpochMilliseconds(),
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
    } catch (_: Exception) {
        ""
    }
}

fun formatEpochMillisAsLocal(timestamp: Long): String {
    return Instant.fromEpochMilliseconds(timestamp)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .formatLong()
}

private fun LocalDateTime.formatShort(): String = buildString {
    append(month.number.toString().padStart(2, '0'))
    append('-')
    append(day.toString().padStart(2, '0'))
    append(' ')
    append(hour.toString().padStart(2, '0'))
    append(':')
    append(minute.toString().padStart(2, '0'))
}

private fun LocalDateTime.formatLong(): String = buildString {
    append(year.toString().padStart(4, '0'))
    append('-')
    append(month.number.toString().padStart(2, '0'))
    append('-')
    append(day.toString().padStart(2, '0'))
    append(' ')
    append(hour.toString().padStart(2, '0'))
    append(':')
    append(minute.toString().padStart(2, '0'))
}
