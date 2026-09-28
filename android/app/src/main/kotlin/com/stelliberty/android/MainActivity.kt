package com.stelliberty.android

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.stelliberty.android.data.api.MihomoConnectionManager
import com.stelliberty.android.data.repository.ProfileProcessor
import com.stelliberty.android.data.repository.SubscriptionProxyResolver
import com.stelliberty.android.platform.AndroidWifiPolicy
import com.stelliberty.android.platform.BootStartManager
import com.stelliberty.android.platform.FilePicker
import com.stelliberty.android.platform.PlatformStorage
import com.stelliberty.android.platform.ProxyServiceBridge
import com.stelliberty.android.platform.ProxyServiceController
import com.stelliberty.android.platform.StorageKeys
import com.stelliberty.android.platform.TunMode
import com.stelliberty.android.platform.WifiPolicyController
import com.stelliberty.android.service.RootHelper
import com.stelliberty.android.ui.platform.IconDiskCache
import com.stelliberty.android.ui.platform.IconLoader
import com.stelliberty.android.ui.theme.ThemeColorMode
import com.stelliberty.android.ui.theme.ThemeConfig
import com.stelliberty.android.ui.theme.readThemeConfig
import com.stelliberty.android.ui.theme.resolveIsDark
import com.stelliberty.android.viewmodel.AppProxyViewModel
import com.stelliberty.android.viewmodel.ConnectionViewModel
import com.stelliberty.android.viewmodel.DnsQueryViewModel
import com.stelliberty.android.viewmodel.ExternalControlViewModel
import com.stelliberty.android.viewmodel.HomeViewModel
import com.stelliberty.android.viewmodel.LogViewModel
import com.stelliberty.android.viewmodel.MetaSettingsViewModel
import com.stelliberty.android.viewmodel.NetworkSettingsViewModel
import com.stelliberty.android.viewmodel.ProviderViewModel
import com.stelliberty.android.viewmodel.ProxyViewModel
import com.stelliberty.android.viewmodel.SubscriptionViewModel
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanCustomCode
import io.github.g00fy2.quickie.config.BarcodeFormat
import io.github.g00fy2.quickie.config.ScannerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get

private const val STATE_DEEPLINK_NONCE = "deeplink_nonce"

class MainActivity : ComponentActivity() {

    private lateinit var serviceController: ProxyServiceController
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var subscriptionViewModel: SubscriptionViewModel
    private lateinit var proxyViewModel: ProxyViewModel
    private lateinit var logViewModel: LogViewModel
    private lateinit var providerViewModel: ProviderViewModel
    private lateinit var connectionViewModel: ConnectionViewModel
    private lateinit var dnsQueryViewModel: DnsQueryViewModel
    private lateinit var networkSettingsViewModel: NetworkSettingsViewModel
    private lateinit var metaSettingsViewModel: MetaSettingsViewModel
    private lateinit var externalControlViewModel: ExternalControlViewModel
    private lateinit var appProxyViewModel: AppProxyViewModel
    private lateinit var filePicker: FilePicker
    private lateinit var scanQrLauncher: ActivityResultLauncher<ScannerConfig>
    private lateinit var vpnPermissionLauncher: ActivityResultLauncher<Intent>
    private lateinit var wifiPermissionLauncher: ActivityResultLauncher<Array<String>>
    private var qrResultCallback: ((String?) -> Unit)? = null
    private var wifiPermissionCallback: ((Boolean) -> Unit)? = null
    private var latestThemeConfig: ThemeConfig? = null
    private var contentReady = false

    private val pendingDeepLinkImport = mutableStateOf<DeepLinkImportRequest?>(null)
    private var consumedDeepLinkNonce: String? = null
    private val scannerConfig: ScannerConfig by lazy {
        ScannerConfig.build {
            setBarcodeFormats(listOf(BarcodeFormat.FORMAT_QR_CODE))
            setOverlayStringRes(R.string.qr_scanner_overlay)
            setShowTorchToggle(true)
            setShowCloseButton(true)
            setKeepScreenOn(true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { !contentReady }

        consumedDeepLinkNonce = savedInstanceState?.getString(STATE_DEEPLINK_NONCE)
        acceptDeepLinkImport(intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
            }
        }

        scanQrLauncher = registerForActivityResult(ScanCustomCode()) { result ->
            val url: String? = when (result) {
                is QRResult.QRSuccess -> {
                    val raw = result.content.rawValue
                    when {
                        raw == null -> {
                            showQrToast(R.string.qr_unsupported)
                            null
                        }

                        raw.startsWith("http://") || raw.startsWith("https://") -> raw
                        else -> {
                            showQrToast(R.string.qr_invalid_subscription)
                            null
                        }
                    }
                }

                is QRResult.QRMissingPermission -> {
                    showQrToast(R.string.qr_permission_denied)
                    null
                }

                is QRResult.QRError -> {
                    showQrToast(R.string.qr_scan_failed)
                    null
                }

                is QRResult.QRUserCanceled -> null
            }
            qrResultCallback?.invoke(url)
            qrResultCallback = null
        }

        val storage: PlatformStorage = get()
        val initialThemeConfig = readThemeConfig(storage)
        updateEdgeToEdge(initialThemeConfig)
        val profileProcessor: ProfileProcessor = get()
        lifecycleScope.launch { profileProcessor.cleanupResidual() }
        IconDiskCache.init(this)
        serviceController = get()
        vpnPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                homeViewModel.startProxy()
            }
        }
        wifiPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            val granted = result.isNotEmpty() && result.values.all { it }
            wifiPermissionCallback?.invoke(granted)
            wifiPermissionCallback = null
        }
        serviceController.setVpnPermissionLauncher(vpnPermissionLauncher)
        filePicker = FilePicker(this)
        val wifiPolicyController: WifiPolicyController = get()
        if (storage.getString(StorageKeys.WIFI_POLICY_ENABLED, "false") == "true" &&
            wifiPolicyController.hasRequiredPermission()
        ) {
            wifiPolicyController.startMonitor()
        }
        val iconProxyResolver: SubscriptionProxyResolver = get()
        IconLoader.setProxyResolver {
            iconProxyResolver.resolve()
        }

        logViewModel = get()
        providerViewModel = get()
        connectionViewModel = get()
        dnsQueryViewModel = get()
        networkSettingsViewModel = get()
        metaSettingsViewModel = get()
        externalControlViewModel = get()
        appProxyViewModel = get()
        subscriptionViewModel = get()
        subscriptionViewModel.runStartupUpdates()
        proxyViewModel = get()
        homeViewModel = get()

        val connectionManager: MihomoConnectionManager = get()
        lifecycleScope.launch {
            connectionManager.repository.collect { repo ->
                proxyViewModel.setRepository(repo)
                logViewModel.setRepository(repo)
                providerViewModel.setRepository(repo)
                connectionViewModel.setRepository(repo)
                dnsQueryViewModel.setRepository(repo)
            }
        }

        val hasRootState = mutableStateOf(
            storage.getString(StorageKeys.HAS_ROOT, "false") == "true"
        )
        lifecycleScope.launch(Dispatchers.IO) {
            val hasRoot = RootHelper.hasRootAccess()
            storage.putString(StorageKeys.HAS_ROOT, if (hasRoot) "true" else "false")
            if (!hasRoot) {
                val current = TunMode.fromStorage(storage.getString(StorageKeys.TUN_MODE, TunMode.Vpn.storageValue))
                if (current != TunMode.Vpn) {
                    storage.putString(StorageKeys.TUN_MODE, TunMode.Vpn.storageValue)
                    // 这次回退发生在启动之后，桥里已经填过 ROOT，不改的话首页会显示一个已经用不上的模式。
                    ProxyServiceBridge.setSelectedTunMode(TunMode.Vpn)
                }
            }
            hasRootState.value = hasRoot
        }

        if (storage.getString(StorageKeys.HIDE_TASK_CARD, "false") == "true") {
            setExcludeFromRecents(true)
        }

        setContent {
            var themeConfig by remember { mutableStateOf(initialThemeConfig) }
            SideEffect {
                updateEdgeToEdge(themeConfig)
            }
            App(
                themeConfig = themeConfig,
                onThemeConfigChange = { themeConfig = it },
                homeViewModel = homeViewModel,
                subscriptionViewModel = subscriptionViewModel,
                proxyViewModel = proxyViewModel,
                logViewModel = logViewModel,
                providerViewModel = providerViewModel,
                connectionViewModel = connectionViewModel,
                dnsQueryViewModel = dnsQueryViewModel,
                networkSettingsViewModel = networkSettingsViewModel,
                metaSettingsViewModel = metaSettingsViewModel,
                externalControlViewModel = externalControlViewModel,
                appProxyViewModel = appProxyViewModel,
                filePicker = filePicker,
                storage = storage,
                bootStartManager = get<BootStartManager>(),
                onScanQR = { callback ->
                    qrResultCallback = callback
                    scanQrLauncher.launch(scannerConfig)
                },
                wifiPolicyController = wifiPolicyController,
                onRequestWifiPermission = { callback ->
                    requestWifiPolicyPermission(wifiPolicyController, callback)
                },
                hasRootPermission = hasRootState.value,
                onPredictiveBackChange = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    { enabled ->
                        StellibertyApplication.setEnableOnBackInvokedCallback(applicationInfo, enabled)
                        recreateWithoutTransition()
                    }
                } else null,
                onHideTaskCardChange = { enabled ->
                    setExcludeFromRecents(enabled)
                },
                deepLinkImport = pendingDeepLinkImport.value,
                onDeepLinkImportConsumed = { pendingDeepLinkImport.value = null },
                backupViewModel = get(),
                overrideViewModel = get(),
                chainProxyViewModel = get(),
                ruleOverrideViewModel = get(),
                onRestartApp = { restartApplication() },
            )
            SideEffect { contentReady = true }
        }
    }

    private fun restartApplication() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finishAffinity()
        Runtime.getRuntime().exit(0)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        acceptDeepLinkImport(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        consumedDeepLinkNonce?.let { outState.putString(STATE_DEEPLINK_NONCE, it) }
    }

    private fun acceptDeepLinkImport(intent: Intent?) {
        if (intent?.action != ExternalImportActivity.ACTION_IMPORT_SUBSCRIPTION) return
        val nonce = intent.getStringExtra(ExternalImportActivity.EXTRA_IMPORT_NONCE) ?: return
        if (nonce == consumedDeepLinkNonce) return
        val url = intent.getStringExtra(ExternalImportActivity.EXTRA_IMPORT_URL)
        if (url.isNullOrBlank()) return
        consumedDeepLinkNonce = nonce
        pendingDeepLinkImport.value = DeepLinkImportRequest(
            url = url,
            name = intent.getStringExtra(ExternalImportActivity.EXTRA_IMPORT_NAME).orEmpty(),
            intervalMinutes = intent.getLongExtra(ExternalImportActivity.EXTRA_IMPORT_INTERVAL_MINUTES, 0L),
        )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) latestThemeConfig?.let(::updateEdgeToEdge)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        latestThemeConfig?.let(::updateEdgeToEdge)
    }

    private fun updateEdgeToEdge(themeConfig: ThemeConfig) {
        latestThemeConfig = themeConfig
        val isDark = themeConfig.resolveIsDark(
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES,
        )
        val systemBarStyle = themeConfig.systemBarStyle()
        enableEdgeToEdge(
            statusBarStyle = systemBarStyle,
            navigationBarStyle = systemBarStyle,
        )
        enforceSystemBarsAppearance(isDark)
        window.decorView.post {
            enforceSystemBarsAppearance(isDark)
        }
    }

    private fun ThemeConfig.systemBarStyle(): SystemBarStyle = when (colorMode) {
        ThemeColorMode.Light -> SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        ThemeColorMode.Dark -> SystemBarStyle.dark(Color.TRANSPARENT)
        ThemeColorMode.System -> SystemBarStyle.auto(
            lightScrim = Color.TRANSPARENT,
            darkScrim = Color.TRANSPARENT,
            detectDarkMode = { resources ->
                (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                        Configuration.UI_MODE_NIGHT_YES
            },
        )
    }

    private fun enforceSystemBarsAppearance(isDark: Boolean) {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
    }

    @Suppress("DEPRECATION")
    private fun recreateWithoutTransition() {
        overridePendingTransition(0, 0)
        recreate()
        overridePendingTransition(0, 0)
    }

    private fun requestWifiPolicyPermission(
        controller: WifiPolicyController,
        callback: (Boolean) -> Unit,
    ) {
        if (controller.hasRequiredPermission()) {
            callback(true)
            return
        }
        wifiPermissionCallback = callback
        wifiPermissionLauncher.launch(AndroidWifiPolicy.requiredPermissions())
    }

    override fun onResume() {
        super.onResume()
        latestThemeConfig?.let(::updateEdgeToEdge)
        serviceController.verifyAndSyncState()
    }

    override fun onStart() {
        super.onStart()
        homeViewModel.setUiVisible(true)
    }

    override fun onDestroy() {
        serviceController.setVpnPermissionLauncher(null)
        super.onDestroy()
    }

    override fun onStop() {
        super.onStop()
        homeViewModel.setUiVisible(false)
    }

    private fun showQrToast(@StringRes resId: Int) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
    }

    private fun setExcludeFromRecents(exclude: Boolean) {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val currentTaskId = taskId
        am.appTasks
            .firstOrNull { task ->
                val info = task.taskInfo ?: return@firstOrNull false
                info.taskId == currentTaskId
            }
            ?.setExcludeFromRecents(exclude)
    }
}
