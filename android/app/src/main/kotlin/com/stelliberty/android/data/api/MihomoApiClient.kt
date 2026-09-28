package com.stelliberty.android.data.api

import com.stelliberty.android.domain.model.ConnectionsResponse
import com.stelliberty.android.domain.model.DelayResult
import com.stelliberty.android.domain.model.DnsQueryResponse
import com.stelliberty.android.domain.model.GroupsResponse
import com.stelliberty.android.domain.model.MihomoConfig
import com.stelliberty.android.domain.model.MihomoVersion
import com.stelliberty.android.domain.model.ProvidersResponse
import com.stelliberty.android.domain.model.ProxiesResponse
import com.stelliberty.android.domain.model.RuleProvidersResponse
import com.stelliberty.android.domain.model.RulesResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class MihomoApiClient(
    private val baseUrl: String = "http://127.0.0.1:9090",
    private val secret: String = "",
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
        install(WebSockets)
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            requestTimeoutMillis = 60_000
            socketTimeoutMillis = 60_000
        }
        defaultRequest {
            if (secret.isNotEmpty()) {
                header("Authorization", "Bearer $secret")
            }
        }
    }

    suspend fun getVersion(): MihomoVersion {
        val response = client.get("$baseUrl/version")
        ensureSuccess(response, "version")
        return response.body()
    }

    suspend fun getConfig(): MihomoConfig {
        val response = client.get("$baseUrl/configs")
        ensureSuccess(response, "configs")
        return response.body()
    }

    suspend fun getProxies(): ProxiesResponse {
        val response = client.get("$baseUrl/proxies")
        ensureSuccess(response, "proxies")
        return response.body()
    }

    suspend fun getGroups(): GroupsResponse {
        val response = client.get("$baseUrl/group")
        ensureSuccess(response, "groups")
        return response.body()
    }

    suspend fun selectProxy(group: String, name: String) {
        val response: HttpResponse = client.put(endpoint("proxies", group)) {
            contentType(ContentType.Application.Json)
            setBody(mapOf("name" to name))
        }
        ensureSuccess(response, "proxy group '$group'")
    }

    suspend fun unfixProxy(group: String) {
        val response: HttpResponse = client.delete(endpoint("proxies", group))
        ensureSuccess(response, "proxy group '$group'")
    }

    // 测速失败时内核回的是 404 / 503 / 504 加一段 message，这里必须按状态码判成失败：
    // 那种响应体解析成 DelayResult 会得到 delay=0，跟「真的 0 毫秒」再也分不开。
    suspend fun getProxyDelay(name: String, testUrl: String = "http://www.gstatic.com/generate_204", timeout: Int = 5000): DelayResult {
        val response = client.get(endpoint("proxies", name, "delay")) {
            url {
                parameters.append("url", testUrl)
                parameters.append("timeout", timeout.toString())
            }
        }
        ensureSuccess(response, "delay test '$name'")
        return response.body()
    }

    // 订阅里通过 provider 拉来的节点不在普通节点的命名空间里，用普通测速接口会返回找不到，
    // 必须走 provider 专用的健康检查地址。
    suspend fun getProviderProxyDelay(
        provider: String,
        name: String,
        testUrl: String = "http://www.gstatic.com/generate_204",
        timeout: Int = 5000,
    ): DelayResult {
        val response = client.get(endpoint("providers", "proxies", provider, name, "healthcheck")) {
            url {
                parameters.append("url", testUrl)
                parameters.append("timeout", timeout.toString())
            }
        }
        ensureSuccess(response, "delay test '$name'")
        return response.body()
    }

    suspend fun getRules(): RulesResponse {
        val response = client.get("$baseUrl/rules")
        ensureSuccess(response, "rules")
        return response.body()
    }

    suspend fun getConnections(): ConnectionsResponse {
        val response = client.get("$baseUrl/connections")
        ensureSuccess(response, "connections")
        return response.body()
    }

    suspend fun closeAllConnections() {
        client.delete("$baseUrl/connections")
    }

    suspend fun closeConnection(id: String) {
        client.delete(endpoint("connections", id))
    }

    suspend fun getProviders(): ProvidersResponse {
        val response = client.get("$baseUrl/providers/proxies")
        ensureSuccess(response, "proxy providers")
        return response.body()
    }

    suspend fun updateProvider(name: String) {
        val response: HttpResponse = client.put(endpoint("providers", "proxies", name))
        ensureSuccess(response, "proxy provider '$name'")
    }

    suspend fun getRuleProviders(): RuleProvidersResponse {
        val response = client.get("$baseUrl/providers/rules")
        ensureSuccess(response, "rule providers")
        return response.body()
    }

    suspend fun updateRuleProvider(name: String) {
        val response: HttpResponse = client.put(endpoint("providers", "rules", name))
        ensureSuccess(response, "rule provider '$name'")
    }

    suspend fun queryDns(name: String, type: String = "A"): DnsQueryResponse {
        val response = client.get("$baseUrl/dns/query") {
            url {
                parameters.append("name", name)
                parameters.append("type", type)
            }
        }
        ensureSuccess(response, "dns query '$name'")
        return response.body()
    }

    suspend fun flushFakeIp() {
        client.post("$baseUrl/cache/fakeip/flush")
    }

    suspend fun flushDnsCache() {
        client.post("$baseUrl/cache/dns/flush")
    }

    fun close() {
        client.close()
    }

    // 名称属于单个路径段，斜线、百分号与查询符号都必须编码后再发送。
    private fun endpoint(vararg segments: String): Url = URLBuilder(baseUrl).apply {
        appendPathSegments(*segments, encodeSlash = true)
    }.build()

    private suspend fun ensureSuccess(response: HttpResponse, context: String) {
        if (response.status.isSuccess()) return
        val detail = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
        val summary = extractErrorMessage(detail) ?: response.status.description
        throw MihomoApiException("$context: ${response.status.value} $summary")
    }

    private fun extractErrorMessage(body: String): String? {
        if (body.isBlank()) return null
        return runCatching {
            val element = json.parseToJsonElement(body)
            element.let { it as? JsonObject }
                ?.get("message")
                ?.let { it as? JsonPrimitive }
                ?.content
        }.getOrNull()
    }

    fun getWebSocketUrl(path: String): String {
        val wsBase = baseUrl.replace("http://", "ws://").replace("https://", "wss://")
        return if (secret.isNotEmpty()) {
            val separator = if ("?" in path) "&" else "?"
            "$wsBase$path${separator}token=$secret"
        } else {
            "$wsBase$path"
        }
    }
}

class MihomoApiException(message: String) : Exception(message)
