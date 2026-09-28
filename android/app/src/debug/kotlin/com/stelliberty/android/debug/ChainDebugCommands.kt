package com.stelliberty.android.debug

import android.os.Bundle
import com.stelliberty.android.domain.model.Subscription
import com.stelliberty.android.domain.model.SubscriptionChainProxyHop
import com.stelliberty.android.domain.model.SubscriptionChainProxyHopKind
import com.stelliberty.android.domain.model.SubscriptionCustomChainProxy
import com.stelliberty.android.domain.repository.ChainProxyRepository
import com.stelliberty.android.domain.repository.SubscriptionRepository
import com.stelliberty.android.platform.ProxyServiceController
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking

// --arg 是订阅；保存后按页面行为在当前订阅上重启。hops 用逗号分隔，首跳写成 @组名 表示代理组。
@OptIn(ExperimentalUuidApi::class)
internal fun runChainCommand(action: String, arg: String?, extras: Bundle?): Bundle {
    val command = "chain.$action"
    val repository = koin<ChainProxyRepository>()
    val subscriptions = koin<SubscriptionRepository>()
    val controller = koin<ProxyServiceController>()
    val token = extras.target(arg) ?: return debugResult(false, "Missing subscription", command)
    val subscription = subscriptions.subscriptions.value.let { items ->
        items.firstOrNull { it.id == token } ?: items.firstOrNull { it.id.startsWith(token) }
            ?: items.firstOrNull { it.name == token }
    } ?: return debugResult(false, "Subscription not found", command, token)

    suspend fun save(disabled: List<String>, custom: List<SubscriptionCustomChainProxy>, message: String): Bundle =
        runCatching { repository.save(subscription.id, disabled, custom) }.fold(
            onSuccess = { hasCycle ->
                if (subscriptions.currentSubscriptionId.value == subscription.id) {
                    controller.restartWhenReady(subscription.id)
                }
                debugResult(true, message, command, subscription.id, "cycle=$hasCycle")
            },
            onFailure = { debugResult(false, it.describeForDebug(), command, subscription.id) },
        )

    return runBlocking {
        when (action) {
            "list" -> runCatching { repository.loadContext(subscription.id) }.fold(
                onSuccess = { context ->
                    val builtin = context.builtinNames.joinToString(",") { name ->
                        if (name in subscription.disabledBuiltinChainProxyNames) "-$name" else name
                    }
                    val custom = subscription.customChainProxies.joinToString(",") { chain ->
                        val mark = if (chain.isEnabled) "" else "-"
                        "$mark${chain.displayName}[${chain.id.take(8)}](${chain.proxyGroupName}:" +
                            chain.hops.joinToString(">") { it.name } + ")"
                    }
                    debugResult(
                        true,
                        "${context.builtinNames.size} builtin, ${subscription.customChainProxies.size} custom",
                        command,
                        subscription.id,
                        "builtin=$builtin; custom=$custom; groups=${context.proxyGroups.size}; " +
                            "candidates=${context.candidates.size}",
                    )
                },
                onFailure = { debugResult(false, it.describeForDebug(), command, subscription.id) },
            )

            "add" -> {
                val name = extras.string(EXTRA_NAME) ?: return@runBlocking debugResult(false, "Missing name", command)
                val group = extras.string(EXTRA_GROUP) ?: return@runBlocking debugResult(false, "Missing group", command)
                val hops = extras.string(EXTRA_HOPS).orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    .map { part ->
                        if (part.startsWith("@")) SubscriptionChainProxyHop(SubscriptionChainProxyHopKind.ProxyGroup, part.drop(1))
                        else SubscriptionChainProxyHop(SubscriptionChainProxyHopKind.Proxy, part)
                    }
                val chain = SubscriptionCustomChainProxy(Uuid.random().toHexString(), name, group, hops)
                save(subscription.disabledBuiltinChainProxyNames, subscription.customChainProxies + chain, "added ${chain.id}")
            }

            // name 先按自定义链的名称或 Id 前缀匹配，匹配不到按内置链名处理。
            "toggle" -> {
                val name = extras.string(EXTRA_NAME) ?: return@runBlocking debugResult(false, "Missing name", command)
                val custom = subscription.findCustom(name)
                when {
                    custom != null -> save(
                        subscription.disabledBuiltinChainProxyNames,
                        subscription.customChainProxies.map { if (it.id == custom.id) it.copy(isEnabled = !it.isEnabled) else it },
                        "toggled ${custom.id}",
                    )
                    else -> {
                        val disabled = subscription.disabledBuiltinChainProxyNames
                        save(if (name in disabled) disabled - name else disabled + name, subscription.customChainProxies, "toggled $name")
                    }
                }
            }

            "delete" -> {
                val name = extras.string(EXTRA_NAME) ?: return@runBlocking debugResult(false, "Missing name", command)
                val custom = subscription.findCustom(name)
                    ?: return@runBlocking debugResult(false, "Chain not found: $name", command, subscription.id)
                save(subscription.disabledBuiltinChainProxyNames, subscription.customChainProxies - custom, "deleted ${custom.id}")
            }

            else -> debugResult(false, "Unknown chain action: $action", command, arg)
        }
    }
}

private fun Subscription.findCustom(token: String): SubscriptionCustomChainProxy? =
    customChainProxies.firstOrNull { it.displayName == token } ?: customChainProxies.firstOrNull { it.id.startsWith(token) }
