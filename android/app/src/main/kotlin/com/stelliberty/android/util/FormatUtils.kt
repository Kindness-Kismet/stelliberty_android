package com.stelliberty.android.util

import java.util.Locale

object FormatUtils {

    fun formatSpeed(bytesPerSecond: Long): String {
        return "${formatBytes(bytesPerSecond)}/s"
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unitIndex = 0
        while (value >= 1024 && unitIndex < units.lastIndex) {
            value /= 1024
            unitIndex++
        }
        return if (value == value.toLong().toDouble()) {
            "${value.toLong()} ${units[unitIndex]}"
        } else {
            String.format(Locale.US, "%.1f ${units[unitIndex]}", value)
        }
    }

    // 只管有效延迟。超时和未测试是两种不同状态，文案需要本地化，交给 UI 层区分。
    fun formatLatency(delay: Int): String = "$delay ms"
}
