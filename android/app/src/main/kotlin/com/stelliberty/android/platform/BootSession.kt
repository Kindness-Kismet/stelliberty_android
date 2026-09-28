package com.stelliberty.android.platform

import android.content.Context
import android.provider.Settings
import com.stelliberty.android.platform.BootSession.mark

object BootSession {

    fun mark(context: Context, storage: PlatformStorage) {
        storage.putString(StorageKeys.ROOT_BOOT_COUNT, bootCount(context)?.toString() ?: "")
    }

    fun clear(storage: PlatformStorage) {
        storage.putString(StorageKeys.ROOT_BOOT_COUNT, "")
    }

    // 宁可漏判不可误判：漏判只是多试一次重连，后面的三道检查会挡下过期的进程号；
    // 误判会清掉仍然有效的进程号，让还活着的 mihomo 变成没人管的孤儿，而界面显示未运行。
    fun hasRebootedSince(context: Context, storage: PlatformStorage): Boolean {
        val stored = storage.getString(StorageKeys.ROOT_BOOT_COUNT, "").toIntOrNull() ?: return false
        val current = bootCount(context) ?: return false
        return current != stored
    }

    private fun bootCount(context: Context): Int? =
        runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT) }
            .getOrNull()
            ?.takeIf { it > 0 }
}
