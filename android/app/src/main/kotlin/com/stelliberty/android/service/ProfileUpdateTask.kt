package com.stelliberty.android.service

import android.content.Intent
import com.stelliberty.android.data.repository.ProfileProcessor
import com.stelliberty.android.data.store.SubscriptionStore
import com.stelliberty.android.domain.model.SubscriptionAutoUpdateMode
import com.stelliberty.android.platform.ProxyServiceController
import com.stelliberty.android.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ProfileUpdateTask(
    private val store: SubscriptionStore,
    private val processor: ProfileProcessor,
    private val controller: ProxyServiceController,
    private val scheduler: ProfileUpdateScheduler,
) {
    suspend fun run(intent: Intent) = updateLock.withLock {
        if (intent.action == ProfileWorker.ACTION_SCHEDULE_UPDATES) {
            scheduler.reconcileNow()
            return@withLock
        }
        val targets = when (intent.action) {
            ProfileWorker.ACTION_UPDATE_PROFILE -> listOfNotNull(intent.data?.host)
            ProfileWorker.ACTION_UPDATE_PROFILES -> intent.getStringArrayListExtra(ProfileWorker.EXTRA_UUIDS).orEmpty()
            else -> return@withLock
        }
        val updated = mutableListOf<String>()
        for (uuid in targets.distinct()) {
            val subscription = store.findImported(uuid) ?: continue
            if (subscription.isLocalFile) continue
            // 排队期间可能改了自动更新模式，或已由手动更新续期。
            if (intent.action == ProfileWorker.ACTION_UPDATE_PROFILE && !scheduler.isDue(subscription)) continue
            if (intent.action == ProfileWorker.ACTION_UPDATE_PROFILES &&
                subscription.autoUpdateMode != SubscriptionAutoUpdateMode.Startup
            ) continue
            try {
                processor.update(uuid)
                updated += uuid
                AppLogger.info(TAG, "Automatic profile update completed: $uuid")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 失败原因由 ProfileProcessor 保存到订阅卡片，自动任务不发布交互提示。
                AppLogger.error(TAG, "Automatic profile update failed: $uuid", e)
            }
        }
        // 下载整批订阅后再重启，后续经 mixed-port 的请求才能保持连接。
        updated.forEach { controller.restartAfterProfileUpdate(it) }
    }

    private companion object {
        const val TAG = "ProfileUpdateTask"
        val updateLock = Mutex()
    }
}
