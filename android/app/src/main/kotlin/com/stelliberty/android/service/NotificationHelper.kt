package com.stelliberty.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.stelliberty.android.MainActivity
import com.stelliberty.android.R
import com.stelliberty.android.service.NotificationHelper.PROFILE_PROGRESS_ID_BASE
import java.util.concurrent.atomic.AtomicInteger

// 通知编号按用途分段，撞号就会互相覆盖或取消掉：固定通知一段、更新结果一段循环用、
// 每个订阅的进度另占一段。新增通知按段取号。
object NotificationHelper {

    private const val CHANNEL_VPN = "stelliberty_vpn"
    const val NOTIFICATION_ID_VPN = 1

    private const val CHANNEL_PROFILE_STATUS = "stelliberty_profile_status"
    const val NOTIFICATION_ID_PROFILE_WORKER = 2
    private const val PROFILE_PROGRESS_ID_BASE = 0x10000

    private const val CHANNEL_PROFILE_RESULT = "stelliberty_profile_result"
    private const val RESULT_ID_BASE = 100
    private const val RESULT_ID_RANGE = 900
    private val nextResultId = AtomicInteger(0)

    private const val CHANNEL_WIFI_POLICY_SERVICE = "stelliberty_wifi_policy_service"
    private const val CHANNEL_WIFI_POLICY_EVENT = "stelliberty_wifi_policy_event"
    const val NOTIFICATION_ID_WIFI_POLICY = 3

    private const val NOTIFICATION_ID_WIFI_POLICY_EVENT = 4

    fun profileProgressId(uuid: String): Int =
        PROFILE_PROGRESS_ID_BASE + (uuid.hashCode() and 0xFFFF)

    private fun nextResultId(): Int =
        RESULT_ID_BASE + (nextResultId.getAndIncrement() % RESULT_ID_RANGE)

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_VPN,
                    context.getString(R.string.channel_vpn_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.channel_vpn_desc)
                    setShowBadge(false)
                },
                NotificationChannel(
                    CHANNEL_PROFILE_STATUS,
                    context.getString(R.string.channel_profile_status_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.channel_profile_status_desc)
                    setShowBadge(false)
                },
                NotificationChannel(
                    CHANNEL_PROFILE_RESULT,
                    context.getString(R.string.channel_profile_result_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.channel_profile_result_desc)
                },
                NotificationChannel(
                    CHANNEL_WIFI_POLICY_SERVICE,
                    context.getString(R.string.channel_wifi_policy_service_name),
                    NotificationManager.IMPORTANCE_MIN,
                ).apply {
                    description = context.getString(R.string.channel_wifi_policy_service_desc)
                    setShowBadge(false)
                },
                NotificationChannel(
                    CHANNEL_WIFI_POLICY_EVENT,
                    context.getString(R.string.channel_wifi_policy_event_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.channel_wifi_policy_event_desc)
                },
            )
        )
    }

    fun buildNotification(context: Context, title: String, content: String): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(context, CHANNEL_VPN)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun buildLoadingNotification(context: Context): Notification {
        return buildNotification(
            context,
            context.getString(R.string.app_name),
            context.getString(R.string.notification_loading),
        )
    }

    fun buildRunningNotification(context: Context, mode: String = "VpnService"): Notification {
        return buildNotification(
            context,
            context.getString(R.string.notification_running_title),
            context.getString(R.string.notification_running_content, mode)
        )
    }

    fun buildDynamicNotification(
        context: Context,
        profileName: String,
        uploadTotal: String,
        downloadTotal: String,
        uploadSpeed: String,
        downloadSpeed: String,
    ): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(context, CHANNEL_VPN)
            .setContentTitle("$profileName • $uploadTotal↑ $downloadTotal↓")
            .setContentText("$uploadSpeed↑ $downloadSpeed↓")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun buildWifiPolicyServiceNotification(context: Context): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, NOTIFICATION_ID_WIFI_POLICY, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(context, CHANNEL_WIFI_POLICY_SERVICE)
            .setContentTitle(context.getString(R.string.notification_wifi_policy_title))
            .setContentText(context.getString(R.string.notification_wifi_policy_service_content))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun notifyWifiPolicyEvent(context: Context, contentResId: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, NOTIFICATION_ID_WIFI_POLICY_EVENT, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = Notification.Builder(context, CHANNEL_WIFI_POLICY_EVENT)
            .setContentTitle(context.getString(R.string.notification_wifi_policy_title))
            .setContentText(context.getString(contentResId))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID_WIFI_POLICY_EVENT, notification)
    }

    fun buildProfileWorkerNotification(context: Context): Notification {
        return Notification.Builder(context, CHANNEL_PROFILE_STATUS)
            .setContentTitle(context.getString(R.string.notification_profile_update))
            .setContentText(context.getString(R.string.notification_profile_running))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun buildProfileUpdatingNotification(context: Context, name: String): Notification {
        return Notification.Builder(context, CHANNEL_PROFILE_STATUS)
            .setContentTitle(context.getString(R.string.notification_profile_updating))
            .setContentText(name)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setGroup(CHANNEL_PROFILE_STATUS)
            .build()
    }

    fun notifyProfileUpdateSuccess(context: Context, name: String): Int {
        val id = nextResultId()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = Notification.Builder(context, CHANNEL_PROFILE_RESULT)
            .setContentTitle(context.getString(R.string.notification_update_success))
            .setContentText(context.getString(R.string.notification_update_success_content, name))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup(CHANNEL_PROFILE_RESULT)
            .build()

        context.getSystemService(NotificationManager::class.java).notify(id, notification)
        return id
    }

    fun notifyProfileUpdateFailed(context: Context, name: String, reason: String): Int {
        val id = nextResultId()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val text = "$name: $reason"
        val notification = Notification.Builder(context, CHANNEL_PROFILE_RESULT)
            .setContentTitle(context.getString(R.string.notification_update_failed))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup(CHANNEL_PROFILE_RESULT)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .build()

        context.getSystemService(NotificationManager::class.java).notify(id, notification)
        return id
    }
}
