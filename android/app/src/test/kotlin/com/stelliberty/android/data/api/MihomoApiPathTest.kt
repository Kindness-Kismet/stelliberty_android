package com.stelliberty.android.data.api

import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.URLDecoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

class MihomoApiPathTest {
    @Test
    fun keepsNamesInSinglePathSegmentsAcrossProxyOperations() = runBlocking<Unit> {
        val group = "Example/组 ?#%2F+🔹"
        val node = "Node/节点 ?#%25+🔹"
        val provider = "Provider/源 ?#%2F+🔹"
        val expected = listOf(
            "PUT" to listOf("proxies", group),
            "DELETE" to listOf("proxies", group),
            "GET" to listOf("proxies", node, "delay"),
            "GET" to listOf("providers", "proxies", provider, node, "healthcheck"),
            "PUT" to listOf("providers", "proxies", provider),
            "PUT" to listOf("providers", "rules", provider),
        )
        ServerSocket(0, 6, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 5_000
            val peer = async(Dispatchers.IO) {
                expected.forEachIndexed { index, (method, segments) ->
                    server.accept().use { socket ->
                        socket.soTimeout = 5_000
                        val input = socket.getInputStream().buffered()
                        val headers = buildString {
                            while (!endsWith("\r\n\r\n")) {
                                val byte = input.read()
                                check(byte >= 0)
                                append(byte.toChar())
                            }
                        }.split("\r\n")
                        val requestLine = headers.first().split(' ')
                        assertEquals(method, requestLine[0])
                        val uri = URI(requestLine[1])
                        val actual = uri.rawPath.removePrefix("/").split('/').map {
                            URLDecoder.decode(it.replace("+", "%2B"), Charsets.UTF_8)
                        }
                        assertEquals(segments, actual)
                        if (method == "GET") assertTrue(uri.rawQuery.contains("timeout=5000"))
                        val length = headers.firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
                            ?.substringAfter(':')?.trim()?.toInt() ?: 0
                        val body = input.readNBytes(length).toString(Charsets.UTF_8)
                        if (index == 0) assertEquals(mapOf("name" to node), Json.decodeFromString<Map<String, String>>(body))
                        val response = "{\"delay\":42}"
                        socket.getOutputStream().apply {
                            write(
                                ("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                                    "Content-Length: ${response.length}\r\nConnection: close\r\n\r\n$response").toByteArray(),
                            )
                            flush()
                        }
                    }
                }
            }
            val api = MihomoApiClient("http://127.0.0.1:${server.localPort}")
            try {
                api.selectProxy(group, node)
                api.unfixProxy(group)
                assertEquals(42, api.getProxyDelay(node).delay)
                assertEquals(42, api.getProviderProxyDelay(provider, node).delay)
                api.updateProvider(provider)
                api.updateRuleProvider(provider)
                peer.await()
            } finally {
                api.close()
            }
        }
    }
}
