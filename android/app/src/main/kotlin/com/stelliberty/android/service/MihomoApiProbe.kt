package com.stelliberty.android.service

import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal object MihomoApiProbe {
    @Serializable
    private data class RuntimeIdentity(val pid: Int)

    fun isReady(pid: Int, secret: String, externalController: String, timeoutMs: Int = 500): Boolean {
        val connection = try {
            URL("http://$externalController/stelliberty/runtime")
                .openConnection(Proxy.NO_PROXY) as HttpURLConnection
        } catch (_: Exception) {
            return false
        }
        return try {
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", "Bearer $secret")
            connection.responseCode == HttpURLConnection.HTTP_OK &&
                connection.inputStream.bufferedReader().use {
                    Json.decodeFromString<RuntimeIdentity>(it.readText()).pid == pid
                }
        } catch (_: Exception) {
            false
        } finally {
            connection.disconnect()
        }
    }
}
