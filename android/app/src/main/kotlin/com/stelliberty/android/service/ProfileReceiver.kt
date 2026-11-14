package com.stelliberty.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.stelliberty.android.util.AppLogger

class ProfileReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        AppLogger.info(TAG, "onReceive: ${intent.action}")
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> {
                startProfileWorker(context, ProfileWorker.ACTION_SCHEDULE_UPDATES)
            }

            ACTION_PROFILE_REQUEST_UPDATE -> {
                val uuid = intent.data?.host ?: return
                AppLogger.info(TAG, "Update requested for: $uuid")
                startProfileWorker(context, ProfileWorker.ACTION_UPDATE_PROFILE, uuid)
            }
        }
    }

    companion object {
        private const val TAG = "ProfileReceiver"
        const val ACTION_PROFILE_REQUEST_UPDATE = "com.stelliberty.android.action.PROFILE_REQUEST_UPDATE"

        private fun startProfileWorker(context: Context, action: String, uuid: String? = null) {
            val intent = Intent(context, ProfileWorker::class.java).apply {
                this.action = action
                if (uuid != null) data = Uri.parse("uuid://$uuid")
            }
            ProfileWorker.start(context, intent)
        }
    }
}
