package com.stelliberty.android.debug

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import kotlinx.coroutines.runBlocking
import com.stelliberty.android.data.api.MihomoConnectionManager
import com.stelliberty.android.data.repository.OverrideJsonStore
import com.stelliberty.android.data.store.ProxySelectionStore
import com.stelliberty.android.domain.repository.SubscriptionRepository
import com.stelliberty.android.platform.ProxyServiceController
import com.stelliberty.android.platform.TunMode
import com.stelliberty.android.service.VpnPermissionActivity
import com.stelliberty.android.viewmodel.ProxyViewModel

private val VALID_MODES = setOf("rule", "global", "direct")

internal fun runProxyCommand(context: Context, action: String, arg: String?, extras: Bundle?): Bundle {
    val command = "proxy.$action"
    val controller = koin<ProxyServiceController>()
    return when (action) {
        "start" -> {
            val uuid = extras.string(EXTRA_UUID)
            if (controller.getTunMode() == TunMode.Vpn && VpnService.prepare(context) != null) {
                val target = controller.resolveStartTarget(uuid)
                    ?: return debugResult(false, "Active subscription has no config on disk", command)
                context.startActivity(
                    Intent(context, VpnPermissionActivity::class.java).apply {
                        target.id?.let { putExtra(VpnPermissionActivity.EXTRA_SUBSCRIPTION_ID, it) }
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
                return debugResult(true, "VPN consent required; dialog launched", command, target.id ?: "(builtin)")
            }
            controller.start(uuid)
            debugResult(true, "start requested", command)
        }

        "stop" -> {
            controller.stop()
            debugResult(true, "stop requested", command)
        }

        "restart" -> {
            controller.restart(extras.string(EXTRA_UUID))
            debugResult(true, "restart requested", command)
        }

        "set_mode" -> {
            val mode = extras.target(arg)?.lowercase()
                ?: return debugResult(false, "Missing mode", command)
            if (mode !in VALID_MODES) {
                return debugResult(false, "Invalid mode: $mode (want ${VALID_MODES.joinToString("/")})", command, mode)
            }
            koin<OverrideJsonStore>().update { it.copy(mode = mode) }
            controller.restartWhenReady()
            debugResult(true, "mode set to $mode", command, mode)
        }

        else -> runProxyRuntimeCommand(action, command, arg, extras)
    }
}

private fun runProxyRuntimeCommand(action: String, command: String, arg: String?, extras: Bundle?): Bundle {
    val repository = koin<MihomoConnectionManager>().repository.value
        ?: return debugResult(false, "Proxy is not running", command)
    return runBlocking {
        when (action) {
            "select" -> {
                val group = extras.string(EXTRA_GROUP) ?: return@runBlocking debugResult(false, "Missing group", command)
                val node = extras.string(EXTRA_NODE) ?: extras.target(arg)
                    ?: return@runBlocking debugResult(false, "Missing node", command)
                repository.selectProxy(group, node).fold(
                    onSuccess = {
                        // 与代理页一样记下选择，内核重启后按它恢复。
                        koin<SubscriptionRepository>().getActive()?.let {
                            koin<ProxySelectionStore>().select(it.id, group, node)
                        }
                        koin<ProxyViewModel>().loadProxies()
                        debugResult(true, "selected $node in $group", command, node)
                    },
                    onFailure = { debugResult(false, it.describeForDebug(), command, node) },
                )
            }

            "test_group" -> {
                val group = extras.target(arg) ?: return@runBlocking debugResult(false, "Missing group", command)
                repository.getProxyDelay(group).fold(
                    onSuccess = { debugResult(true, "delay=${it.delay}ms", command, group, "delay=${it.delay}") },
                    onFailure = { debugResult(false, it.describeForDebug(), command, group) },
                )
            }

            "test_node" -> {
                val node = extras.string(EXTRA_NODE) ?: extras.target(arg)
                    ?: return@runBlocking debugResult(false, "Missing node", command)
                repository.getProxyDelay(node).fold(
                    onSuccess = { debugResult(true, "delay=${it.delay}ms", command, node, "delay=${it.delay}") },
                    onFailure = { debugResult(false, it.describeForDebug(), command, node) },
                )
            }

            "list" -> repository.getGroups().fold(
                onSuccess = { response ->
                    val data = response.proxies.joinToString("; ") { "${it.name}(${it.type})->${it.now}" }
                    debugResult(true, "${response.proxies.size} groups", command, data = data)
                },
                onFailure = { debugResult(false, it.describeForDebug(), command) },
            )

            else -> debugResult(false, "Unknown proxy action: $action", command, arg)
        }
    }
}

internal fun Throwable.describeForDebug(): String =
    message ?: this::class.simpleName ?: "Unknown error"
