package com.stelliberty.android.platform

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.stelliberty.android.service.WifiPolicyBootReceiver
import com.stelliberty.android.service.WifiPolicyMonitorService

object WifiPolicyIntents {
    const val ACTION_START = "com.stelliberty.android.action.WIFI_POLICY_START"
    const val ACTION_STOP = "com.stelliberty.android.action.WIFI_POLICY_STOP"
    const val ACTION_EVALUATE = "com.stelliberty.android.action.WIFI_POLICY_EVALUATE"
}

enum class WifiPolicyAction(val storageValue: String) {
    StopService("stop_service"),
    DirectMode("direct");

    companion object {
        fun fromStorage(value: String): WifiPolicyAction =
            entries.firstOrNull { it.storageValue == value } ?: StopService
    }
}

class WifiPolicyController(private val context: PlatformContext) {

    fun hasRequiredPermission(): Boolean = AndroidWifiPolicy.hasRequiredPermission(context)

    fun currentSsid(): String? = AndroidWifiPolicy.currentSsid(context)

    fun startMonitor() {
        setBootReceiverEnabled(true)
        context.startForegroundService(buildIntent(WifiPolicyIntents.ACTION_START))
    }

    fun stopMonitor() {
        setBootReceiverEnabled(false)
        context.startService(buildIntent(WifiPolicyIntents.ACTION_STOP))
    }

    fun evaluateNow() {
        context.startForegroundService(buildIntent(WifiPolicyIntents.ACTION_EVALUATE))
    }

    private fun buildIntent(action: String): Intent =
        Intent(context, WifiPolicyMonitorService::class.java).setAction(action)

    private fun setBootReceiverEnabled(enabled: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, WifiPolicyBootReceiver::class.java),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}

object AndroidWifiPolicy {
    fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun hasRequiredPermission(context: Context): Boolean =
        requiredPermissions().all {
            context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }

    fun currentSsid(context: Context, callbackSsid: String? = null): String? {
        if (!hasRequiredPermission(context)) return null
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) {
            return null
        }
        callbackSsid?.let { return it }
        normalizeSsid((capabilities.transportInfo as? WifiInfo)?.ssid)?.let { return it }
        @Suppress("DEPRECATION")
        val fallback = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
            ?.connectionInfo
            ?.ssid
        return normalizeSsid(fallback)
    }

    fun normalizeSsid(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        if (trimmed == WifiManager.UNKNOWN_SSID || trimmed.equals("<unknown ssid>", ignoreCase = true)) return null
        return if (trimmed.length >= 2 && trimmed.first() == '"' && trimmed.last() == '"') {
            trimmed.substring(1, trimmed.length - 1)
        } else {
            trimmed
        }.takeIf { it.isNotEmpty() }
    }
}
