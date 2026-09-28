package com.stelliberty.android.service

import android.annotation.SuppressLint
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import com.stelliberty.android.R
import com.stelliberty.android.StellibertyApplication
import com.stelliberty.android.data.repository.OverrideJsonStore
import com.stelliberty.android.data.store.ProfileTransformWriter
import com.stelliberty.android.data.store.SubscriptionStore
import com.stelliberty.android.domain.model.AppProxyMode
import com.stelliberty.android.domain.model.resolveExternalController
import com.stelliberty.android.platform.PlatformStorage
import com.stelliberty.android.platform.ProxyServiceBridge
import com.stelliberty.android.platform.ProxyServiceController
import com.stelliberty.android.platform.ProxyServiceStatus
import com.stelliberty.android.platform.ProxyState
import com.stelliberty.android.platform.StorageKeys
import com.stelliberty.android.platform.TunMode
import com.stelliberty.android.util.AppLogger
import java.io.File
import java.io.FileDescriptor
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

@SuppressLint("VpnServicePolicy")
class StellibertyTunService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runner by lazy { MihomoRunner(this) }
    private val dynamicNotification by lazy {
        DynamicNotificationManager(this, scope, StellibertyApplication.instance.connectionManager)
    }

    private val overrideStore: OverrideJsonStore by inject()
    private val subscriptionStore: SubscriptionStore by inject()
    private val transformWriter: ProfileTransformWriter by inject()
    private var tunFd: Int = -1
    private var monitorJob: Job? = null
    private var notificationRefreshJob: Job? = null

    @Volatile
    private var startJob: Job? = null

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
                    tunMode = TunMode.Vpn,
                )
            )
            stopSelf()
            return
        }
        notificationRefreshJob = scope.launch {
            ProxyServiceBridge.notificationRefresh.collect {
                val state = ProxyServiceBridge.state.value
                if (state.state == ProxyState.Running && state.tunMode == TunMode.Vpn) {
                    dynamicNotification.stop()
                    dynamicNotification.startOrFallbackStatic(
                        PlatformStorage(this@StellibertyTunService),
                    )
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val subscriptionId = intent.getStringExtra(ProxyServiceController.EXTRA_SUBSCRIPTION_ID)
                startProxy(subscriptionId)
            }

            ACTION_STOP -> stopProxy()
            ACTION_RESTART -> {
                val subscriptionId = intent.getStringExtra(ProxyServiceController.EXTRA_SUBSCRIPTION_ID)
                restartProxy(subscriptionId)
            }
        }
        return START_STICKY
    }

    // 开机自启和「打开应用自动连接」会在几百毫秒内各发一次启动，正在启动时再来一律忽略；
    // 停止和重启之前也必须先等这条协程收尾。
    private fun startProxy(subscriptionId: String? = null) {
        if (startJob?.isActive == true) {
            AppLogger.info(TAG, "Start already in progress, ignoring duplicate START")
            return
        }
        startJob = scope.launch {
            AppLogger.info(TAG, "Starting proxy, subscription: $subscriptionId")
            // 带了订阅编号就必须能读到它的配置，读不到说明文件坏了或没落盘，属于错误。
            // 没带编号是「一条订阅都没有」，写一份内置配置照常启动，流量直连。
            if (subscriptionId == null) {
                ConfigGenerator.writeFallbackConfig(this@StellibertyTunService)
            } else if (!ProfileFileOps.hasValidConfig(this@StellibertyTunService, subscriptionId)) {
                AppLogger.error(TAG, "No valid subscription config (id=$subscriptionId), aborting start")
                ProxyServiceBridge.updateState(
                    ProxyServiceStatus(
                        ProxyState.Error,
                        errorMessage = getString(R.string.error_no_active_profile),
                        tunMode = TunMode.Vpn,
                    )
                )
                stopSelf()
                return@launch
            }
            ProxyServiceBridge.updateState(ProxyServiceStatus(ProxyState.Starting, tunMode = TunMode.Vpn))

            if (runner.isRunning) {
                runner.stop()
            }

            val storage = PlatformStorage(this@StellibertyTunService)
            val hadRootPid = storage.getString(StorageKeys.ROOT_MIHOMO_PID, "").isNotEmpty()
            val hasRoot = storage.getString(StorageKeys.HAS_ROOT, "false") == "true"
            if (hadRootPid || hasRoot) {
                val residualTun = storage.getString(StorageKeys.ROOT_TUN_DEVICE, RuntimeOverrideBuilder.DEFAULT_TUN_DEVICE)
                RootHelper.cleanupOrphanedMihomo(tunDevice = residualTun)
                ProfileFileOps.cleanupAllRootRuntime(this@StellibertyTunService)
                storage.putString(StorageKeys.ROOT_MIHOMO_PID, "")
                storage.putString(StorageKeys.ROOT_MIHOMO_SECRET, "")
                storage.putString(StorageKeys.ROOT_ACTIVE_SUBSCRIPTION_ID, "")
            }

            val userOverride = overrideStore.load()

            val fd = try {
                Builder().apply {
                    addAddress(TUN_GATEWAY, TUN_SUBNET_PREFIX)
                    setMtu(RuntimeOverrideBuilder.VPN_TUN_MTU)
                    setSession("Stelliberty")
                    setBlocking(false)

                    val bypassPrivate = storage.getString(StorageKeys.VPN_BYPASS_PRIVATE_NETWORK, "true") == "true"
                    if (bypassPrivate) {
                        resources.getStringArray(R.array.bypass_private_route).forEach { cidr ->
                            val parts = cidr.split("/")
                            addRoute(parts[0], parts[1].toInt())
                        }
                        addRoute(TUN_DNS, 32)
                    } else {
                        addRoute("0.0.0.0", 0)
                    }

                    val allowIpv6 = storage.getString(StorageKeys.VPN_ALLOW_IPV6, "false") == "true"
                    if (allowIpv6) {
                        addAddress(TUN_GATEWAY6, TUN_SUBNET_PREFIX6)
                        if (bypassPrivate) {
                            resources.getStringArray(R.array.bypass_private_route6).forEach { cidr ->
                                val parts = cidr.split("/")
                                addRoute(parts[0], parts[1].toInt())
                            }
                            addRoute(TUN_DNS6, 128)
                        } else {
                            addRoute("::", 0)
                        }
                    }

                    val dnsHijacking = storage.getString(StorageKeys.VPN_DNS_HIJACKING, "true") == "true"
                    if (dnsHijacking) {
                        addDnsServer(TUN_DNS)
                        if (allowIpv6) addDnsServer(TUN_DNS6)
                    }

                    val allowBypass = storage.getString(StorageKeys.VPN_ALLOW_BYPASS, "true") == "true"
                    if (allowBypass) {
                        allowBypass()
                    }

                    val proxyMode = AppProxyMode.parse(storage.getString(StorageKeys.APP_PROXY_MODE, ""))
                    val packages = storage.getStringSet(StorageKeys.APP_PROXY_PACKAGES, emptySet())

                    // 自身包名一律排除，否则子进程的 HTTP 请求会被自己代理住、永久卡死。
                    // 单个包名添加失败多是用户选中后又卸载了，跳过即可。
                    when (proxyMode) {
                        AppProxyMode.AllowSelected -> {
                            val filtered = packages.filter { it != packageName }
                            if (filtered.isEmpty()) {
                                addDisallowedApplication(packageName)
                            } else {
                                filtered.forEach { pkg ->
                                    runCatching { addAllowedApplication(pkg) }
                                }
                            }
                        }

                        AppProxyMode.DenySelected -> {
                            addDisallowedApplication(packageName)
                            packages.forEach { pkg ->
                                if (pkg != packageName) {
                                    runCatching { addDisallowedApplication(pkg) }
                                }
                            }
                        }

                        AppProxyMode.AllowAll -> addDisallowedApplication(packageName)
                    }

                    setMetered(false)

                    val systemProxy = storage.getString(StorageKeys.VPN_SYSTEM_PROXY, "true") == "true"
                    if (systemProxy) {
                        val port = userOverride.mixedPort ?: 7890
                        setHttpProxy(
                            android.net.ProxyInfo.buildDirectProxy(
                                "127.0.0.1",
                                port,
                                listOf(
                                    "localhost", "*.local", "127.*", "10.*", "172.16.*",
                                    "172.17.*", "172.18.*", "172.19.*", "172.20.*",
                                    "172.21.*", "172.22.*", "172.23.*", "172.24.*",
                                    "172.25.*", "172.26.*", "172.27.*", "172.28.*",
                                    "172.29.*", "172.30.*", "172.31.*", "192.168.*"
                                ),
                            )
                        )
                    }
                }.establish()?.detachFd()
            } catch (e: Exception) {
                AppLogger.error(TAG, "Failed to establish VPN", e)
                ProxyServiceBridge.updateState(
                    ProxyServiceStatus(ProxyState.Error, errorMessage = getString(R.string.error_vpn_failed, e.message ?: ""))
                )
                stopSelf()
                return@launch
            }

            if (fd == null || fd < 0) {
                AppLogger.error(TAG, "VPN establish returned null (permission denied?)")
                ProxyServiceBridge.updateState(
                    ProxyServiceStatus(ProxyState.Error, errorMessage = getString(R.string.error_vpn_denied))
                )
                stopSelf()
                return@launch
            }

            tunFd = fd
            AppLogger.info(TAG, "VPN established, fd=$fd")

            try {
                val pfd = ParcelFileDescriptor.adoptFd(fd)
                val flags = Os.fcntlInt(pfd.fileDescriptor, OsConstants.F_GETFD, 0)
                AppLogger.info(TAG, "fd=$fd flags before: $flags")
                Os.fcntlInt(pfd.fileDescriptor, OsConstants.F_SETFD, flags and OsConstants.FD_CLOEXEC.inv())
                val flagsAfter = Os.fcntlInt(pfd.fileDescriptor, OsConstants.F_GETFD, 0)
                AppLogger.info(TAG, "fd=$fd flags after: $flagsAfter")
                pfd.detachFd()
            } catch (e: Exception) {
                AppLogger.error(TAG, "Failed to clear O_CLOEXEC on fd=$fd, aborting: $e")
                closeTunFd()
                ProxyServiceBridge.updateState(
                    ProxyServiceStatus(
                        ProxyState.Error,
                        errorMessage = getString(R.string.error_fd_setup_failed, e.message ?: e.javaClass.simpleName),
                        tunMode = TunMode.Vpn,
                    )
                )
                stopSelf()
                return@launch
            }

            val secret = ConfigGenerator.resolveSecret(this@StellibertyTunService, userOverride, subscriptionId)
            val extCtl = userOverride.resolveExternalController()
            val subMixedPort = subscriptionId?.let {
                ConfigGenerator.readSubscriptionMixedPort(this@StellibertyTunService, it)
            }
            val overrideFile = RuntimeOverrideBuilder.buildAndWriteForRun(
                context = this@StellibertyTunService,
                userOverride = userOverride,
                tunFd = fd,
                tunMode = TunMode.Vpn,
                subscriptionUpdateViaProxy = subscriptionStore.anyUpdatesViaProxy(),
                subscriptionMixedPort = subMixedPort,
            )

            val subscription = subscriptionId?.let(subscriptionStore::findImported)
            val success = runner.start(
                subscriptionId = subscriptionId,
                useRoot = false,
                overrideJsonPath = overrideFile.absolutePath,
                secret = secret,
                externalController = extCtl,
                ageSecretKey = subscription?.ageSecretKey.orEmpty(),
                transformPath = transformWriter.writeRuntime(subscription),
            )
            if (!success) {
                val errorMsg = runner.errorMessage.ifBlank { getString(R.string.error_start_failed) }
                AppLogger.error(TAG, "Failed to start mihomo: $errorMsg")
                ProxyServiceBridge.updateState(
                    ProxyServiceStatus(ProxyState.Error, errorMessage = errorMsg)
                )
                closeTunFd()
                stopSelf()
                return@launch
            }

            ProxyServiceBridge.updateState(
                ProxyServiceStatus(
                    ProxyState.Running,
                    secret = runner.secret,
                    externalController = extCtl,
                    tunMode = TunMode.Vpn,
                    startTime = Clock.System.now().toEpochMilliseconds(),
                    mihomoPid = runner.pid
                )
            )

            dynamicNotification.startOrFallbackStatic(storage)
            PlatformStorage(this@StellibertyTunService).putString(StorageKeys.SERVICE_WAS_RUNNING, "true")
            AppLogger.info(TAG, "Proxy running, fd=$fd")

            val monitorWorkDir = if (subscriptionId != null) {
                ProfileFileOps.getSubscriptionDir(this@StellibertyTunService, subscriptionId)
            } else {
                ConfigGenerator.getWorkDir(this@StellibertyTunService)
            }
            startProcessMonitor(monitorWorkDir)
        }
    }

    @SuppressLint("StringFormatInvalid")
    private fun startProcessMonitor(workDir: File) {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            delay(10_000)
            while (runner.isRunning) {
                delay(5_000)
            }
            val logContent = File(workDir, "mihomo.log").readLastLines(DEATH_LOG_LINES)
            val errorMsg = if (logContent.isNotBlank()) {
                getString(R.string.error_mihomo_start_failed, logContent)
            } else {
                getString(R.string.error_mihomo_exited)
            }
            AppLogger.error(TAG, "mihomo process died unexpectedly: $errorMsg")
            ProxyServiceBridge.updateState(ProxyServiceStatus(ProxyState.Error, errorMessage = errorMsg))
            closeTunFd()
            PlatformStorage(this@StellibertyTunService).putString(StorageKeys.SERVICE_WAS_RUNNING, "false")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun restartProxy(subscriptionId: String?) {
        AppLogger.info(TAG, "Restarting proxy...")
        monitorJob?.cancel()
        ProxyServiceBridge.updateState(ProxyServiceStatus(ProxyState.Stopping, tunMode = TunMode.Vpn))
        dynamicNotification.stop()
        scope.launch {
            startJob?.cancelAndJoin()
            runner.stop()
            closeTunFd()
            withContext(Dispatchers.Main) {
                startProxy(subscriptionId)
            }
        }
    }

    private fun stopProxy() {
        AppLogger.info(TAG, "Stopping proxy...")
        monitorJob?.cancel()
        ProxyServiceBridge.updateState(ProxyServiceStatus(ProxyState.Stopping, tunMode = TunMode.Vpn))
        dynamicNotification.stop()
        scope.launch {
            startJob?.cancelAndJoin()
            runner.stop()
            closeTunFd()
            PlatformStorage(this@StellibertyTunService).putString(StorageKeys.SERVICE_WAS_RUNNING, "false")
            ProxyServiceBridge.markStopped(TunMode.Vpn)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    // 清除 TUN 描述符上的 close-on-exec 标记失败要当成致命错误：子进程拿不到这个描述符，
    // mihomo 会创建不出网卡，而它只写一条日志、不退出，界面就会停在「已连接」但完全没网。
    @SuppressLint("DiscouragedPrivateApi")
    private fun closeTunFd() {
        if (tunFd >= 0) {
            try {
                val fileDescriptor = FileDescriptor()
                val field = FileDescriptor::class.java.getDeclaredField("descriptor")
                field.isAccessible = true
                field.setInt(fileDescriptor, tunFd)
                Os.close(fileDescriptor)
                AppLogger.info(TAG, "Closed tun fd=$tunFd")
            } catch (e: Exception) {
                AppLogger.warn(TAG, "Failed to close tun fd=$tunFd: $e")
            }
            tunFd = -1
        }
    }

    override fun onDestroy() {
        notificationRefreshJob?.cancel()
        monitorJob?.cancel()
        dynamicNotification.stop()
        runner.stop()
        closeTunFd()
        PlatformStorage(this).putString(StorageKeys.SERVICE_WAS_RUNNING, "false")
        ProxyServiceBridge.markStoppedUnlessError(TunMode.Vpn)
        scope.cancel()
        AppLogger.info(TAG, "StellibertyTunService destroyed")
        super.onDestroy()
    }

    override fun onRevoke() {
        AppLogger.info(TAG, "VPN revoked by system")
        monitorJob?.cancel()
        ProxyServiceBridge.updateState(ProxyServiceStatus(ProxyState.Stopping, tunMode = TunMode.Vpn))
        dynamicNotification.stop()
        PlatformStorage(this).putString(StorageKeys.SERVICE_WAS_RUNNING, "false")
        scope.launch {
            startJob?.cancelAndJoin()
            runner.stop()
            closeTunFd()
            ProxyServiceBridge.markStopped(TunMode.Vpn)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    companion object {
        private const val TAG = "StellibertyTunService"

        private const val DEATH_LOG_LINES = 10
        const val ACTION_START = "com.stelliberty.android.START"
        const val ACTION_STOP = "com.stelliberty.android.STOP"
        const val ACTION_RESTART = "com.stelliberty.android.RESTART"

        private const val TUN_SUBNET_PREFIX = 30
        private const val TUN_GATEWAY = "198.18.0.1"
        private const val TUN_GATEWAY6 = "fdfe:dcba:9876::1"
        private const val TUN_SUBNET_PREFIX6 = 126
        private const val TUN_DNS = "198.18.0.2"
        private const val TUN_DNS6 = "fdfe:dcba:9876::2"
    }
}
