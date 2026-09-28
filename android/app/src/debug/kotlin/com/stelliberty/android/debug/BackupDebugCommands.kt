package com.stelliberty.android.debug

import android.content.Context
import android.os.Bundle
import com.stelliberty.android.data.backup.BackupManager
import com.stelliberty.android.data.backup.RestoreMode
import com.stelliberty.android.platform.ProxyServiceBridge
import com.stelliberty.android.platform.ProxyState
import java.io.File
import kotlinx.coroutines.runBlocking

// 备份文件放在应用外部文件目录的 backups/ 下：无需存储权限，adb 可直接推拉。
internal fun runBackupCommand(context: Context, action: String, arg: String?, extras: Bundle?): Bundle {
    val command = "backup.$action"
    val dir = File(context.getExternalFilesDir(null), "backups").apply { mkdirs() }
    val manager = koin<BackupManager>()
    return when (action) {
        "export" -> {
            val file = File(dir, File(extras.target(arg) ?: BackupManager.newBackupFileName()).name)
            runBlocking { manager.writeBackupTo(file) }
            debugResult(true, "exported ${file.length()} bytes", command, file.path)
        }

        "restore" -> {
            val state = ProxyServiceBridge.state.value.state
            if (state != ProxyState.Stopped && state != ProxyState.Error) {
                return debugResult(false, "Stop the proxy first", command)
            }
            val name = extras.target(arg) ?: return debugResult(false, "Missing file name", command)
            val mode = when (extras.string(EXTRA_MODE) ?: "overwrite") {
                "overwrite" -> RestoreMode.Overwrite
                "merge" -> RestoreMode.Merge
                else -> return debugResult(false, "Unknown mode, use merge or overwrite", command, name)
            }
            val file = File(dir, File(name).name)
            if (!file.isFile) return debugResult(false, "Not found: ${file.path}", command, name)
            runBlocking { manager.restoreBackupFrom(file, mode) }
            debugResult(true, "restored ($mode); restart the app before checking the UI", command, file.path)
        }

        else -> debugResult(false, "Unknown backup action: $action", command, arg)
    }
}
