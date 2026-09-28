package com.stelliberty.android.service

import android.content.Context
import com.stelliberty.android.domain.model.ConfigurationOverride
import com.stelliberty.android.domain.model.resolveSecretOrNull
import com.stelliberty.android.service.RuntimeOverrideBuilder.DEFAULT_MIXED_PORT
import java.io.File
import java.util.UUID

object ConfigGenerator {

    fun generateSecret(): String = UUID.randomUUID().toString().take(16)

    // 接口访问密码的取值顺序：用户自己设的 > 订阅文件里写的 > 随机生成。
    // 两个服务都从这里取，不要各写一份。
    fun resolveSecret(
        context: Context,
        userOverride: ConfigurationOverride,
        subscriptionId: String?,
    ): String = userOverride.resolveSecretOrNull()
        ?: subscriptionId?.let { readSubscriptionSecret(context, it) }
        ?: generateSecret()

    fun getWorkDir(context: Context): File {
        val dir = File(context.filesDir, "mihomo")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    // 一条订阅都没有时的最小配置：mihomo 必须拿到能解析的 config.yaml 才肯启动，而端口、TUN、
    // DNS、出站模式全由 override.run.json 决定，这里只给规则段。每次启动重写，无需保留状态。
    fun writeFallbackConfig(context: Context): File {
        val file = File(getWorkDir(context), "config.yaml")
        ProfileFileOps.writeAtomically(file, FALLBACK_CONFIG)
        return file
    }

    // proxies 留空，出站只有内核自带的 DIRECT / REJECT。写成 MATCH,DIRECT 而不是留空规则段，
    // 是为了让没有配置时的行为是「照常上网、不走代理」，而不是全部拒绝。
    private val FALLBACK_CONFIG = """
        mixed-port: $DEFAULT_MIXED_PORT
        mode: rule
        log-level: info
        proxies: []
        proxy-groups: []
        rules:
          - MATCH,DIRECT
    """.trimIndent() + "\n"

    fun readSubscriptionSecret(context: Context, subscriptionId: String): String? {
        val file = ProfileFileOps.getSubscriptionConfigFile(context, subscriptionId)
        if (!file.exists()) return null
        val regex = Regex("""^secret:\s*(.+?)\s*(?:#.*)?$""")
        return try {
            file.useLines { lines ->
                for (line in lines) {
                    val raw = regex.matchEntire(line)?.groupValues?.get(1) ?: continue
                    val unquoted = when {
                        raw.length >= 2 && raw.startsWith('"') && raw.endsWith('"') -> raw.substring(1, raw.length - 1)
                        raw.length >= 2 && raw.startsWith('\'') && raw.endsWith('\'') -> raw.substring(1, raw.length - 1)
                        else -> raw
                    }
                    return@useLines unquoted.takeIf { it.isNotEmpty() }
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun readSubscriptionMixedPort(context: Context, subscriptionId: String): Int? {
        val file = ProfileFileOps.getSubscriptionConfigFile(context, subscriptionId)
        if (!file.exists()) return null
        val regex = Regex("""^mixed-port:\s*(\d+)\s*(?:#.*)?$""")
        return try {
            file.useLines { lines ->
                for (line in lines) {
                    val raw = regex.matchEntire(line)?.groupValues?.get(1) ?: continue
                    return@useLines raw.toIntOrNull()?.takeIf { it in 1..65535 }
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
