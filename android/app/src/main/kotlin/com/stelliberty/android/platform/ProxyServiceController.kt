package com.stelliberty.android.platform

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.activity.result.ActivityResultLauncher
import com.stelliberty.android.R
import com.stelliberty.android.data.store.SubscriptionStore
import com.stelliberty.android.service.StellibertyRootService
import com.stelliberty.android.service.StellibertyTunService
import com.stelliberty.android.util.AppLogger
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ProxyServiceController(
    private val context: Context,
    private val subscriptionStore: SubscriptionStore,
) {

    private val storage by lazy { PlatformStorage(context) }

    private var launchAutoConnectConsumed = false

    private val scope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    @Volatile
    private var pendingRestartJob: Job? = null

    val status: StateFlow<ProxyServiceStatus> = ProxyServiceBridge.state

    fun start(subscriptionId: String? = null) {
        val target = resolveStartTarget(subscriptionId) ?: return
        val mode = getTunMode()
        val intent = buildServiceIntent(mode, Op.Start).apply {
            target.id?.let { putExtra(EXTRA_SUBSCRIPTION_ID, it) }
        }
        context.startForegroundService(intent)
    }

    fun restart(subscriptionId: String? = null) {
        val target = resolveStartTarget(subscriptionId) ?: return
        val mode = activeModeOrStored()
        val intent = buildServiceIntent(mode, Op.Restart).apply {
            target.id?.let { putExtra(EXTRA_SUBSCRIPTION_ID, it) }
        }
        context.startService(intent)
    }

    fun stop() {
        val mode = activeModeOrStored()
        val intent = buildServiceIntent(mode, Op.Stop)
        context.startService(intent)
    }

    // 判据只能用服务真实状态，启动期间界面仍显示未运行。启动中先挂起等稳定再补一次重启，
    // 停了就放弃——用户手动停的不该被这次重启拉起来。
    @Synchronized
    fun restartWhenReady(subscriptionId: String? = null) {
        pendingRestartJob?.cancel()
        pendingRestartJob = null
        when (ProxyServiceBridge.state.value.state) {
            ProxyState.Running -> restart(subscriptionId)

            ProxyState.Starting, ProxyState.Stopping -> {
                pendingRestartJob = scope.launch {
                    val settled = ProxyServiceBridge.state.first {
                        it.state != ProxyState.Starting && it.state != ProxyState.Stopping
                    }
                    if (settled.state != ProxyState.Running) return@launch
                    runCatching { restart(subscriptionId) }
                        .onFailure { AppLogger.error(TAG, "pending restart failed", it) }
                }
            }

            ProxyState.Stopped, ProxyState.Error -> {
            }
        }
    }

    @Synchronized
    fun cancelPendingRestart() {
        pendingRestartJob?.cancel()
        pendingRestartJob = null
    }

    // 切换或删除当前订阅之后调。删光最后一条时改成停止：没有配置可切，重启只会失败成错误态。
    fun onActiveSubscriptionChanged() {
        if (!hasStartableSubscription()) {
            cancelPendingRestart()
            val state = ProxyServiceBridge.state.value.state
            if (state == ProxyState.Running || state == ProxyState.Starting) stop()
            return
        }
        restartWhenReady(subscriptionStore.currentId())
    }

    // mihomo 只在启动时读一次配置文件，而内嵌模式下热重载接口全被关掉了，重启进程是唯一让新订阅生效的办法。
    // 是否为当前订阅的判断统一收在这里，前台与后台任务不各写一套。
    fun restartAfterProfileUpdate(uuid: String) {
        if (storage.getString(StorageKeys.RESTART_AFTER_PROFILE_UPDATE, "true") != "true") return
        if (uuid != subscriptionStore.currentId()) return
        restartWhenReady(uuid)
    }

    fun reattachRoot() {
        val mode = getTunMode()
        if (mode != TunMode.RootTun && mode != TunMode.RootTproxy) return
        val id = subscriptionStore.currentId() ?: run {
            storage.putString(StorageKeys.SERVICE_WAS_RUNNING, "false")
            return
        }
        val intent = buildServiceIntent(mode, Op.Start).apply {
            putExtra(EXTRA_SUBSCRIPTION_ID, id)
            putExtra(StellibertyRootService.EXTRA_ATTACH_ONLY, true)
        }
        context.startForegroundService(intent)
    }

    // id 为 null 表示一条订阅都没有，此时用内置的最小配置启动，
    // 内核照常跑起来、流量直连，用户可以先连上再去导入订阅。
    @JvmInline
    value class StartTarget(val id: String?)

    // 所有启动路径的唯一校验单点。分三种情况：订阅可用就用；一条都没有就放行走兜底配置；
    // 指定的那条坏了或没落盘才算错误——那时静默直连是在骗用户。
    fun resolveStartTarget(subscriptionId: String? = null): StartTarget? {
        val requested = subscriptionId
            ?: subscriptionStore.currentId()
            ?: return StartTarget(null)

        if (hasConfigOnDisk(requested)) return StartTarget(requested)

        AppLogger.warn(TAG, "Refusing to start: no config on disk for $requested")
        val msg = noActiveProfileMessage()
        showToast(msg, long = true)
        val mode = activeModeOrStored()
        val wasRunning = ProxyServiceBridge.state.value.state == ProxyState.Running
        ProxyServiceBridge.updateState(
            ProxyServiceStatus(
                state = ProxyState.Error,
                errorMessage = msg,
                tunMode = mode,
                errorNotified = true,
            )
        )
        if (wasRunning) {
            context.startService(buildServiceIntent(mode, Op.Stop))
        }
        storage.putString(StorageKeys.SERVICE_WAS_RUNNING, "false")
        return null
    }

    // 删掉最后一条订阅后该停还是该重启，用这个判断。
    fun hasStartableSubscription(): Boolean = startableSubscriptionId() != null

    // 没有任何副作用，供自动连接这类「不该因为缺订阅就打断用户」的静默路径使用。
    private fun startableSubscriptionId(subscriptionId: String? = null): String? {
        val effective = subscriptionId
            ?: subscriptionStore.currentId()
            ?: return null
        return effective.takeIf { hasConfigOnDisk(it) }
    }

    private fun hasConfigOnDisk(uuid: String): Boolean {
        val config = File(context.filesDir, "mihomo/imported/$uuid/config.yaml")
        return config.isFile && config.length() > 0
    }

    private fun noActiveProfileMessage(): String =
        context.getString(R.string.error_no_active_profile)

    private fun activeModeOrStored(): TunMode {
        val bridge = ProxyServiceBridge.state.value
        return if (bridge.state != ProxyState.Stopped && bridge.state != ProxyState.Error) {
            bridge.tunMode
        } else {
            getTunMode()
        }
    }

    private enum class Op { Start, Restart, Stop }

    // 类名和各个参数名一律用符号引用，不要写字符串常量：改名时编译器不会报错，
    // 故障表现是「只重连悄悄退化成了全新启动」，正好是最不该发生的那件事。
    private fun buildServiceIntent(mode: TunMode, op: Op): Intent = when (mode) {
        TunMode.Vpn -> Intent(context, StellibertyTunService::class.java).setAction(
            when (op) {
                Op.Start -> StellibertyTunService.ACTION_START
                Op.Restart -> StellibertyTunService.ACTION_RESTART
                Op.Stop -> StellibertyTunService.ACTION_STOP
            }
        )

        TunMode.RootTun, TunMode.RootTproxy -> Intent(context, StellibertyRootService::class.java).apply {
            action = when (op) {
                Op.Start -> StellibertyRootService.ACTION_START
                Op.Restart -> StellibertyRootService.ACTION_RESTART
                Op.Stop -> StellibertyRootService.ACTION_STOP
            }
            StellibertyRootService.submodeExtra(mode)?.let { putExtra(StellibertyRootService.EXTRA_SUBMODE, it) }
        }
    }

    private var vpnPermissionLauncher: ActivityResultLauncher<Intent>? = null

    fun setVpnPermissionLauncher(launcher: ActivityResultLauncher<Intent>?) {
        vpnPermissionLauncher = launcher
    }

    fun requestVpnPermission() {
        if (getTunMode() != TunMode.Vpn) return
        val intent = VpnService.prepare(context) ?: return
        vpnPermissionLauncher?.launch(intent)
    }

    fun hasVpnPermission(): Boolean {
        if (getTunMode() != TunMode.Vpn) return true
        return VpnService.prepare(context) == null
    }

    fun hasRootPermission(): Boolean {
        return storage.getString(StorageKeys.HAS_ROOT, "false") == "true"
    }

    fun getTunMode(): TunMode = TunMode.fromStorage(storage.getString(StorageKeys.TUN_MODE, TunMode.Vpn.storageValue))

    fun verifyAndSyncState() {
        val autoConnect = !launchAutoConnectConsumed &&
                storage.getString(StorageKeys.AUTO_CONNECT_ON_LAUNCH, "false") == "true"
        launchAutoConnectConsumed = true

        val wasRunning = storage.getString(StorageKeys.SERVICE_WAS_RUNNING, "false") == "true"
        val bridgeState = ProxyServiceBridge.state.value.state

        if (bridgeState == ProxyState.Running || bridgeState == ProxyState.Starting) {
            if (getTunMode() == TunMode.Vpn && !hasVpnPermission()) {
                storage.putString(StorageKeys.SERVICE_WAS_RUNNING, "false")
                ProxyServiceBridge.markStopped(activeModeOrStored())
            }
            return
        }

        if (bridgeState == ProxyState.Stopping) {
            storage.putString(StorageKeys.SERVICE_WAS_RUNNING, "false")
            ProxyServiceBridge.updateState(ProxyServiceStatus(ProxyState.Stopped))
            return
        }

        val currentMode = getTunMode()
        if (wasRunning && (currentMode == TunMode.RootTun || currentMode == TunMode.RootTproxy)) {
            val hasPid = storage.getString(StorageKeys.ROOT_MIHOMO_PID, "").isNotEmpty()
            val rebooted = BootSession.hasRebootedSince(context, storage)
            if (hasPid && !rebooted) {
                if (autoConnect) launchAutoConnect() else reattachRoot()
                return
            }
            storage.putString(StorageKeys.SERVICE_WAS_RUNNING, "false")
            storage.putString(StorageKeys.ROOT_MIHOMO_PID, "")
            BootSession.clear(storage)
            if (autoConnect) launchAutoConnect()
            return
        }

        if (wasRunning && currentMode == TunMode.Vpn) {
            storage.putString(StorageKeys.SERVICE_WAS_RUNNING, "false")
        }
        if (autoConnect) launchAutoConnect()
    }

    // 自动连接不能弹错误打断只是打开应用的用户，所以走无副作用的校验版；
    // 一条订阅都没有时也照常连，跟手动点启动保持一致。
    private fun launchAutoConnect() {
        val hasBroken = subscriptionStore.currentId() != null &&
                startableSubscriptionId() == null
        if (hasBroken) return
        if (getTunMode() == TunMode.Vpn && !hasVpnPermission()) {
            requestVpnPermission()
            return
        }
        start(startableSubscriptionId())
    }

    companion object {
        private const val TAG = "ProxyServiceController"
        const val EXTRA_SUBSCRIPTION_ID = "subscription_id"
    }
}
