package com.stelliberty.android.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.stelliberty.android.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class ProfileWorker : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var inFlight = 0
    private var latestStartId = 0

    private var foregroundFailed = false

    private val task: ProfileUpdateTask by inject()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            startForeground(
                NotificationHelper.NOTIFICATION_ID_PROFILE_WORKER,
                NotificationHelper.buildProfileWorkerNotification(this),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } catch (e: Exception) {
            AppLogger.error(TAG, "startForeground failed", e)
            foregroundFailed = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        if (intent == null) {
            stopIfIdle()
            return START_NOT_STICKY
        }
        if (foregroundFailed) {
            ProfileUpdateJobService.enqueue(this, intent)
            stopIfIdle()
            return START_NOT_STICKY
        }
        inFlight++
        scope.launch {
            try {
                withContext(Dispatchers.IO) { task.run(intent) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.error(TAG, "Automatic profile update request failed", e)
                ProfileUpdateJobService.enqueue(this@ProfileWorker, intent)
            } finally {
                inFlight--
                stopIfIdle()
            }
        }
        return START_REDELIVER_INTENT
    }

    // 计数、接收请求和收尾都在主线程；最后完成的任务负责停止最新 startId。
    private fun stopIfIdle() {
        if (inFlight == 0) stopSelfResult(latestStartId)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ProfileWorker"
        const val ACTION_SCHEDULE_UPDATES = "com.stelliberty.android.action.SCHEDULE_UPDATES"
        const val ACTION_UPDATE_PROFILE = "com.stelliberty.android.action.UPDATE_PROFILE"
        const val ACTION_UPDATE_PROFILES = "com.stelliberty.android.action.UPDATE_PROFILES"
        const val EXTRA_UUIDS = "uuids"

        fun startUpdates(context: Context, uuids: List<String>) {
            if (uuids.isEmpty()) return
            start(context, Intent(context, ProfileWorker::class.java).apply {
                action = ACTION_UPDATE_PROFILES
                putStringArrayListExtra(EXTRA_UUIDS, ArrayList(uuids))
            })
        }

        fun start(context: Context, intent: Intent) {
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                AppLogger.warn(TAG, "Foreground update unavailable; scheduling background work", e)
                ProfileUpdateJobService.enqueue(context, intent)
            }
        }
    }
}
