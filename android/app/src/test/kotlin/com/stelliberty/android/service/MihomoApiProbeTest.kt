package com.stelliberty.android.service

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

class MihomoApiProbeTest {
    @Test
    fun requiresSuccessfulAuthenticatedResponseFromTheExpectedProcess() = runBlocking {
        val responses = listOf(
            Triple(200, """{"pid":1234}""", true),
            Triple(401, """{"pid":1234}""", false),
            Triple(503, """{"pid":1234}""", false),
            Triple(200, """{"pid":5678}""", false),
            Triple(200, """{"version":"Example"}""", false),
            Triple(200, "not json", false),
        )
        ServerSocket(0, 8, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 5_000
            val peer = async(Dispatchers.IO) {
                responses.forEach { (status, body) ->
                    server.accept().use { socket ->
                        val request = socket.readRequest()
                        assertTrue(request.startsWith("GET /stelliberty/runtime HTTP/1.1\r\n"))
                        assertTrue(request.contains("Authorization: Bearer example-secret\r\n"))
                        socket.respond(status, body)
                    }
                }
            }
            responses.forEach { (_, _, expected) ->
                assertEquals(expected, MihomoApiProbe.isReady(1234, "example-secret", "127.0.0.1:${server.localPort}"))
            }
            peer.await()
        }
    }

    @Test
    fun doesNotFollowRedirectsToAnotherController() = runBlocking {
        ServerSocket(0, 2, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 5_000
            val peer = async(Dispatchers.IO) {
                server.accept().use { socket ->
                    socket.readRequest()
                    socket.respond(302, "", "Location: http://127.0.0.1:${server.localPort}/other\r\n")
                }
                server.soTimeout = 500
                try {
                    server.accept().use { socket ->
                        socket.readRequest()
                        socket.respond(200, """{"pid":1234}""")
                    }
                    true
                } catch (_: SocketTimeoutException) {
                    false
                }
            }
            assertFalse(MihomoApiProbe.isReady(1234, "example-secret", "127.0.0.1:${server.localPort}"))
            assertFalse(peer.await())
        }
    }

    private fun Socket.readRequest(): String {
        soTimeout = 5_000
        val input = getInputStream().buffered()
        return buildString {
            while (!endsWith("\r\n\r\n")) {
                val byte = input.read()
                check(byte >= 0)
                append(byte.toChar())
            }
        }
    }

    private fun Socket.respond(status: Int, body: String, headers: String = "") {
        getOutputStream().apply {
            write(
                ("HTTP/1.1 $status Response\r\nContent-Type: application/json\r\n" +
                    "Content-Length: ${body.length}\r\nConnection: close\r\n$headers\r\n$body").toByteArray(),
            )
            flush()
        }
    }
}
