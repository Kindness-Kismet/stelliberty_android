package com.stelliberty.android.service

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.stelliberty.android.R
import com.stelliberty.android.data.repository.ProfileProcessor
import com.stelliberty.android.data.store.SubscriptionStore
import com.stelliberty.android.platform.ProxyServiceController
import com.stelliberty.android.util.AppLogger
import com.stelliberty.android.util.describe
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject

class ProfileWorker : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = AtomicInteger(0)

    @Volatile
    private var foregroundFailed = false

    private val subscriptionStore: SubscriptionStore by inject()

    private val updateScheduler: ProfileUpdateScheduler by inject()

    private val serviceController: ProxyServiceController by inject()

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
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (foregroundFailed) return START_NOT_STICKY
        when (intent?.action) {
            ACTION_SCHEDULE_UPDATES -> launchTracked(startId) { updateScheduler.reconcileNow() }

            ACTION_UPDATE_PROFILE -> {
                val uuid = intent.data?.host
                if (uuid != null) launchTracked(startId) { runUpdate(uuid) } else stopIfIdle(startId)
            }

            else -> stopIfIdle(startId)
        }
        return START_NOT_STICKY
    }

    // 每件任务干完后用自己那次的 startId 请求停止：如果期间又来了新请求，系统会告诉我们别停，
    // 那条新请求由它自己的任务去做。换成「等一会儿再清空队列」会漏掉刚入队还没人接手的请求。
    private fun launchTracked(startId: Int, block: suspend () -> Unit) {
        inFlight.incrementAndGet()
        scope.launch {
            try {
                block()
            } finally {
                if (inFlight.decrementAndGet() == 0) stopSelfResult(startId)
            }
        }
    }

    private fun stopIfIdle(startId: Int) {
        if (inFlight.get() == 0) stopSelfResult(startId)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun runUpdate(uuid: String) {
        val imported = subscriptionStore.findImported(uuid) ?: return

        val notificationManager = getSystemService(NotificationManager::class.java)
        val statusId = NotificationHelper.profileProgressId(uuid)
        val processor = get<ProfileProcessor>()

        try {
            notificationManager.notify(
                statusId,
                NotificationHelper.buildProfileUpdatingNotification(this, imported.name),
            )

            processor.update(uuid)

            serviceController.restartAfterProfileUpdate(uuid)

            NotificationHelper.notifyProfileUpdateSuccess(this, imported.name)
            AppLogger.info(TAG, "Profile ${imported.name} updated successfully")
        } catch (e: Exception) {
            AppLogger.error(TAG, "Failed to update profile ${imported.name}", e)
            NotificationHelper.notifyProfileUpdateFailed(
                this, imported.name, e.describe().ifBlank { getString(R.string.notification_unknown_error) }
            )
        } finally {
            notificationManager.cancel(statusId)
        }
    }

    companion object {
        private const val TAG = "ProfileWorker"
        const val ACTION_SCHEDULE_UPDATES = "com.stelliberty.android.action.SCHEDULE_UPDATES"
        const val ACTION_UPDATE_PROFILE = "com.stelliberty.android.action.UPDATE_PROFILE"
    }
}
