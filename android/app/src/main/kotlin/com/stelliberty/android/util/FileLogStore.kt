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

    fun readTail(maxBytes: Long): String = synchronized(lock) {
        if (!logFile.exists()) return@synchronized ""
        readTailBytes(maxBytes).toString(StandardCharsets.UTF_8)
    }

    fun readBytes(): ByteArray = synchronized(lock) {
        if (logFile.exists()) logFile.readBytes() else ByteArray(0)
    }

    fun clear() = synchronized(lock) {
        if (logFile.exists()) logFile.writeText("")
    }

    private fun trimToMaxSize() {
        if (logFile.length() <= maxSizeBytes) return
        logFile.writeBytes(readTailBytes(maxSizeBytes / 2))
    }

    // 从尾部回读固定字节窗口，不把整个文件读进堆。窗口起点会切在半行中间，故丢掉首行残段；
    // UTF-8 多字节序列里不会出现 0x0A，按字节找换行是安全的。
    private fun readTailBytes(maxBytes: Long): ByteArray {
        if (maxBytes <= 0L) return ByteArray(0)
        val length = logFile.length()
        val start = (length - maxBytes).coerceAtLeast(0L)
        val bytes = ByteArray((length - start).toInt())
        RandomAccessFile(logFile, "r").use { input ->
            input.seek(start)
            input.readFully(bytes)
        }
        if (start == 0L) return bytes
        val firstBreak = bytes.indexOf('\n'.code.toByte())
        return if (firstBreak >= 0) bytes.copyOfRange(firstBreak + 1, bytes.size) else bytes
    }

    companion object {
        const val MAX_LOG_SIZE_BYTES = 5L * 1024L * 1024L
    }
}
