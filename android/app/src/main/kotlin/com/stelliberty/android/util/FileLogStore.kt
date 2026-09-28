package com.stelliberty.android.util

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileLogStore(
    context: Context,
    private val maxSizeBytes: Long = MAX_LOG_SIZE_BYTES,
) {
    private val logFile = File(context.filesDir, LOG_FILE_NAME)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val lock = Any()

    fun append(level: String, tag: String, message: String, throwable: String? = null) = synchronized(lock) {
        val line = buildString {
            append(dateFormat.format(Date()))
            append(' ')
            append(level)
            append('/')
            append(tag)
            append(": ")
            append(message)
            throwable?.let {
                appendLine()
                append(it)
            }
            appendLine()
        }
        logFile.parentFile?.mkdirs()
        logFile.appendText(line)
        trimToMaxSize()
    }

    fun read(): String = synchronized(lock) {
        if (!logFile.exists()) return@synchronized ""
        trimToMaxSize()
        logFile.readText()
    }

    fun clear(): Boolean = synchronized(lock) {
        runCatching { if (logFile.exists()) logFile.writeText("") }.isSuccess
    }

    private fun trimToMaxSize() {
        if (logFile.length() <= maxSizeBytes) return
        logFile.writeText(readTailText(maxSizeBytes))
    }

    // 从尾部回读固定字节窗口，不把整个文件读进堆。窗口起点会切在半行中间，故丢掉首行残段。
    private fun readTailText(maxBytes: Long): String {
        if (maxBytes <= 0L) return ""
        val length = logFile.length()
        val start = (length - maxBytes).coerceAtLeast(0L)
        val size = (length - start).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val bytes = ByteArray(size)
        RandomAccessFile(logFile, "r").use { input ->
            input.seek(start)
            input.readFully(bytes)
        }
        val tail = bytes.toString(StandardCharsets.UTF_8)
        val firstBreak = tail.indexOf('\n')
        return if (start > 0L && firstBreak >= 0) tail.drop(firstBreak + 1) else tail
    }

    companion object {
        private const val LOG_FILE_NAME = "stelliberty.log"
        const val MAX_LOG_SIZE_BYTES = 1L * 1024L * 1024L
    }
}
