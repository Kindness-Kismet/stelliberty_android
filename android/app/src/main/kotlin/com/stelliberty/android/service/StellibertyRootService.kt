package com.stelliberty.android.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.stelliberty.android.R
import com.stelliberty.android.StellibertyApplication
import com.stelliberty.android.data.repository.OverrideJsonStore
import com.stelliberty.android.data.store.ProfileTransformWriter
import com.stelliberty.android.data.store.SubscriptionStore
import com.stelliberty.android.domain.model.AppProxyMode
import com.stelliberty.android.domain.model.resolveExternalController
import com.stelliberty.android.platform.AppListProvider
import com.stelliberty.android.platform.BootSession
import com.stelliberty.android.platform.PlatformStorage
import com.stelliberty.android.platform.ProxyServiceBridge
import com.stelliberty.android.platform.ProxyServiceController
import com.stelliberty.android.platform.ProxyServiceStatus
import com.stelliberty.android.platform.ProxyState
import com.stelliberty.android.platform.StorageKeys
import com.stelliberty.android.platform.TunMode
import com.stelliberty.android.service.StellibertyRootService.Companion.EXTRA_SUBMODE
import com.stelliberty.android.util.AppLogger
import java.io.File
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class StellibertyRootService : Service() {

    private enum class Submode(val storageValue: String, val tunMode: TunMode) {
        Tun("tun", TunMode.RootTun),
        Tproxy("tproxy", TunMode.RootTproxy);

        companion object {
            fun from(value: String?): Submode = entries.firstOrNull { it.storageValue == value } ?: Tun
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runner by lazy { MihomoRunner(this) }
    private val dynamicNotification by lazy {
        DynamicNotificationManager(this, scope, StellibertyApplication.instance.connectionManager)
    }

    private val overrideStore: OverrideJsonStore by inject()
    private val subscriptionStore: SubscriptionStore by inject()
    private val transformWriter: ProfileTransformWriter by inject()
    private var monitorJob: Job? = null
    private var notificationRefreshJob: Job? = null

    @Volatile
    private var startJob: Job? = null

    @Volatile
    private var startJobAttachOnly = false

    @Volatile
    private var currentSubmode: Submode = Submode.Tun

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        try {
            startForeground(
                NotificationHelper.NOTIFICATION_ID_VPN,
                NotificationHelper.buildLoadingNotification(this),
            )
        } catch (e: Exception) {
            AppLogger.error(TAG, "startForeground failed", e)
            ProxyServiceBridge.updateState(
                ProxyServiceStatus(
                    ProxyState.Error,
                    errorMessage = getString(R.string.error_foreground_failed, e.message ?: e.javaClass.simpleName),
                    tunMode = currentSubmode.tunMode,
                )
            )
            stopSelf()
            return
        }
        notificationRefreshJob = scope.launch {
            ProxyServiceBridge.notificationRefresh.collect {
                val state = ProxyServiceBridge.state.value
                if (state.state == ProxyState.Running && isRootRunning(state.tunMode)) {
                    dynamicNotification.stop()
                    dynamicNotification.startOrFallbackStatic(
                        PlatformStorage(this@StellibertyRootService),
                        state.tunMode,
                    )
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val requested = Submode.from(intent?.getStringExtra(EXTRA_SUBMODE))
        currentSubmode = if (intent?.hasExtra(EXTRA_SUBMODE) == true) {
            requested
        } else {
            val active = PlatformStorage(this).getString(StorageKeys.ROOT_SUBMODE_ACTIVE, "")
            Submode.from(active.ifEmpty { requested.storageValue })
        }
        when (intent?.action) {
            ACTION_START -> {
                val subscriptionId = intent.getStringExtra(ProxyServiceController.EXTRA_SUBSCRIPTION_ID)
                val attachOnly = intent.getBooleanExtra(EXTRA_ATTACH_ONLY, false)
                startProxy(subscriptionId, attachOnly)
            }

            ACTION_STOP -> stopProxy()
            ACTION_RESTART -> {
                val subscriptionId = intent.getStringExtra(ProxyServiceController.EXTRA_SUBSCRIPTION_ID)
                restartProxy(subscriptionId)
            }
        }
        return START_STICKY
    }

    private fun isRootRunning(mode: TunMode): Boolean =
        mode == TunMode.RootTun || mode == TunMode.RootTproxy

    // attachOnly 表示「只重连已有进程，连不上就保持停止」，用户只是打开了应用、没要求启动。
    // 但用户主动点启动时可以抢占正在跑的 attachOnly——它没有挂起点，得靠自己检查取消标志主动让位。
    private fun startProxy(subscriptionId: String? = null, attachOnly: Boolean = false) {
        val inFlight = startJob?.takeIf { it.isActive }
        if (inFlight != null) {
            if (attachOnly || !startJobAttachOnly) {
                AppLogger.info(TAG, "Start in progress, ignoring duplicate START (attachOnly=$attachOnly)")
                return
            }
            AppLogger.info(TAG, "Fresh START supersedes in-flight attach-only start")
            inFlight.cancel()
        }
        startJobAttachOnly = attachOnly
        startJob = scope.launch {
            inFlight?.join()
            val submode = currentSubmode
            val tunMode = submode.tunMode
            AppLogger.info(TAG, "Starting proxy (ROOT $submode), subscription: $subscriptionId, attachOnly=$attachOnly")
            // 没有订阅时用内置配置，工作目录就是应用自己的 mihomo 目录、不建沙箱；
            // 带了编号却读不到配置才是错误。
            if (subscriptionId == null) {
                ConfigGenerator.writeFallbackConfig(this@StellibertyRootService)
            } else if (!ProfileFileOps.hasValidConfig(this@StellibertyRootService, subscriptionId)) {
                AppLogger.error(TAG, "No valid subscription config (id=$subscriptionId), aborting start")
                ProxyServiceBridge.updateState(
                    ProxyServiceStatus(
                        ProxyState.Error,
                        errorMessage = getString(R.string.error_no_active_profile),
                        tunMode = tunMode,
                    )
                )
                stopSelf()
                return@launch
            }
            ProxyServiceBridge.updateState(ProxyServiceStatus(ProxyState.Starting, tunMode = tunMode))

            val storage = PlatformStorage(this@StellibertyRootService)
            val tetherModeRequested = storage.getString(
                StorageKeys.ROOT_TETHER_MODE,
                RootTetherHijacker.Mode.BYPASS.storageValue,
            )
            val tetherModeActive = storage.getString(StorageKeys.ROOT_TETHER_MODE_ACTIVE, "")
            val submodeActive = storage.getString(StorageKeys.ROOT_SUBMODE_ACTIVE, "")

            val existingPid = storage.getString(StorageKeys.ROOT_MIHOMO_PID, "").toIntOrNull() ?: -1
            val existingSecret = storage.getString(StorageKeys.ROOT_MIHOMO_SECRET, "")
            val existingSubscriptionId = storage.getString(StorageKeys.ROOT_ACTIVE_SUBSCRIPTION_ID, "").ifEmpty { null }
            val subscriptionMismatch = existingPid > 0 && subscriptionId != existingSubscriptionId
            val tetherModeMismatch = existingPid > 0 &&
                    tetherModeActive.isNotEmpty() &&
                    tetherModeActive != tetherModeRequested
            val submodeMismatch = existingPid > 0 &&
                    submodeActive.isNotEmpty() &&
                    submodeActive != submode.storageValue
            if (subscriptionMismatch) {
                AppLogger.info(
                    TAG,
                    "Existing process pid=$existingPid runs subscription=$existingSubscriptionId, requested=$subscriptionId, restarting"
                )
            }
            if (tetherModeMismatch) {
                AppLogger.info(
                    TAG,
                    "Tether mode changed while app was killed (active=$tetherModeActive, requested=$tetherModeRequested), restarting"
                )
            }
            if (submodeMismatch) {
                AppLogger.info(TAG, "Submode changed while app was killed (active=$submodeActive, requested=${submode.storageValue}), restarting")
            }
            if (existingPid > 0 && existingSecret.isNotEmpty() && !subscriptionMismatch && !tetherModeMismatch && !submodeMismatch) {
                val ec = overrideStore.load().resolveExternalController()
                if (runner.attachToExisting(existingPid, existingSecret, ec, subscriptionId)) {
                    val existingStartTime =
                        storage.getString(StorageKeys.ROOT_START_TIME, "").toLongOrNull() ?: Clock.System.now().toEpochMilliseconds()
                    AppLogger.info(TAG, "Reconnected to existing mihomo: pid=$existingPid submode=${submode.storageValue}")
                    when (submode) {
                        Submode.Tun -> {
                            val activeMode = RootTetherHijacker.Mode.from(tetherModeActive.ifEmpty { tetherModeRequested })
                            val tproxySupported = activeMode == RootTetherHijacker.Mode.PROXY &&
                                    RootTetherHijacker.probeTproxySupport()
                            if (shouldReapplyOnAttach(storage, RootTetherHijacker::anyRulesPresent)) {
                                applyTetherRulesActive(storage, tproxySupported)
                            }
                        }

                        Submode.Tproxy -> {
                            if (shouldReapplyOnAttach(storage, RootTproxyApplier::anyRulesPresent)) {
                                applyTproxyRules(storage)
                            }
                        }
                    }
                    ProxyServiceBridge.updateState(
                        ProxyServiceStatus(
                            ProxyState.Running,
                            secret = existingSecret,
                            externalController = ec,
                            tunMode = tunMode,
                            startTime = existingStartTime,
                            mihomoPid = runner.pid
                        )
                    )
                    dynamicNotification.startOrFallbackStatic(storage, tunMode)
                    storage.putString(StorageKeys.SERVICE_WAS_RUNNING, "true")
                    val workDir = if (subscriptionId != null) ProfileFileOps.getRuntimeDir(
                        this@StellibertyRootService,
                        subscriptionId
                    ) else ConfigGenerator.getWorkDir(this@StellibertyRootService)
                    startProcessMonitor(workDir)
                    return@launch
                }
                AppLogger.info(TAG, "Existing process pid=$existingPid failed attach verification, cleaning up")
                clearPersistedState(storage)
            }

            if (attachOnly) {
                if (!isActive) {
                    AppLogger.info(TAG, "Attach-only start superseded, leaving state to the new start")
                    return@launch
                }
                AppLogger.info(TAG, "Attach-only reopen: no live mihomo to reconnect, staying stopped")
                clearPersistedState(storage)
                storage.putString(StorageKeys.SERVICE_WAS_RUNNING, "false")
                ProxyServiceBridge.markStopped(tunMode)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }

            if (runner.isRunning) {
                runner.stop()
            }
            val currentTun = storage.getString(StorageKeys.ROOT_TUN_DEVICE, RuntimeOverrideBuilder.DEFAULT_TUN_DEVICE)
            RootHelper.cleanupOrphanedMihomo(tunDevice = currentTun)
            teardownAllRootRules()

            if (!RootHelper.hasRootAccess()) {
                AppLogger.error(TAG, "Failed to obtain root access")
                ProxyServiceBridge.updateState(
                    ProxyServiceStatus(ProxyState.Error, errorMessage = getString(R.string.error_root_failed), tunMode = tunMode)
                )
                stopSelf()
                return@launch
            }

            if (submode == Submode.Tproxy) {
                if (!RootTproxyApplier.probeTproxySupport()) {
                    storage.putString(StorageKeys.ROOT_TPROXY_KERNEL_CAPABLE, "false")
                    AppLogger.error(TAG, "xt_TPROXY unsupported, cannot start ROOT TPROXY mode")
                    ProxyServiceBridge.updateState(
                        ProxyServiceStatus(ProxyState.Error, errorMessage = getString(R.string.error_tproxy_unsupported), tunMode = tunMode)
                    )
                    stopSelf()
                    return@launch
                }
                storage.putString(StorageKeys.ROOT_TPROXY_KERNEL_CAPABLE, "true")
            }

            if (subscriptionId != null) {
                try {
                    ProfileFileOps.prepareRootRuntime(this@StellibertyRootService, subscriptionId)
                } catch (e: Exception) {
                    AppLogger.error(TAG, "Failed to prepare runtime sandbox", e)
                    ProxyServiceBridge.updateState(
                        ProxyServiceStatus(
                            ProxyState.Error,
                            errorMessage = getString(R.string.error_generic_start_failed, e.message ?: e.javaClass.simpleName),
                            tunMode = tunMode,
                        )
                    )
                    stopSelf()
                    return@launch
                }
            }

            val userOverride = overrideStore.load()
            val secret = ConfigGenerator.resolveSecret(this@StellibertyRootService, userOverride, subscriptionId)
            val extCtl = userOverride.resolveExternalController()
            val userWantsProxy = submode == Submode.Tun &&
                    tetherModeRequested == RootTetherHijacker.Mode.PROXY.storageValue
            val tproxyForTether = if (userWantsProxy) {
                val supported = RootTetherHijacker.probeTproxySupport()
                if (!supported) AppLogger.warn(TAG, "xt_TPROXY unavailable, PROXY degraded to userspace TUN")
                storage.putString(StorageKeys.ROOT_TPROXY_KERNEL_CAPABLE, if (supported) "true" else "false")
                supported
            } else {
                if (submode == Submode.Tun) {
                    storage.putString(StorageKeys.ROOT_TPROXY_KERNEL_CAPABLE, "")
                }
                false
            }
            val subMixedPort = subscriptionId?.let {
                ConfigGenerator.readSubscriptionMixedPort(this@StellibertyRootService, it)
            }
            val overrideFile = RuntimeOverrideBuilder.buildAndWriteForRun(
                context = this@StellibertyRootService,
                userOverride = userOverride,
                tunFd = -1,
                tunMode = submode.tunMode,
                subscriptionUpdateViaProxy = subscriptionStore.anyUpdatesViaProxy(),
                subscriptionMixedPort = subMixedPort,
                tproxyForTether = tproxyForTether,
            )

            val subscription = subscriptionId?.let(subscriptionStore::findImported)
            val success = runner.start(
                subscriptionId = subscriptionId,
                useRoot = true,
                overrideJsonPath = overrideFile.absolutePath,
                secret = secret,
                externalController = extCtl,
                ageSecretKey = subscription?.ageSecretKey.orEmpty(),
                transformPath = transformWriter.writeRuntime(subscription),
            )
            if (!success) {
                val errorMsg = runner.errorMessage.ifBlank { getString(R.string.error_start_failed) }
                AppLogger.error(TAG, "Failed to start mihomo (ROOT): $errorMsg")
                ProxyServiceBridge.updateState(
                    ProxyServiceStatus(ProxyState.Error, errorMessage = errorMsg, tunMode = tunMode)
                )
                stopSelf()
                return@launch
            }

            val startTime = Clock.System.now().toEpochMilliseconds()
            persistState(storage, runner.secret, startTime, subscriptionId)
            storage.putString(StorageKeys.ROOT_TETHER_MODE_ACTIVE, tetherModeRequested)
            storage.putString(StorageKeys.ROOT_SUBMODE_ACTIVE, submode.storageValue)

            when (submode) {
                Submode.Tun -> applyTetherRules(storage, tproxyForTether)
                Submode.Tproxy -> applyTproxyRules(storage)
            }

            ProxyServiceBridge.updateState(
                ProxyServiceStatus(
                    ProxyState.Running,
                    secret = runner.secret,
                    externalController = extCtl,
                    tunMode = tunMode,
                    startTime = startTime,
                    mihomoPid = runner.pid
                )
            )
            dynamicNotification.startOrFallbackStatic(storage, tunMode)
            storage.putString(StorageKeys.SERVICE_WAS_RUNNING, "true")
            AppLogger.info(TAG, "Proxy running (ROOT $submode)")

            val workDir = if (subscriptionId != null) ProfileFileOps.getRuntimeDir(
                this@StellibertyRootService,
                subscriptionId
            ) else ConfigGenerator.getWorkDir(this@StellibertyRootService)
            startProcessMonitor(workDir)
        }
    }

    private suspend fun applyTproxyRules(storage: PlatformStorage) {
        val appUid = applicationInfo.uid
        val ifaces = RootTetherHijacker.parseInterfaces(
            storage.getString(StorageKeys.ROOT_TETHER_IFACES, RootTetherHijacker.DEFAULT_IFACES)
        )
        val proxyMode = AppProxyMode.parse(storage.getString(StorageKeys.APP_PROXY_MODE, ""))
        val packages = storage.getStringSet(StorageKeys.APP_PROXY_PACKAGES, emptySet())
        val selectedUids = if (packages.isEmpty()) {
            emptySet()
        } else {
            AppListProvider(this@StellibertyRootService).resolveUids(packages)
        }
        val ipv6Enabled = storage.getString(StorageKeys.VPN_ALLOW_IPV6, "false") == "true"
        RootTproxyApplier.apply(appUid, selectedUids, proxyMode, ifaces, ipv6Enabled)
    }

    // 重连时先看规则是否还齐全，齐全就不重装，省掉一次拆除加重装。
    // 只有用户打开了强制重装（对付被第三方模块清掉规则的情况）才无条件重来。
    private fun shouldReapplyOnAttach(storage: PlatformStorage, probe: () -> Boolean): Boolean {
        val force = storage.getString(StorageKeys.ROOT_ATTACH_FORCE_REAPPLY, "false") == "true"
        if (force) {
            AppLogger.info(TAG, "attach: force re-apply (ROOT_ATTACH_FORCE_REAPPLY=true)")
            return true
        }
        val present = probe()
        if (present) {
            AppLogger.info(TAG, "attach: probe found existing rules, skip re-apply")
        } else {
            AppLogger.info(TAG, "attach: probe found rules missing, will re-apply")
        }
        return !present
    }

    private suspend fun teardownAllRootRules() {
        withContext(NonCancellable) {
            RootTetherHijacker.teardown()
            RootTproxyApplier.teardown()
        }
    }

    private fun startProcessMonitor(workDir: File) {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            delay(10_000)
            while (runner.isRunning) {
                delay(5_000)
            }
            val logContent = RootHelper.readLogFile(File(workDir, "mihomo.log").absolutePath)
            val errorMsg = if (logContent.isNotBlank()) {
                getString(R.string.error_mihomo_start_failed, logContent)
            } else {
                getString(R.string.error_mihomo_exited)
            }
            AppLogger.error(TAG, "mihomo process died unexpectedly (ROOT): $errorMsg")
            val storage = PlatformStorage(this@StellibertyRootService)
            val runningSubscriptionId = storage.getString(StorageKeys.ROOT_ACTIVE_SUBSCRIPTION_ID, "").ifEmpty { null }
            teardownAllRootRules()
            clearPersistedState(storage)
            storage.putString(StorageKeys.SERVICE_WAS_RUNNING, "false")
            runningSubscriptionId?.let { ProfileFileOps.cleanupRootRuntime(this@StellibertyRootService, it) }
            ProxyServiceBridge.updateState(ProxyServiceStatus(ProxyState.Error, errorMessage = errorMsg, tunMode = currentSubmode.tunMode))
            dynamicNotification.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun persistState(storage: PlatformStorage, secret: String, startTime: Long, subscriptionId: String?) {
        storage.putString(StorageKeys.ROOT_MIHOMO_PID, runner.pid.toString())
        storage.putString(StorageKeys.ROOT_MIHOMO_SECRET, secret)
        storage.putString(StorageKeys.ROOT_START_TIME, startTime.toString())
        storage.putString(StorageKeys.ROOT_ACTIVE_SUBSCRIPTION_ID, subscriptionId ?: "")
        BootSession.mark(this, storage)
    }

    private fun clearPersistedState(storage: PlatformStorage) {
        storage.putString(StorageKeys.ROOT_MIHOMO_PID, "")
        storage.putString(StorageKeys.ROOT_MIHOMO_SECRET, "")
        storage.putString(StorageKeys.ROOT_START_TIME, "")
        storage.putString(StorageKeys.ROOT_ACTIVE_SUBSCRIPTION_ID, "")
        BootSession.clear(storage)
        storage.putString(StorageKeys.ROOT_TETHER_MODE_ACTIVE, "")
        storage.putString(StorageKeys.ROOT_SUBMODE_ACTIVE, "")
    }

    private fun applyTetherRules(storage: PlatformStorage, tproxySupported: Boolean) {
        val mode = RootTetherHijacker.Mode.from(
            storage.getString(StorageKeys.ROOT_TETHER_MODE, RootTetherHijacker.Mode.BYPASS.storageValue)
        )
        val ifaces = RootTetherHijacker.parseInterfaces(
            storage.getString(StorageKeys.ROOT_TETHER_IFACES, RootTetherHijacker.DEFAULT_IFACES)
        )
        RootTetherHijacker.apply(mode, ifaces, tproxySupported)
    }

    private fun applyTetherRulesActive(storage: PlatformStorage, tproxySupported: Boolean) {
        val activeStr = storage.getString(StorageKeys.ROOT_TETHER_MODE_ACTIVE, "")
            .ifEmpty { storage.getString(StorageKeys.ROOT_TETHER_MODE, RootTetherHijacker.Mode.BYPASS.storageValue) }
        val mode = RootTetherHijacker.Mode.from(activeStr)
        val ifaces = RootTetherHijacker.parseInterfaces(
            storage.getString(StorageKeys.ROOT_TETHER_IFACES, RootTetherHijacker.DEFAULT_IFACES)
        )
        RootTetherHijacker.apply(mode, ifaces, tproxySupported)
    }

    private fun restartProxy(subscriptionId: String?) {
        AppLogger.info(TAG, "Restarting proxy (ROOT)...")
        monitorJob?.cancel()
        ProxyServiceBridge.updateState(ProxyServiceStatus(ProxyState.Stopping, tunMode = currentSubmode.tunMode))
        dynamicNotification.stop()
        scope.launch {
            startJob?.cancelAndJoin()
            val storage = PlatformStorage(this@StellibertyRootService)
            val runningSubscriptionId = storage.getString(StorageKeys.ROOT_ACTIVE_SUBSCRIPTION_ID, "").ifEmpty { null }
            runner.stop()
            teardownAllRootRules()
            runningSubscriptionId?.let { ProfileFileOps.cleanupRootRuntime(this@StellibertyRootService, it) }
            clearPersistedState(storage)
            withContext(Dispatchers.Main) {
                startProxy(subscriptionId)
            }
        }
    }

    private fun stopProxy() {
        AppLogger.info(TAG, "Stopping proxy (ROOT)...")
        monitorJob?.cancel()
        ProxyServiceBridge.updateState(ProxyServiceStatus(ProxyState.Stopping, tunMode = currentSubmode.tunMode))
        dynamicNotification.stop()
        scope.launch {
            startJob?.cancelAndJoin()
            val storage = PlatformStorage(this@StellibertyRootService)
            val runningSubscriptionId = storage.getString(StorageKeys.ROOT_ACTIVE_SUBSCRIPTION_ID, "").ifEmpty { null }
            runner.stop()
            teardownAllRootRules()
            runningSubscriptionId?.let { ProfileFileOps.cleanupRootRuntime(this@StellibertyRootService, it) }
            clearPersistedState(storage)
            storage.putString(StorageKeys.SERVICE_WAS_RUNNING, "false")
            ProxyServiceBridge.markStopped(currentSubmode.tunMode)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        notificationRefreshJob?.cancel()
        monitorJob?.cancel()
        dynamicNotification.stop()
        ProxyServiceBridge.markStoppedUnlessError(currentSubmode.tunMode)
        scope.cancel()
        AppLogger.info(TAG, "StellibertyRootService destroyed")
        super.onDestroy()
    }

    companion object {
        private const val TAG = "StellibertyRootService"
        const val ACTION_START = "com.stelliberty.android.ROOT_START"
        const val ACTION_STOP = "com.stelliberty.android.ROOT_STOP"
        const val ACTION_RESTART = "com.stelliberty.android.ROOT_RESTART"
        const val EXTRA_SUBMODE = "submode"
        const val EXTRA_ATTACH_ONLY = "attach_only"

        fun submodeExtra(mode: TunMode): String? =
            Submode.entries.firstOrNull { it.tunMode == mode }?.storageValue
    }
}
