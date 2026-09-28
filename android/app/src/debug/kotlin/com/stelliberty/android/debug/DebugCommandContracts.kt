package com.stelliberty.android.debug

import android.os.Bundle
import org.koin.core.context.GlobalContext

internal const val EXTRA_VALUE = "value"
internal const val EXTRA_ENABLED = "enabled"
internal const val EXTRA_NAME = "name"
internal const val EXTRA_URL = "url"
internal const val EXTRA_UUID = "uuid"
internal const val EXTRA_GROUP = "group"
internal const val EXTRA_NODE = "node"
internal const val EXTRA_INTERVAL = "interval"
internal const val EXTRA_TYPE = "type"
internal const val EXTRA_AGE_KEY = "age_key"
internal const val EXTRA_MODE = "mode"
internal const val EXTRA_AUTO_UPDATE = "auto_update"
internal const val EXTRA_AUTO_DELAY = "auto_delay"
internal const val EXTRA_FORMAT = "format"
internal const val EXTRA_IDS = "ids"
internal const val EXTRA_HOPS = "hops"
internal const val EXTRA_PAYLOAD = "payload"
internal const val EXTRA_PROXY = "proxy"
internal const val EXTRA_OPTIONS = "options"

private const val KEY_OK = "ok"
private const val KEY_MESSAGE = "message"
private const val KEY_COMMAND = "command"
private const val KEY_TARGET = "target"
private const val KEY_DATA = "data"

internal val PAGE_METHODS = setOf("page.open")

// 方法名一律「动词」或「动词_宾语」，别用名词当动作。主目标一律经 --arg 传，
// extras 只放修饰参数——两条规则任一破例，调用方就得逐条记特例。
internal val PROXY_METHODS = setOf(
    "proxy.start",
    "proxy.stop",
    "proxy.restart",
    "proxy.set_mode",
    "proxy.select",
    "proxy.test_group",
    "proxy.test_node",
    "proxy.list",
)

internal val SUBSCRIPTION_METHODS = setOf(
    "subscription.add",
    "subscription.list",
    "subscription.activate",
    "subscription.update",
    "subscription.update_all",
    "subscription.delete",
)

internal val OVERRIDE_METHODS = setOf(
    "override.add",
    "override.list",
    "override.update",
    "override.save",
    "override.select",
    "override.delete",
)

internal val CHAIN_METHODS = setOf(
    "chain.list",
    "chain.add",
    "chain.toggle",
    "chain.delete",
)

internal val RULE_METHODS = setOf(
    "rule.list",
    "rule.add",
    "rule.toggle",
    "rule.delete",
)

internal val SETTINGS_METHODS = setOf(
    "settings.get",
    "settings.set",
    "settings.dump",
    "settings.set_tun_mode",
)

internal val DNS_METHODS = setOf(
    "dns.query",
    "dns.flush_cache",
    "dns.flush_fakeip",
)

internal val STATE_METHODS = setOf("state.get")

internal val BACKUP_METHODS = setOf(
    "backup.export",
    "backup.restore",
)

internal fun debugResult(
    isOk: Boolean,
    message: String,
    command: String? = null,
    target: String? = null,
    data: String? = null,
): Bundle = Bundle().apply {
    putBoolean(KEY_OK, isOk)
    putString(KEY_MESSAGE, message)
    putString(KEY_COMMAND, command)
    putString(KEY_TARGET, target)
    data?.let { putString(KEY_DATA, it) }
}

internal inline fun <reified T : Any> koin(): T = GlobalContext.get().get<T>()

internal fun Bundle?.target(arg: String?): String? =
    arg?.takeIf { it.isNotBlank() } ?: this?.getString(EXTRA_VALUE)?.takeIf { it.isNotBlank() }

internal fun Bundle?.string(key: String): String? = this?.getString(key)?.takeIf { it.isNotBlank() }

internal fun Bundle?.boolean(key: String): Boolean? {
    val bundle = this ?: return null
    if (!bundle.containsKey(key)) return null
    @Suppress("DEPRECATION")
    return when (val raw = bundle.get(key)) {
        is Boolean -> raw
        is String -> raw.toBooleanStrictOrNull()
        else -> null
    }
}

internal fun Bundle?.int(key: String): Int? {
    val bundle = this ?: return null
    if (!bundle.containsKey(key)) return null
    @Suppress("DEPRECATION")
    return when (val raw = bundle.get(key)) {
        is Int -> raw
        is Long -> raw.toInt()
        is String -> raw.toIntOrNull()
        else -> null
    }
}
