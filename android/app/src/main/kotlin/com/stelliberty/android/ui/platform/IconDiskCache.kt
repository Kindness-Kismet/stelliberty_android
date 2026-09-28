package com.stelliberty.android.ui.platform

import com.stelliberty.android.platform.PlatformContext
import java.io.File

object IconDiskCache {
    private var cacheDir: File? = null

    fun init(context: PlatformContext) {
        cacheDir = File(context.cacheDir, "icons").apply { mkdirs() }
    }

    fun get(url: String): ByteArray? {
        val file = cacheFile(url) ?: return null
        return if (file.exists()) file.readBytes() else null
    }

    fun put(url: String, bytes: ByteArray) {
        cacheFile(url)?.writeBytes(bytes)
    }

    fun clear() {
        cacheDir?.listFiles()?.forEach { it.delete() }
    }

    private fun cacheFile(url: String): File? {
        val dir = cacheDir ?: return null
        return File(dir, url.hashCode().toUInt().toString(16))
    }
}
