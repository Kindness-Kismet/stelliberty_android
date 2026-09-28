package com.stelliberty.android.data.api

import com.stelliberty.android.data.repository.MihomoRepositoryImpl
import com.stelliberty.android.domain.repository.MihomoRepository
import com.stelliberty.android.platform.ProxyServiceBridge
import com.stelliberty.android.platform.ProxyServiceStatus
import com.stelliberty.android.platform.ProxyState
import com.stelliberty.android.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MihomoConnectionManager(scope: CoroutineScope) {

    private val _repository = MutableStateFlow<MihomoRepository?>(null)
    val repository: StateFlow<MihomoRepository?> = _repository.asStateFlow()

    private val mutex = Mutex()
    private var current: MihomoRepository? = null

    init {
        scope.launch {
            ProxyServiceBridge.state.collect { status ->
                runCatching { applyStatus(status) }
                    .onFailure { AppLogger.error(TAG, "failed to apply proxy status ${status.state}", it) }
            }
        }
    }

    private suspend fun applyStatus(status: ProxyServiceStatus) {
        val next: MihomoRepository? = when (status.state) {
            ProxyState.Running -> buildRepository(status)
            else -> null
        }
        replace(next)
    }

    // 先拿到锁再换字段和数据流，最后才关掉旧的：先发布新的能保证下游立刻看到，
    // 不做端点比对是有意的——重连时多重建一次不到 50 毫秒，比状态机比对出竞态划算。
    private suspend fun replace(next: MihomoRepository?) {
        mutex.withLock {
            val old = current
            current = next
            _repository.value = next
            old?.close()
        }
    }

    private fun buildRepository(status: ProxyServiceStatus): MihomoRepository {
        val client = MihomoApiClient(
            baseUrl = "http://${status.externalController}",
            secret = status.secret,
        )
        val ws = MihomoWebSocket(client)
        return MihomoRepositoryImpl(client, ws)
    }

    private companion object {
        const val TAG = "MihomoConnectionManager"
    }
}
