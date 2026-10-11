package com.stelliberty.android.data.repository

import com.stelliberty.android.data.bridge.StellibertyCoreBridge
import com.stelliberty.android.data.store.ProfileTransformWriter
import com.stelliberty.android.data.store.SubscriptionStore
import com.stelliberty.android.domain.model.ProxyPreview
import com.stelliberty.android.domain.repository.ProxyPreviewRepository
import com.stelliberty.android.platform.ProfileFileManager
import com.stelliberty.android.util.AppLogger
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProxyPreviewRepositoryImpl(
    private val subscriptionStore: SubscriptionStore,
    private val transformWriter: ProfileTransformWriter,
    private val fileManager: ProfileFileManager,
) : ProxyPreviewRepository {

    // 与运行时同一套变换（覆写 + 链式代理）；规则覆写只动 rules 段，预览不需要套。
    override suspend fun load(subscriptionId: String): ProxyPreview? = withContext(Dispatchers.IO) {
        val subscription = subscriptionStore.findImported(subscriptionId) ?: return@withContext null
        val transform = transformWriter.write(subscription, PREVIEW_TRANSFORM, rules = null)
        try {
            StellibertyCoreBridge.proxyPreview(
                workDir = File(fileManager.getImportedDir(subscription.id)),
                transform = transform?.let(::File),
                ageSecretKey = subscription.ageSecretKey,
            )
        } catch (e: Exception) {
            AppLogger.warn(TAG, "load proxy preview failed", e)
            null
        } finally {
            transform?.let { File(it).delete() }
        }
    }

    private companion object {
        const val TAG = "ProxyPreviewRepository"
        const val PREVIEW_TRANSFORM = "proxy.preview.json"
    }
}
