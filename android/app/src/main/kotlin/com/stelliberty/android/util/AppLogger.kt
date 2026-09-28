package com.stelliberty.android.util

import android.content.Context
import android.util.Log

object AppLogger {
    private var fileLogStore: FileLogStore? = null

    fun initialize(context: Context) {
        fileLogStore = FileLogStore(context.applicationContext)
    }

    fun debug(tag: String, message: String) {
        runCatching { Log.d(tag, message) }
        writeToFile("D", tag, message)
    }

    fun info(tag: String, message: String) {
        runCatching { Log.i(tag, message) }
        writeToFile("I", tag, message)
    }

    fun warn(tag: String, message: String, throwable: Throwable? = null) {
        emitWithThrowable("W", tag, message, throwable) { t, m -> Log.w(t, m) }
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        emitWithThrowable("E", tag, message, throwable) { t, m -> Log.e(t, m) }
    }

    fun readLogs(): String = runCatching { fileLogStore?.read().orEmpty() }.getOrDefault("")

    fun clearLogs(): Boolean = runCatching { fileLogStore?.clear() == true }.getOrDefault(false)

    private inline fun emitWithThrowable(
        level: String,
        tag: String,
        message: String,
        throwable: Throwable?,
        logcat: (String, String) -> Unit,
    ) {
        val trace = throwable?.stackTraceToString()
        val logcatMessage = if (trace == null) message else "$message\n$trace"
        runCatching { logcat(tag, logcatMessage) }
        writeToFile(level, tag, message, trace)
    }

    private fun writeToFile(level: String, tag: String, message: String, throwable: String? = null) {
        runCatching { fileLogStore?.append(level, tag, message, throwable) }
    }
}
