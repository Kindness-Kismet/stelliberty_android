package com.stelliberty.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.stelliberty.android.platform.PlatformStorage
import com.stelliberty.android.platform.StorageKeys
import com.stelliberty.android.platform.WifiPolicyIntents

class WifiPolicyBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val storage = PlatformStorage(context)
                val enabled = storage.getString(StorageKeys.WIFI_POLICY_ENABLED, "false") == "true"
                val pendingRestart = storage.getString(StorageKeys.WIFI_POLICY_PENDING_RESTART, "false") == "true"
                if (!enabled && !pendingRestart) return
                val serviceIntent = Intent(context, WifiPolicyMonitorService::class.java).apply {
                    action = WifiPolicyIntents.ACTION_START
                }
                context.startForegroundService(serviceIntent)
            }
        }
    }
}
