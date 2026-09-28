package com.stelliberty.android.data.api

import com.stelliberty.android.domain.model.LogEvent
import com.stelliberty.android.domain.model.LogMessage
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class MihomoLogStreamTest {
    @Test
    fun emitsLogConnectionEventsAndReconnectsAfterServerClose() = runBlocking<Unit> {
        ServerSocket(0, 2, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 5_000
            val peer = async(Dispatchers.IO) {
                repeat(2) { index ->
                    server.accept().use { socket ->
                        val headers = socket.readHeaders()
                        assertTrue(headers.first().startsWith("GET /logs?level=info "))
                        socket.sendLogAndClose(headers, "session $index")
                    }
                }
            }
            val api = MihomoApiClient("http://127.0.0.1:${server.localPort}")
            val webSocket = MihomoWebSocket(api)
            try {
                val events = withTimeout(10_000) { webSocket.logsFlow().take(5).toList() }
                assertEquals(
                    listOf(
                        LogEvent.Connected,
                        LogEvent.Message(LogMessage("info", "session 0")),
                        LogEvent.Disconnected,
                        LogEvent.Connected,
                        LogEvent.Message(LogMessage("info", "session 1")),
                    ),
                    events,
                )
                peer.await()
            } finally {
                webSocket.close()
                api.close()
            }
        }
    }

    @Test
    fun reportsDisconnectedWhenHandshakeFails() = runBlocking<Unit> {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 5_000
            val peer = async(Dispatchers.IO) {
                server.accept().use { socket ->
                    socket.readHeaders()
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 401 Unauthorized\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                        flush()
                    }
                }
            }
            val api = MihomoApiClient("http://127.0.0.1:${server.localPort}")
            val webSocket = MihomoWebSocket(api)
            try {
                assertEquals(LogEvent.Disconnected, withTimeout(5_000) { webSocket.logsFlow().first() })
                peer.await()
            } finally {
                webSocket.close()
                api.close()
            }
        }
    }

    private fun Socket.readHeaders(): List<String> {
        soTimeout = 5_000
        val reader = getInputStream().bufferedReader(Charsets.US_ASCII)
        return generateSequence { reader.readLine()?.takeIf(String::isNotEmpty) }.toList()
    }

    private fun Socket.sendLogAndClose(headers: List<String>, message: String) {
        val key = headers.first { it.startsWith("Sec-WebSocket-Key:", ignoreCase = true) }
            .substringAfter(':').trim()
        val accept = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-1")
                .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray(Charsets.US_ASCII)),
        )
        val payload = "{\"type\":\"info\",\"payload\":\"$message\"}".toByteArray()
        getOutputStream().apply {
            write(
                ("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                    "Sec-WebSocket-Accept: $accept\r\n\r\n").toByteArray(Charsets.US_ASCII),
            )
            write(byteArrayOf(0x81.toByte(), payload.size.toByte()))
            write(payload)
            write(byteArrayOf(0x88.toByte(), 2, 0x03, 0xE8.toByte()))
            flush()
        }
    }
}
