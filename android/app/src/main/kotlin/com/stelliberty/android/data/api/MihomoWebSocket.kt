package com.stelliberty.android.data.api

import com.stelliberty.android.domain.model.ConnectionsResponse
import com.stelliberty.android.domain.model.LogEvent
import com.stelliberty.android.domain.model.LogMessage
import com.stelliberty.android.domain.model.MemoryData
import com.stelliberty.android.domain.model.TrafficData
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json

class MihomoWebSocket(
    private val apiClient: MihomoApiClient,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val wsClient = HttpClient {
        install(WebSockets) {
            pingIntervalMillis = 20_000
        }
    }

    private val _connectionState = MutableStateFlow(false)

    // 整体连接状态按存活通道计数；单条日志通道的状态随 LogEvent 发布。
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

    private var liveConnections = 0

    @Synchronized
    private fun addLiveConnection(delta: Int) {
        liveConnections = (liveConnections + delta).coerceAtLeast(0)
        _connectionState.value = liveConnections > 0
    }

    fun trafficFlow(): Flow<TrafficData> = webSocketFlow(
        apiClient.getWebSocketUrl("/traffic"),
    ) { text -> json.decodeFromString<TrafficData>(text) }

    fun logsFlow(): Flow<LogEvent> = webSocketFlow(
        apiClient.getWebSocketUrl("/logs?level=info"),
        connectedEvent = LogEvent.Connected,
        disconnectedEvent = LogEvent.Disconnected,
    ) { text -> LogEvent.Message(json.decodeFromString<LogMessage>(text)) }

    fun memoryFlow(): Flow<MemoryData> = webSocketFlow(
        apiClient.getWebSocketUrl("/memory"),
    ) { text -> json.decodeFromString<MemoryData>(text) }

    fun connectionsFlow(): Flow<ConnectionsResponse> = webSocketFlow(
        apiClient.getWebSocketUrl("/connections"),
    ) { text -> json.decodeFromString<ConnectionsResponse>(text) }

    // 无限重连的数据流，除了被取消不会自己结束。调用 close() 也停不下来：连接被关掉后抛的异常同样
    // 被这里接住，只是进了重试等待。用的人必须显式取消收集协程，否则留下一条每 30 秒重试的僵尸流。
    private fun <T> webSocketFlow(
        url: String,
        connectedEvent: T? = null,
        disconnectedEvent: T? = null,
        parser: (String) -> T,
    ): Flow<T> = flow {
        var backoffMs = INITIAL_BACKOFF_MS
        while (currentCoroutineContext().isActive) {
            var counted = false
            try {
                wsClient.webSocket(url) {
                    counted = true
                    addLiveConnection(1)
                    backoffMs = INITIAL_BACKOFF_MS
                    connectedEvent?.let { emit(it) }
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        val parsed = runCatching { parser(frame.readText()) }.getOrNull() ?: continue
                        emit(parsed)
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
            } finally {
                if (counted) addLiveConnection(-1)
            }
            disconnectedEvent?.let { emit(it) }
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
    }.flowOn(Dispatchers.Default)

    fun close() {
        wsClient.close()
    }

    companion object {
        private const val INITIAL_BACKOFF_MS = 1000L
        private const val MAX_BACKOFF_MS = 30_000L
    }
}
