package com.stelliberty.android.util

import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

class FileLogStore(
    private val logFile: File,
    private val maxSizeBytes: Long = MAX_LOG_SIZE_BYTES,
) {
    private val lock = Any()

    fun append(line: String) = synchronized(lock) {
        logFile.parentFile?.mkdirs()
        logFile.appendText(line + "\n")
        trimToMaxSize()
    }

    fun read(): String = synchronized(lock) {
        if (!logFile.exists()) return@synchronized ""
        trimToMaxSize()
        logFile.readText()
    }

    fun clear() = synchronized(lock) {
        if (logFile.exists()) logFile.writeText("")
    }

    private fun trimToMaxSize() {
        if (logFile.length() <= maxSizeBytes) return
        logFile.writeText(readTailText(maxSizeBytes / 2))
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
        const val MAX_LOG_SIZE_BYTES = 1L * 1024L * 1024L
    }
}
