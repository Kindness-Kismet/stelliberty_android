package com.stelliberty.android.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.stelliberty.android.service.AutoDelayReceiver

class AutoDelayAlarm(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val pendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, AutoDelayReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun schedule(triggerElapsedRealtime: Long) {
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerElapsedRealtime, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerElapsedRealtime, pendingIntent)
        }
    }

    fun cancel() {
        alarmManager.cancel(pendingIntent)
    }

    fun acquireWakeLock(): AutoCloseable {
        val wakeLock = context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "${context.packageName}:auto-delay")
        wakeLock.acquire()
        return AutoCloseable { wakeLock.release() }
    }
}
