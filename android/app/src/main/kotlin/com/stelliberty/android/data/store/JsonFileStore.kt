package com.stelliberty.android.data.store

import com.stelliberty.android.platform.ProfileFileManager
import com.stelliberty.android.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

// 与 PC 的 System.Text.Json 输出对齐：全部键都写出、null 显式写出、两格缩进。
@OptIn(ExperimentalSerializationApi::class)
internal val StoreJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = true
    coerceInputValues = true
    prettyPrint = true
    prettyPrintIndent = "  "
}

// 内存值为准的单文件 JSON 存储，路径相对 files/mihomo/。进程内只能有一个实例，
// 各自新建会读到磁盘上的旧值，并互相覆盖对方的写入。
class JsonFileStore<T>(
    private val fileManager: ProfileFileManager,
    private val relativePath: String,
    private val serializer: KSerializer<T>,
    private val empty: T,
    private val scope: CoroutineScope,
) {
    private val writeLock = Mutex()
    private val _state = MutableStateFlow(empty)
    val state: StateFlow<T> = _state.asStateFlow()
    val value: T get() = _state.value

    // 本次加载遇到损坏文件并以空值启动；依赖「列表完整」的清理逻辑据此跳过。
    @Volatile
    var loadFailed: Boolean = false
        private set

    init {
        _state.value = read()
    }

    // 先写盘再发布：写盘失败时内存保持原值，异常交给调用方。
    suspend fun update(transform: (T) -> T): T = writeLock.withLock {
        withContext(Dispatchers.IO) {
            val current = _state.value
            val next = transform(current)
            if (next != current) {
                write(next)
                _state.value = next
            }
            next
        }
    }

    // 先发布后写盘，用于要立即可见的状态（当前订阅、节点选择）。
    // 同一实例只用一种写法：update 的读改写会覆盖这里已发布但未写盘的值。
    fun updateLater(transform: (T) -> T) {
        val previous = _state.value
        val next = transform(previous)
        if (next == previous) return
        _state.value = next
        scope.launch(Dispatchers.IO) {
            writeLock.withLock {
                // 排队期间已有更新的值，这份快照直接放弃。
                if (_state.value !== next) return@withLock
                runCatching { write(next) }
                    .onFailure { AppLogger.error(TAG, "failed to persist $relativePath", it) }
            }
        }
    }

    fun encode(value: T = _state.value): String = StoreJson.encodeToString(serializer, value)

    // 恢复备份换入文件后调用，丢弃内存中的旧值。
    suspend fun reload() = writeLock.withLock {
        withContext(Dispatchers.IO) {
            loadFailed = false
            _state.value = read()
        }
    }

    private fun read(): T {
        val text = fileManager.readMihomoFile(relativePath) ?: return empty
        if (text.isBlank()) return empty
        return runCatching { StoreJson.decodeFromString(serializer, text) }.getOrElse { e ->
            AppLogger.error(TAG, "failed to parse $relativePath, moving it to .bak", e)
            fileManager.backupMihomoFile(relativePath)
            loadFailed = true
            empty
        }
    }

    private fun write(value: T) = fileManager.writeMihomoFile(relativePath, encode(value))

    private companion object {
        const val TAG = "JsonFileStore"
    }
}
