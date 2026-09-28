package com.stelliberty.android.debug

import android.os.Bundle
import com.stelliberty.android.platform.PlatformStorage
import com.stelliberty.android.platform.ProxyServiceBridge
import com.stelliberty.android.platform.ProxyServiceController
import com.stelliberty.android.platform.StorageKeys
import com.stelliberty.android.platform.TunMode

private val SET_TYPED_KEYS = setOf(
    StorageKeys.WIFI_POLICY_SSIDS,
    StorageKeys.APP_PROXY_PACKAGES,
)

internal fun runSettingsCommand(action: String, arg: String?, extras: Bundle?): Bundle {
    val command = "settings.$action"
    val storage = koin<PlatformStorage>()
    return when (action) {
        "get" -> {
            val key = extras.target(arg) ?: return debugResult(false, "Missing key", command)
            // 集合型键必须按集合读：getString 撞上类型不符会退回默认值（那是防崩溃循环的正确行为），
            // 这里若跟着走 getString，写进去的名单会读成空串，看着像 set 没生效。
            val value = if (key in SET_TYPED_KEYS) {
                storage.getStringSet(key, emptySet()).joinToString(",")
            } else {
                storage.getString(key, "")
            }
            debugResult(true, "read $key", command, key, "$key=$value")
        }

        "set" -> {
            val key = extras.string(EXTRA_NAME) ?: extras.target(arg)
                ?: return debugResult(false, "Missing key", command)
            val value = extras.string(EXTRA_VALUE)
                ?: extras.boolean(EXTRA_ENABLED)?.toString()
                ?: return debugResult(false, "Missing value or enabled", command, key)
            // 这几个键按集合存取，写成字符串会让读取方拿到类型不符的值。逗号分隔即可。
            if (key in SET_TYPED_KEYS) {
                val items = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                storage.putStringSet(key, items)
                debugResult(true, "$key=$items", command, key, "$key=${items.joinToString(",")}")
            } else {
                storage.putString(key, value)
                debugResult(true, "$key=$value", command, key, "$key=$value")
            }
        }

        "dump" -> {
            val filter = extras.target(arg)
            val entries = storage.dumpAll()
                .filterKeys { filter == null || it.contains(filter, ignoreCase = true) }
                .toSortedMap()
            val data = entries.entries.joinToString("; ") { "${it.key}=${it.value}" }
            debugResult(true, "${entries.size} keys", command, filter, data)
        }

        "set_tun_mode" -> {
            val raw = extras.target(arg) ?: return debugResult(false, "Missing mode", command)
            val normalized = raw.replace("-", "_").lowercase()
            val mode = TunMode.entries.firstOrNull {
                it.storageValue == normalized || it.name.equals(normalized.replace("_", ""), ignoreCase = true)
            } ?: return debugResult(
                false,
                "Invalid mode: $raw (want ${TunMode.entries.joinToString("/") { it.storageValue }})",
                command,
                raw,
            )
            storage.putString(StorageKeys.TUN_MODE, mode.storageValue)
            // 和设置页那条路径保持一致：不同步桥，首页会一直显示旧模式。运行中它自己会忽略。
            ProxyServiceBridge.setSelectedTunMode(mode)
            val running = koin<ProxyServiceController>().status.value.state.name
            debugResult(true, "tunMode=${mode.storageValue} (proxy state=$running)", command, mode.storageValue)
        }

        else -> debugResult(false, "Unknown settings action: $action", command, arg)
    }
}
