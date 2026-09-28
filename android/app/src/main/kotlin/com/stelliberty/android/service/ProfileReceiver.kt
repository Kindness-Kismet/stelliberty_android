package com.stelliberty.android.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.stelliberty.android.util.AppLogger
import kotlin.time.Clock

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
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                AppLogger.error(TAG, "startForegroundService failed for $action", e)
                rescheduleAfterFailure(context, action, uuid)
            }
        }

        private fun rescheduleAfterFailure(context: Context, action: String, uuid: String?) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            val retryDelay = ProfileUpdateScheduler.MIN_INTERVAL_MS
            val triggerAt = Clock.System.now().toEpochMilliseconds() + retryDelay

            when (action) {
                ProfileWorker.ACTION_UPDATE_PROFILE -> {
                    uuid ?: return
                    val pi = ProfileUpdateScheduler.pendingIntentOf(context, uuid)
                    ProfileUpdateScheduler.scheduleAlarm(alarmManager, triggerAt, pi)
                    AppLogger.info(TAG, "Re-armed $uuid update in ${retryDelay / 1000}s after FGS denial")
                }

                ProfileWorker.ACTION_SCHEDULE_UPDATES -> {
                    val retryIntent = Intent(context, ProfileReceiver::class.java).apply {
                        this.action = Intent.ACTION_BOOT_COMPLETED
                    }
                    val pi = PendingIntent.getBroadcast(
                        context, BOOT_RETRY_REQUEST_CODE, retryIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                    ProfileUpdateScheduler.scheduleAlarm(alarmManager, triggerAt, pi)
                    AppLogger.info(TAG, "Re-armed reconcile in ${retryDelay / 1000}s after FGS denial")
                }
            }
        }

        private const val BOOT_RETRY_REQUEST_CODE = -1
    }
}
