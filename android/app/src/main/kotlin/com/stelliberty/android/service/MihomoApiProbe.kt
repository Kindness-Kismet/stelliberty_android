package com.stelliberty.android.service

import java.io.IOException
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal object MihomoApiProbe {
    @Serializable
    private data class RuntimeIdentity(val pid: Int)

    enum class Status {
        Ready,
        Initializing,
        // 有 HTTP 响应却不是目标进程：控制端口被其他程序占着，目标进程已无法再绑定。
        Foreign,
        Unreachable,
    }

    fun probe(pid: Int, secret: String, externalController: String, timeoutMs: Int = 500): Status {
        val connection = try {
            URL("http://$externalController/stelliberty/runtime")
                .openConnection(Proxy.NO_PROXY) as HttpURLConnection
        } catch (_: Exception) {
            return Status.Unreachable
        }
        return try {
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", "Bearer $secret")
            val code = connection.responseCode
            val body = (if (code < 400) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }
            val ownProcess = runCatching { Json.decodeFromString<RuntimeIdentity>(body.orEmpty()).pid == pid }
                .getOrDefault(false)
            when {
                !ownProcess -> Status.Foreign
                code == HttpURLConnection.HTTP_OK -> Status.Ready
                code == HttpURLConnection.HTTP_UNAVAILABLE -> Status.Initializing
                else -> Status.Foreign
            }
        } catch (_: IOException) {
            Status.Unreachable
        } finally {
            connection.disconnect()
        }
    }
}
