package com.stelliberty.android.service

import com.stelliberty.android.util.AppLogger
import java.io.File
import java.io.RandomAccessFile

private const val TAG = "LogTail"

private const val DEFAULT_TAIL_BYTES = 256L * 1024

// 必须从文件末尾倒着读。调试级别的日志跑久了能有几十兆，为了拿最后几行就把整个文件读进内存，
// 在前台服务里会直接内存溢出。
internal fun File.readLastLines(maxLines: Int, maxBytes: Long = DEFAULT_TAIL_BYTES): String {
    if (!isFile) return ""
    return try {
        RandomAccessFile(this, "r").use { raf ->
            val length = raf.length()
            val from = (length - maxBytes).coerceAtLeast(0)
            raf.seek(from)
            val buffer = ByteArray((length - from).toInt())
            raf.readFully(buffer)
            val lines = String(buffer, Charsets.UTF_8).lineSequence()
            val usable = if (from > 0) lines.drop(1) else lines
            usable.toList().takeLast(maxLines).joinToString("\n").trim()
        }
    } catch (e: Exception) {
        AppLogger.warn(TAG, "failed to tail $name", e)
        ""
    }
}
