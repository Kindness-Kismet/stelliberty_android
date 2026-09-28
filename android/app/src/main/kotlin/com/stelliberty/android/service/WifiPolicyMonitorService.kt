package com.stelliberty.android.service

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.os.Build
import android.os.IBinder
import com.stelliberty.android.R
import com.stelliberty.android.platform.AndroidWifiPolicy
import com.stelliberty.android.platform.PlatformStorage
import com.stelliberty.android.platform.ProxyServiceBridge
import com.stelliberty.android.platform.ProxyServiceController
import com.stelliberty.android.platform.ProxyState
import com.stelliberty.android.platform.StorageKeys
import com.stelliberty.android.platform.WifiPolicyAction
import com.stelliberty.android.platform.WifiPolicyIntents
import com.stelliberty.android.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class WifiPolicyMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val storage by lazy { PlatformStorage(this) }
    private val controller: ProxyServiceController by inject()
    private val connectivity by lazy { getSystemService(ConnectivityManager::class.java) }

    private var networkCallbackRegistered = false
    private var evaluateJob: Job? = null
    private var pendingModeRestart: String? = null
    private var pendingModeNotificationResId: Int? = null

    @Volatile
    private var callbackWifi: Pair<Network, String?>? = null

    private inner class WifiNetworkCallback(flags: Int) : ConnectivityManager.NetworkCallback(flags) {

        override fun onAvailable(network: Network) = scheduleEvaluate()

        override fun onLost(network: Network) {
            if (callbackWifi?.first == network) callbackWifi = null
            scheduleEvaluate()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            callbackWifi = network to AndroidWifiPolicy.normalizeSsid((networkCapabilities.transportInfo as? WifiInfo)?.ssid)
            scheduleEvaluate()
        }
    }

    private val networkCallback: ConnectivityManager.NetworkCallback =
        WifiNetworkCallback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        if (!updateMonitorNotificationVisibility()) {
            stopSelf()
            return
        }
        registerNetworkCallback()
        scope.launch {
            ProxyServiceBridge.state.collect { status ->
                when (status.state) {
                    ProxyState.Running ->
                        if (!consumePendingModeRestart()) scheduleEvaluate(delayMs = 0)

                    ProxyState.Stopped, ProxyState.Error -> {
                        dropPendingModeRestart()
                        scheduleEvaluate(delayMs = 0)
                    }

                    ProxyState.Starting, ProxyState.Stopping -> Unit
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            WifiPolicyIntents.ACTION_STOP -> {
                storage.putString(StorageKeys.WIFI_POLICY_ENABLED, "false")
                scheduleEvaluate(delayMs = 0)
                return START_NOT_STICKY
            }

            WifiPolicyIntents.ACTION_START,
            WifiPolicyIntents.ACTION_EVALUATE,
            null -> {
                if (!updateMonitorNotificationVisibility()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                scheduleEvaluate(delayMs = 0)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterNetworkCallback()
        evaluateJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun registerNetworkCallback() {
        if (networkCallbackRegistered) return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        runCatching {
            connectivity?.registerNetworkCallback(request, networkCallback)
            networkCallbackRegistered = true
        }.onFailure { AppLogger.warn(TAG, "registerNetworkCallback failed", it) }
    }

    private fun unregisterNetworkCallback() {
        if (!networkCallbackRegistered) return
        runCatching { connectivity?.unregisterNetworkCallback(networkCallback) }
        networkCallbackRegistered = false
    }

    private fun scheduleEvaluate(delayMs: Long = 300) {
        evaluateJob?.cancel()
        evaluateJob = scope.launch {
            if (delayMs > 0) delay(delayMs)
            evaluatePolicy()
        }
    }

    private fun startMonitorForeground(): Boolean {
        return try {
            val notification = NotificationHelper.buildWifiPolicyServiceNotification(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NotificationHelper.NOTIFICATION_ID_WIFI_POLICY,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(NotificationHelper.NOTIFICATION_ID_WIFI_POLICY, notification)
            }
            true
        } catch (e: Exception) {
            AppLogger.error(TAG, "startForeground failed", e)
            false
        }
    }

    private fun updateMonitorNotificationVisibility(): Boolean {
        if (!startMonitorForeground()) return false
        val hideNotification = storage.getString(
            StorageKeys.WIFI_POLICY_HIDE_MONITOR_NOTIFICATION,
            "false",
        ) == "true"
        if (hideNotification) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        return true
    }

    private fun evaluatePolicy() {
        if (storage.getString(StorageKeys.WIFI_POLICY_ENABLED, "false") != "true") {
            restoreResidualStateAndStop()
            return
        }
        if (!AndroidWifiPolicy.hasRequiredPermission(this)) {
            clearMatchedState(restoreService = true)
            return
        }

        val currentSsid = AndroidWifiPolicy.currentSsid(this, callbackWifi?.second)
        val matchedSsids = storage.getStringSet(StorageKeys.WIFI_POLICY_SSIDS, emptySet())
        val matched = currentSsid != null && currentSsid in matchedSsids
        val wasMatched = storage.getString(StorageKeys.WIFI_POLICY_MATCHED, "false") == "true"
        val action = WifiPolicyAction.fromStorage(storage.getString(StorageKeys.WIFI_POLICY_ACTION, ""))
        val previousAction = WifiPolicyAction.fromStorage(storage.getString(StorageKeys.WIFI_POLICY_MATCHED_ACTION, ""))

        if (matched && (!wasMatched || previousAction != action)) {
            if (wasMatched && previousAction != action) {
                leaveMatched(previousAction, restoreService = false)
            }
            storage.putString(StorageKeys.WIFI_POLICY_MATCHED, "true")
            storage.putString(StorageKeys.WIFI_POLICY_MATCHED_ACTION, action.storageValue)
            enterMatched(action)
        } else if (!matched && wasMatched) {
            clearMatchedState(restoreService = true)
        } else if (matched && action == WifiPolicyAction.DirectMode) {
            if (storage.getString(StorageKeys.WIFI_POLICY_RUNTIME_MODE, "") != "direct") {
                ensureDirectMode()
            }
        }
    }

    private fun restoreResidualStateAndStop() {
        val pendingRestart = storage.getString(StorageKeys.WIFI_POLICY_PENDING_RESTART, "false") == "true"
        val runtimeMode = storage.getString(StorageKeys.WIFI_POLICY_RUNTIME_MODE, "")
        storage.putString(StorageKeys.WIFI_POLICY_MATCHED, "false")
        storage.putString(StorageKeys.WIFI_POLICY_MATCHED_ACTION, "")
        storage.putString(StorageKeys.WIFI_POLICY_PENDING_RESTART, "false")
        storage.putString(StorageKeys.WIFI_POLICY_RUNTIME_MODE, "")

        if (pendingRestart && !isProxyActive()) {
            controller.start()
        } else if (runtimeMode.isNotEmpty() && isProxyActive()) {
            restartOrQueueModeRestart("", null)
            if (pendingModeRestart != null) return
        }
        setBootReceiverEnabled(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun clearMatchedState(restoreService: Boolean) {
        val wasMatched = storage.getString(StorageKeys.WIFI_POLICY_MATCHED, "false") == "true"
        if (!wasMatched) return
        val previousAction = WifiPolicyAction.fromStorage(storage.getString(StorageKeys.WIFI_POLICY_MATCHED_ACTION, ""))
        storage.putString(StorageKeys.WIFI_POLICY_MATCHED, "false")
        storage.putString(StorageKeys.WIFI_POLICY_MATCHED_ACTION, "")
        leaveMatched(previousAction, restoreService)
    }

    private fun enterMatched(action: WifiPolicyAction) {
        when (action) {
            WifiPolicyAction.StopService -> {
                if (isProxyActive()) {
                    storage.putString(StorageKeys.WIFI_POLICY_PENDING_RESTART, "true")
                    controller.stop()
                    notifySwitch(R.string.notification_wifi_policy_stop_enter)
                }
            }

            WifiPolicyAction.DirectMode -> {
                storage.putString(StorageKeys.WIFI_POLICY_PENDING_RESTART, "false")
                if (ensureDirectMode(R.string.notification_wifi_policy_direct_enter)) {
                    notifySwitch(R.string.notification_wifi_policy_direct_enter)
                }
            }
        }
    }

    private fun leaveMatched(action: WifiPolicyAction, restoreService: Boolean) {
        when (action) {
            WifiPolicyAction.StopService -> {
                val pendingRestart = storage.getString(StorageKeys.WIFI_POLICY_PENDING_RESTART, "false") == "true"
                storage.putString(StorageKeys.WIFI_POLICY_PENDING_RESTART, "false")
                if (restoreService && pendingRestart && !isProxyActive()) {
                    controller.start()
                    notifySwitch(R.string.notification_wifi_policy_stop_leave)
                }
            }

            WifiPolicyAction.DirectMode -> {
                storage.putString(StorageKeys.WIFI_POLICY_RUNTIME_MODE, "")
                if (restoreService && isProxyActive()) {
                    if (restartOrQueueModeRestart("", R.string.notification_wifi_policy_direct_leave)) {
                        notifySwitch(R.string.notification_wifi_policy_direct_leave)
                    }
                }
            }
        }
    }

    private fun ensureDirectMode(notificationResId: Int? = null): Boolean {
        storage.putString(StorageKeys.WIFI_POLICY_RUNTIME_MODE, "direct")
        if (!isProxyActive()) return false
        return restartOrQueueModeRestart("direct", notificationResId)
    }

    private fun restartOrQueueModeRestart(mode: String, notificationResId: Int?): Boolean {
        when (ProxyServiceBridge.state.value.state) {
            ProxyState.Running -> {
                dropPendingModeRestart()
                return restartRunningServiceForWifiMode(mode)
            }

            ProxyState.Starting -> {
                pendingModeRestart = mode
                pendingModeNotificationResId = notificationResId
                AppLogger.info(TAG, "Queued service restart for Wi-Fi policy mode=$mode while starting")
            }

            ProxyState.Stopping, ProxyState.Stopped, ProxyState.Error -> Unit
        }
        return false
    }

    private fun dropPendingModeRestart() {
        pendingModeRestart = null
        pendingModeNotificationResId = null
    }

    private fun consumePendingModeRestart(): Boolean {
        val mode = pendingModeRestart ?: return false
        val notificationResId = pendingModeNotificationResId
        dropPendingModeRestart()
        if (storage.getString(StorageKeys.WIFI_POLICY_RUNTIME_MODE, "") != mode) return false
        val restarted = restartRunningServiceForWifiMode(mode)
        if (restarted && notificationResId != null) {
            notifySwitch(notificationResId)
        }
        return restarted
    }

    private fun restartRunningServiceForWifiMode(mode: String): Boolean {
        val status = ProxyServiceBridge.state.value
        if (status.state != ProxyState.Running) return false
        controller.restart()
        AppLogger.info(TAG, "Restarted service for Wi-Fi policy mode=${mode.ifEmpty { "user" }}")
        return true
    }

    private fun setBootReceiverEnabled(enabled: Boolean) {
        packageManager.setComponentEnabledSetting(
            ComponentName(this, WifiPolicyBootReceiver::class.java),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    private fun isProxyActive(): Boolean = when (ProxyServiceBridge.state.value.state) {
        ProxyState.Running, ProxyState.Starting, ProxyState.Stopping -> true
        ProxyState.Stopped, ProxyState.Error -> false
    }

    private fun notifySwitch(contentResId: Int) {
        if (storage.getString(StorageKeys.WIFI_POLICY_NOTIFY_SWITCH, "true") == "false") return
        NotificationHelper.notifyWifiPolicyEvent(this, contentResId)
    }

    companion object {
        private const val TAG = "WifiPolicyMonitor"
    }
}
