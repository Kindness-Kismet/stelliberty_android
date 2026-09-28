package com.stelliberty.android.data.repository

import com.stelliberty.android.domain.model.ConfigurationOverride
import com.stelliberty.android.platform.ProfileFileManager
import com.stelliberty.android.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

class OverrideJsonStore(
    private val fileManager: ProfileFileManager,
    private val scope: CoroutineScope,
) {

    private val json = Json {
        encodeDefaults = false
        explicitNulls = false
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val _state = MutableStateFlow(readFromDisk())
    val state: StateFlow<ConfigurationOverride> = _state.asStateFlow()

    private val writeLock = Mutex()

    // 内存里的值才是准的，所以直接返回不去读文件。这个类是全进程单例，服务和后台任务都注入同一个实例；
    // 谁要是自己新建一个，读到的就是磁盘上的旧值，用户改完设置立刻启动就会用到改之前的配置。
    fun load(): ConfigurationOverride = _state.value

    fun save(override: ConfigurationOverride) {
        _state.value = override
        persist(override)
    }

    fun update(transform: (ConfigurationOverride) -> ConfigurationOverride) {
        persist(_state.updateAndGet(transform))
    }

    fun encode(override: ConfigurationOverride): String = json.encodeToString(override)

    // 备份恢复换入文件后调用；排队中的旧写入会因内存值已变而放弃。
    fun reload() {
        _state.value = readFromDisk()
    }

    // 排队写入期间如果已经有更新的内容，这次直接放弃，否则旧内容会盖掉新的。
    private fun persist(override: ConfigurationOverride) {
        scope.launch(Dispatchers.IO) {
            writeLock.withLock {
                if (_state.value !== override) return@withLock
                runCatching { fileManager.writeMihomoFile(FILE_NAME, json.encodeToString(override)) }
                    .onFailure { AppLogger.error(TAG, "failed to persist $FILE_NAME", it) }
            }
        }
    }

    private fun readFromDisk(): ConfigurationOverride {
        val text = fileManager.readMihomoFile(FILE_NAME) ?: return ConfigurationOverride()
        if (text.isBlank()) return ConfigurationOverride()
        return runCatching { json.decodeFromString<ConfigurationOverride>(text) }
            .getOrElse { e ->
                AppLogger.error(TAG, "failed to parse $FILE_NAME, backing up to $FILE_NAME.bak", e)
                fileManager.backupMihomoFile(FILE_NAME)
                ConfigurationOverride()
            }
    }

    companion object {
        const val FILE_NAME = "override.user.json"
        private const val TAG = "OverrideJsonStore"
    }
}
