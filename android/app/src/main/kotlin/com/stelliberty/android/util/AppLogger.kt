package com.stelliberty.android.util

import android.content.Context
import android.util.Log
import android.os.Build
import com.stelliberty.android.BuildConfig
import com.stelliberty.android.domain.model.LogLevel
import com.stelliberty.android.domain.model.LogSource

object AppLogger {
    val logs = DiagnosticLogStore { error ->
        Log.e("AppLogger", "Failed to persist diagnostic logs", error)
    }

    fun initialize(context: Context) {
        logs.initialize(context.applicationContext.filesDir)
        info("Application", "Starting ${environment()}")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            error("Application", "Uncaught exception on ${thread.name}", error)
            previous?.uncaughtException(thread, error)
        }
    }

    fun environment(): String =
        "Stelliberty ${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE}); " +
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}); " +
            "${Build.MANUFACTURER} ${Build.MODEL}; ABI=${Build.SUPPORTED_ABIS.joinToString()}"

    fun debug(tag: String, message: String) {
        emit(LogLevel.Debug, tag, message)
    }

    fun info(tag: String, message: String) {
        emit(LogLevel.Info, tag, message)
    }

    fun warn(tag: String, message: String, throwable: Throwable? = null) {
        emit(LogLevel.Warning, tag, message, throwable)
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        emit(LogLevel.Error, tag, message, throwable)
    }

    private fun emit(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        val trace = throwable?.stackTraceToString()
        val entry = logs.append(LogSource.Application, level, tag, if (trace == null) message else "$message\n$trace")
        val priority = when (level) {
            LogLevel.Debug -> Log.DEBUG
            LogLevel.Info -> Log.INFO
            LogLevel.Warning -> Log.WARN
            LogLevel.Error -> Log.ERROR
        }
        runCatching { Log.println(priority, tag, LogFormatter.format(entry)) }
    }
}
