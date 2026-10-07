package com.stelliberty.android.util

// 订阅与覆写的凭据常放在路径、查询串或 userinfo 里：http(s) / ws(s) 与 DNS 上游协议只留协议与主机，
// 其他协议（节点分享链接、clash:// 深链）整体遮掉。
object LogRedactor {
    private val url = Regex("""\b([A-Za-z][A-Za-z0-9+.\-]*)://([^\s"'<>]*)""")
    private val hostSchemes = setOf("http", "https", "ws", "wss", "tcp", "udp", "tls", "quic")

    fun redact(text: String): String {
        if (!text.contains("://")) return text
        return url.replace(text) { match ->
            val scheme = match.groupValues[1]
            val rest = match.groupValues[2]
            if (scheme.lowercase() !in hostSchemes) return@replace "$scheme://***"
            val authority = rest.takeWhile { it != '/' && it != '?' && it != '#' }
            val host = authority.substringAfterLast('@')
            val tail = rest.substring(authority.length)
            buildString {
                append(scheme).append("://")
                if (host.length != authority.length) append("***@")
                append(host)
                if (tail.isNotEmpty()) append(if (tail == "/") "/" else "/***")
            }
        }
    }
}
