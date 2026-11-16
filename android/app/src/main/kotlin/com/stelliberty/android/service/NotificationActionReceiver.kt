package com.stelliberty.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.stelliberty.android.platform.ProxyServiceBridge
import com.stelliberty.android.platform.ProxyServiceController
import com.stelliberty.android.platform.TunMode
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class NotificationActionReceiver : BroadcastReceiver(), KoinComponent {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_DISMISS) {
            ProxyServiceBridge.dismissNotification()
            return
        }
        if (intent.action != ACTION_STOP) return
        val controller: ProxyServiceController = get()
        controller.cancelPendingRestart()
        val mode = intent.getStringExtra(EXTRA_SERVICE_MODE)
        if (mode == null) controller.stop() else controller.stop(TunMode.fromStorage(mode))
    }

    companion object {
        const val ACTION_STOP = "com.stelliberty.android.action.NOTIFICATION_STOP"
        const val ACTION_DISMISS = "com.stelliberty.android.action.NOTIFICATION_DISMISS"
        const val EXTRA_SERVICE_MODE = "service_mode"
    }
}
