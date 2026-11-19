package com.stelliberty.android.data.api

import com.stelliberty.android.data.repository.MihomoRepositoryImpl
import com.stelliberty.android.domain.repository.MihomoRepository
import com.stelliberty.android.platform.ProxyServiceBridge
import com.stelliberty.android.platform.ProxyServiceStatus
import com.stelliberty.android.platform.ProxyState
import com.stelliberty.android.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
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
    private var currentStatus: ProxyServiceStatus? = null
    private val coreLogs = CoreLogCollector(scope)

    init {
        scope.launch {
            ProxyServiceBridge.state.collect { status ->
                try {
                    applyStatus(status)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.error(TAG, "failed to apply proxy status ${status.state}", e)
                }
            }
        }
    }

    private suspend fun applyStatus(status: ProxyServiceStatus) {
        val identity = status.copy(configurationRevision = 0, isReloading = false)
        if (identity == currentStatus) return
        val next: MihomoRepository? = when (status.state) {
            ProxyState.Running -> buildRepository(status)
            else -> null
        }
        replace(next)
        currentStatus = identity
    }

    // 进程身份变化时替换客户端；同一内核重载配置保留长连接。
    private suspend fun replace(next: MihomoRepository?) {
        mutex.withLock {
            val old = current
            current = next
            coreLogs.setRepository(next)
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
