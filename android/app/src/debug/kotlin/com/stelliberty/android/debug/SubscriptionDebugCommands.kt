package com.stelliberty.android.debug

import android.os.Bundle
import kotlinx.coroutines.runBlocking
import com.stelliberty.android.data.repository.ProfileProcessor
import com.stelliberty.android.domain.model.SubscriptionAutoUpdateMode
import com.stelliberty.android.domain.repository.SubscriptionRepository
import com.stelliberty.android.platform.ProfileFileManager
import com.stelliberty.android.platform.ProxyServiceController

internal fun runSubscriptionCommand(action: String, arg: String?, extras: Bundle?): Bundle {
    val command = "subscription.$action"
    val repository = koin<SubscriptionRepository>()
    return runBlocking {
        when (action) {
            "add" -> {
                val url = extras.string(EXTRA_URL) ?: extras.target(arg)
                    ?: return@runBlocking debugResult(false, "Missing url", command)
                val name = extras.string(EXTRA_NAME).orEmpty()
                val interval = extras.int(EXTRA_INTERVAL) ?: 0
                val autoUpdate = when (extras.string(EXTRA_AUTO_UPDATE)) {
                    null -> if (interval > 0) SubscriptionAutoUpdateMode.Interval else SubscriptionAutoUpdateMode.Disabled
                    "disabled" -> SubscriptionAutoUpdateMode.Disabled
                    "startup" -> SubscriptionAutoUpdateMode.Startup
                    "interval" -> SubscriptionAutoUpdateMode.Interval
                    else -> return@runBlocking debugResult(false, "Unknown auto_update, use disabled, startup or interval", command, url)
                }
                val updateProxy = extras.updateProxyMode()
                    ?: return@runBlocking debugResult(false, "Unknown update_proxy, use direct, system or core", command, url)
                val created = repository.create(
                    name = name,
                    sourceLocation = url,
                    isLocalFile = false,
                    autoUpdateMode = autoUpdate,
                    autoUpdateIntervalMinutes = interval,
                    ageSecretKey = extras.string(EXTRA_AGE_KEY).orEmpty(),
                    updateProxyMode = updateProxy,
                    autoTestDelayIntervalMinutes = extras.int(EXTRA_AUTO_DELAY) ?: 0,
                )
                runCatching { koin<ProfileProcessor>().apply(created.id) }.fold(
                    onSuccess = {
                        debugResult(true, "imported ${created.id}", command, url, "uuid=${created.id}")
                    },
                    onFailure = { error ->
                        runCatching { repository.release(created.id) }
                        debugResult(false, error.describeForDebug(), command, url)
                    },
                )
            }

            "list" -> {
                val items = repository.subscriptions.value
                val activeUuid = repository.currentSubscriptionId.value
                val data = items.joinToString("; ") { sub ->
                    val mark = if (sub.id == activeUuid) "*" else ""
                    val kind = if (sub.isLocalFile) "File" else "Url"
                    "$mark${sub.name}[${sub.id.take(8)}]($kind)"
                }
                debugResult(true, "${items.size} subscriptions", command, data = data)
            }

            "activate" -> {
                val uuid = resolveUuid(repository, extras, arg)
                    ?: return@runBlocking debugResult(false, "Subscription not found", command, arg)
                repository.setActive(uuid)
                // setActive 只写存储，重启由这一句发起。跳过它的话代理还跑着上一条订阅的配置。
                koin<ProxyServiceController>().onActiveSubscriptionChanged()
                debugResult(true, "activated $uuid", command, uuid)
            }

            // 更新完必须补 restartAfterProfileUpdate：内核只在启动时读一次配置，不重启就还跑着旧的。
            // 三个真实入口（订阅页单条、订阅页全部、后台任务）都调了它，指令这条是第四个入口，
            // 漏调就会让「更新成功」和「新配置生效」在指令这条路上不再是一回事。
            "update" -> {
                val uuid = resolveUuid(repository, extras, arg)
                    ?: return@runBlocking debugResult(false, "Subscription not found", command, arg)
                runCatching { koin<ProfileProcessor>().update(uuid) }.fold(
                    onSuccess = {
                        koin<ProxyServiceController>().restartAfterProfileUpdate(uuid)
                        debugResult(true, "updated $uuid", command, uuid)
                    },
                    onFailure = { debugResult(false, it.describeForDebug(), command, uuid) },
                )
            }

            "update_all" -> {
                val items = repository.subscriptions.value.filterNot { it.isLocalFile }
                val processor = koin<ProfileProcessor>()
                val failed = items.mapNotNull { sub ->
                    runCatching { processor.update(sub.id) }.exceptionOrNull()?.let { "${sub.name}: ${it.describeForDebug()}" }
                }
                // 重启排在整轮之后：中途重启会断网，后面还要下载的订阅会跟着一起失败。
                // 只有当前订阅那次真的会重启，其余被控制器内部的判断挡掉。
                val controller = koin<ProxyServiceController>()
                items.forEach { controller.restartAfterProfileUpdate(it.id) }
                if (failed.isEmpty()) {
                    debugResult(true, "updated ${items.size} subscriptions", command)
                } else {
                    debugResult(false, "failed: ${failed.joinToString("; ")}", command)
                }
            }

            "delete" -> {
                val uuid = resolveUuid(repository, extras, arg)
                    ?: return@runBlocking debugResult(false, "Subscription not found", command, arg)
                val wasActive = repository.currentSubscriptionId.value == uuid
                repository.delete(uuid)
                koin<ProfileFileManager>().deleteDirs(uuid)
                // 删的是当前订阅时才需要处理：仓库已经把 active 挪到剩下的第一条，
                // 挪完得让代理跟着切过去；一条都不剩则改成停止。
                if (wasActive) koin<ProxyServiceController>().onActiveSubscriptionChanged()
                debugResult(true, "deleted $uuid", command, uuid)
            }

            else -> debugResult(false, "Unknown subscription action: $action", command, arg)
        }
    }
}

private fun resolveUuid(repository: SubscriptionRepository, extras: Bundle?, arg: String?): String? {
    val token = extras.string(EXTRA_UUID) ?: extras.target(arg) ?: return null
    val items = repository.subscriptions.value
    return items.firstOrNull { it.id == token }?.id
        ?: items.firstOrNull { it.id.startsWith(token) }?.id
        ?: items.firstOrNull { it.name == token }?.id
}
