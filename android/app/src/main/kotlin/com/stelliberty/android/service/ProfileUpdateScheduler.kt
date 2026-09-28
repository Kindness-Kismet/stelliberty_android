package com.stelliberty.android.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.stelliberty.android.data.repository.MIN_AUTO_UPDATE_INTERVAL_MINUTES
import com.stelliberty.android.data.store.SubscriptionStore
import com.stelliberty.android.domain.model.Subscription
import com.stelliberty.android.domain.model.SubscriptionAutoUpdateMode
import com.stelliberty.android.util.AppLogger
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ProfileUpdateScheduler(
    private val context: Context,
    private val store: SubscriptionStore,
    private val scope: CoroutineScope,
) {

    private var job: Job? = null

    private val armed = mutableSetOf<String>()

    fun start() {
        if (job != null) return
        job = scope.launch(Dispatchers.IO) {
            store.importedFlow.collect { reconcile(it) }
        }
    }

    fun reconcileNow() {
        reconcile(store.imported())
    }

    // 闹钟集合完全由订阅列表推导出来，跟着列表的变化持续核对：新增订阅、改间隔立刻生效，删订阅自动撤闹钟。
    // 不要改成「在每个增删改的地方各自布置闹钟」，那样漏一处就是设了自动更新却永远不更新。
    private fun reconcile(subscriptions: List<Subscription>) {
        val eligible = subscriptions.filter { it.intervalMillisOrNull() != null }
        val keep = eligible.mapTo(mutableSetOf()) { it.id }
        ((armed + subscriptions.map { it.id }) - keep).forEach { cancelNext(context, it) }
        eligible.forEach { scheduleNext(context, it) }
        armed.clear()
        armed += keep
    }

    companion object {
        private const val TAG = "ProfileUpdateScheduler"

        const val MIN_INTERVAL_MS = MIN_AUTO_UPDATE_INTERVAL_MINUTES * 60 * 1000L

        // 本地文件与「启动时更新」不布置闹钟；间隔低于下限的视为关闭。
        private fun Subscription.intervalMillisOrNull(): Long? {
            if (isLocalFile || autoUpdateMode != SubscriptionAutoUpdateMode.Interval) return null
            return (autoUpdateIntervalMinutes * 60_000L).takeIf { it >= MIN_INTERVAL_MS }
        }

        private fun scheduleNext(context: Context, subscription: Subscription) {
            val intent = pendingIntentOf(context, subscription.id)
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return

            alarmManager.cancel(intent)
            val interval = subscription.intervalMillisOrNull() ?: return

            val current = Clock.System.now().toEpochMilliseconds()
            // 同 PC：起点取最近一次成功或失败，失败后也顺延一个间隔，不会立刻重试。
            val lastAttempt = listOfNotNull(subscription.lastUpdatedAt ?: subscription.createdAt, subscription.lastErrorAt)
                .max().toEpochMilliseconds()
            val delay = (interval - (current - lastAttempt)).coerceAtLeast(0)
            AppLogger.info(TAG, "Schedule ${subscription.id} (${subscription.name}) in ${delay / 1000}s")
            scheduleAlarm(alarmManager, current + delay, intent)
        }

        fun cancelNext(context: Context, uuid: String) {
            context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntentOf(context, uuid))
        }

        // 精确闹钟能让后台启动服务被系统放行。用户若在系统设置里收回了精确闹钟权限就退成不精确闹钟，
        // 此时靠 ProfileReceiver 里的异常捕获加重新布置来保住调度链。
        fun scheduleAlarm(alarmManager: AlarmManager, triggerAt: Long, pi: PendingIntent) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }

        fun pendingIntentOf(context: Context, uuid: String): PendingIntent {
            val intent = Intent(ProfileReceiver.ACTION_PROFILE_REQUEST_UPDATE).apply {
                setPackage(context.packageName)
                data = Uri.parse("uuid://$uuid")
            }
            return PendingIntent.getBroadcast(
                context,
                uuid.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
