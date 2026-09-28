package com.stelliberty.android.util

fun formatUptime(seconds: Long): String {
    if (seconds < 0) return "00:00:00"
    if (seconds >= 100 * 3600) return "99:99:99"
    val hours = (seconds / 3600).toString().padStart(2, '0')
    val minutes = (seconds / 60 % 60).toString().padStart(2, '0')
    val remainingSeconds = (seconds % 60).toString().padStart(2, '0')
    return "$hours:$minutes:$remainingSeconds"
}
