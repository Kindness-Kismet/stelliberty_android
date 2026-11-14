package com.stelliberty.android.debug

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import com.stelliberty.android.data.api.AutoDelayTester
import com.stelliberty.android.data.api.MihomoConnectionManager
import com.stelliberty.android.data.repository.OverrideJsonStore
import com.stelliberty.android.data.store.ProxySelectionStore
import com.stelliberty.android.domain.repository.SubscriptionRepository
import com.stelliberty.android.domain.repository.MihomoRepository
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

        "set_auto_delay" -> {
            val interval = extras.target(arg)?.toIntOrNull()
                ?.takeIf { it in 0..999999 }
                ?: return debugResult(false, "Invalid interval (want 0..999999 minutes)", command)
            val subscriptions = koin<SubscriptionRepository>()
            val uuid = subscriptions.getActive()?.id
                ?: return debugResult(false, "No active subscription", command)
            runBlocking { subscriptions.setAutoTestDelayInterval(uuid, interval) }
            debugResult(true, "Auto delay interval set to ${interval}m", command, uuid)
        }

        "get_test_state" -> {
            val repository = koin<MihomoConnectionManager>().repository.value
            val schedule = koin<AutoDelayTester>().snapshot()
            val ui = koin<ProxyViewModel>().uiState.value
            val data = buildString {
                append("testingNodes=").append(repository?.testingNodes?.value.orEmpty().joinToString(","))
                append("; delays=").append(repository?.delayResults?.value.orEmpty().entries.joinToString(",") { "${it.key}:${it.value.delay}" })
                append("; subscriptionId=").append(schedule.subscriptionId)
                append("; intervalMinutes=").append(schedule.intervalMinutes)
                append("; nextElapsedRealtime=").append(schedule.nextElapsedRealtime)
                append("; isAutoTesting=").append(schedule.isTesting)
                append("; uiGroups=").append(ui.groups.size)
                append("; uiRunning=").append(ui.isProxyRunning)
                append("; uiError=").append(ui.error)
                append("; uiDelays=").append(ui.groups.joinToString("|") { group ->
                    "${group.name}=" + group.delays.entries.joinToString(",") { "${it.key}:${it.value}" }
                })
            }
            debugResult(true, "Delay state read", command, data = data)
        }

        else -> runProxyRuntimeCommand(action, command, arg, extras)
    }
}

private fun runProxyRuntimeCommand(action: String, command: String, arg: String?, extras: Bundle?): Bundle {
    val connectionManager = koin<MihomoConnectionManager>()
    val repository = connectionManager.repository.value
        ?: return debugResult(false, "Proxy is not running", command)
    return runBlocking {
        when (action) {
            "select" -> {
                val group = extras.string(EXTRA_GROUP) ?: return@runBlocking debugResult(false, "Missing group", command)
                val node = extras.string(EXTRA_NODE) ?: extras.target(arg)
                    ?: return@runBlocking debugResult(false, "Missing node", command)
                val subscriptions = koin<SubscriptionRepository>()
                val uuid = subscriptions.getActive()?.id
                val result = repository.selectProxy(group, node)
                withContext(Dispatchers.Main.immediate) {
                    if (connectionManager.repository.value !== repository || subscriptions.getActive()?.id != uuid) {
                        return@withContext debugResult(false, "Proxy changed during selection", command, node)
                    }
                    result.fold(
                        onSuccess = {
                            uuid?.let { koin<ProxySelectionStore>().select(it, group, node) }
                            koin<ProxyViewModel>().loadProxies()
                            debugResult(true, "selected $node in $group", command, node)
                        },
                        onFailure = { debugResult(false, it.describeForDebug(), command, node) },
                    )
                }
            }

            "test_group" -> {
                val group = extras.target(arg) ?: return@runBlocking debugResult(false, "Missing group", command)
                val nodes = repository.getGroups().getOrThrow().proxies.firstOrNull { it.name == group }?.all
                    ?: return@runBlocking debugResult(false, "Unknown group", command, group)
                repository.testDelays(nodes, repository.providerNodes())
                debugResult(true, "Group delay test completed", command, group)
            }

            "test_all" -> {
                val groups = repository.getGroups().getOrThrow().proxies
                val nodes = groups.flatMapTo(LinkedHashSet()) { it.all }
                repository.testDelays(nodes, repository.providerNodes())
                debugResult(true, "All delay tests completed", command, data = "nodes=${nodes.size}")
            }

            "test_node" -> {
                val node = extras.string(EXTRA_NODE) ?: extras.target(arg)
                    ?: return@runBlocking debugResult(false, "Missing node", command)
                val provider = repository.providerNodes()[node]
                val result = if (provider == null) repository.getProxyDelay(node)
                    else repository.getProviderProxyDelay(provider, node)
                result.fold(
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

private suspend fun MihomoRepository.providerNodes(): Map<String, String> {
    val runtime = getProxies().getOrThrow().proxies
    val providers = getProviders().getOrThrow().providers
    return buildMap {
        providers.forEach { (name, provider) ->
            provider.proxies.forEach { node ->
                if (node.name !in runtime) putIfAbsent(node.name, name)
            }
        }
    }
}

internal fun Throwable.describeForDebug(): String =
    message ?: this::class.simpleName ?: "Unknown error"
