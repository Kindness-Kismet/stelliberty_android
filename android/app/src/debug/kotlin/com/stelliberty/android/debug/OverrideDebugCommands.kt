package com.stelliberty.android.debug

import android.os.Bundle
import com.stelliberty.android.domain.model.OverrideFormat
import com.stelliberty.android.domain.model.SubscriptionUpdateProxyMode
import com.stelliberty.android.domain.repository.OverrideProfileRepository
import com.stelliberty.android.domain.repository.SubscriptionRepository
import com.stelliberty.android.platform.ProxyServiceController
import kotlinx.coroutines.runBlocking

// 与覆写页同一判据：改动影响当前订阅时重启，使保存结果立即生效。
internal fun runOverrideCommand(action: String, arg: String?, extras: Bundle?): Bundle {
    val command = "override.$action"
    val repository = koin<OverrideProfileRepository>()
    val subscriptions = koin<SubscriptionRepository>()
    val controller = koin<ProxyServiceController>()
    fun restartCurrent() {
        subscriptions.currentSubscriptionId.value?.let(controller::restartWhenReady)
    }
    return runBlocking {
        when (action) {
            "add" -> {
                val url = extras.string(EXTRA_URL) ?: extras.target(arg)
                    ?: return@runBlocking debugResult(false, "Missing url", command)
                val format = when (extras.string(EXTRA_FORMAT) ?: "yaml") {
                    "yaml" -> OverrideFormat.Yaml
                    "js" -> OverrideFormat.JavaScript
                    else -> return@runBlocking debugResult(false, "Unknown format, use yaml or js", command, url)
                }
                val name = extras.string(EXTRA_NAME) ?: url.substringAfterLast('/')
                runCatching { repository.addRemote(name, url, format, SubscriptionUpdateProxyMode.Core) }.fold(
                    onSuccess = { debugResult(true, "added ${it.id}", command, url, "id=${it.id}") },
                    onFailure = { debugResult(false, it.describeForDebug(), command, url) },
                )
            }

            "list" -> {
                val items = repository.profiles.value
                val data = items.joinToString("; ") { item ->
                    val mark = if (repository.isUsedByCurrent(item.id)) "*" else ""
                    "$mark${item.name}[${item.id.take(8)}](${item.sourceType},${item.format})"
                }
                debugResult(true, "${items.size} overrides", command, data = data)
            }

            "update" -> {
                val id = resolveOverrideId(repository, extras, arg)
                    ?: return@runBlocking debugResult(false, "Override not found", command, arg)
                runCatching { repository.update(id) }.fold(
                    onSuccess = {
                        if (repository.isUsedByCurrent(id)) restartCurrent()
                        debugResult(true, "updated $id", command, id)
                    },
                    onFailure = { debugResult(false, it.describeForDebug(), command, id) },
                )
            }

            // --arg 是订阅，ids 按应用顺序用逗号分隔，留空表示清空选择。
            "select" -> {
                val token = extras.string(EXTRA_UUID) ?: extras.target(arg)
                    ?: return@runBlocking debugResult(false, "Missing subscription", command)
                val subscription = subscriptions.subscriptions.value.let { items ->
                    items.firstOrNull { it.id == token } ?: items.firstOrNull { it.id.startsWith(token) }
                        ?: items.firstOrNull { it.name == token }
                } ?: return@runBlocking debugResult(false, "Subscription not found", command, token)
                val ids = extras.string(EXTRA_IDS).orEmpty().split(',').filter { it.isNotBlank() }.map { part ->
                    resolveOverrideId(repository, null, part.trim())
                        ?: return@runBlocking debugResult(false, "Override not found: $part", command, token)
                }
                val order = ids + repository.profiles.value.map { it.id }.filterNot { it in ids }
                runCatching { repository.setSelection(subscription.id, ids, order) }.fold(
                    onSuccess = {
                        if (ids != subscription.orderedOverrideIds && subscriptions.currentSubscriptionId.value == subscription.id) {
                            controller.restartWhenReady(subscription.id)
                        }
                        debugResult(true, "selected ${ids.size} overrides", command, subscription.id)
                    },
                    onFailure = { debugResult(false, it.describeForDebug(), command, subscription.id) },
                )
            }

            "save" -> {
                val id = resolveOverrideId(repository, extras, null)
                    ?: return@runBlocking debugResult(false, "Override not found", command)
                val content = arg ?: return@runBlocking debugResult(false, "Missing content", command, id)
                runCatching { repository.saveContent(id, content) }.fold(
                    onSuccess = {
                        if (repository.isUsedByCurrent(id)) restartCurrent()
                        debugResult(true, "saved $id", command, id)
                    },
                    onFailure = { debugResult(false, it.describeForDebug(), command, id) },
                )
            }

            "delete" -> {
                val id = resolveOverrideId(repository, extras, arg)
                    ?: return@runBlocking debugResult(false, "Override not found", command, arg)
                val used = repository.isUsedByCurrent(id)
                repository.delete(id)
                if (used) restartCurrent()
                debugResult(true, "deleted $id", command, id)
            }

            else -> debugResult(false, "Unknown override action: $action", command, arg)
        }
    }
}

private fun resolveOverrideId(repository: OverrideProfileRepository, extras: Bundle?, arg: String?): String? {
    val token = extras.string(EXTRA_UUID) ?: extras.target(arg) ?: return null
    val items = repository.profiles.value
    return items.firstOrNull { it.id == token }?.id
        ?: items.firstOrNull { it.id.startsWith(token) }?.id
        ?: items.firstOrNull { it.name == token }?.id
}
