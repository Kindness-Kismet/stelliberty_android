package com.stelliberty.android.data.repository

import com.stelliberty.android.data.bridge.StellibertyCoreBridge
import com.stelliberty.android.data.bridge.StellibertyCoreError
import com.stelliberty.android.data.store.ProfileTransformWriter
import com.stelliberty.android.data.store.SubscriptionStore
import com.stelliberty.android.domain.model.ChainProxyContext
import com.stelliberty.android.domain.model.Subscription
import com.stelliberty.android.domain.model.SubscriptionCustomChainProxy
import com.stelliberty.android.domain.repository.ChainProxyRepository
import com.stelliberty.android.platform.ProfileFileManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// 保存持 processLock，校验与写入之间订阅配置和覆写文件保持一致；读取候选项只读文件，不占这把锁。
class ChainProxyRepositoryImpl(
    private val subscriptionStore: SubscriptionStore,
    private val subscriptions: SubscriptionRepositoryImpl,
    private val transformWriter: ProfileTransformWriter,
    private val fileManager: ProfileFileManager,
) : ChainProxyRepository {

    private val contextLock = Mutex()

    override suspend fun loadContext(subscriptionId: String): ChainProxyContext = contextLock.withLock {
        withContext(Dispatchers.IO) {
            val subscription = find(subscriptionId)
            val transform = transformWriter.write(subscription, CONTEXT_TRANSFORM, withChains = false, rules = null)
            try {
                StellibertyCoreBridge.chainProxyContext(
                    workDir = File(fileManager.getImportedDir(subscription.id)),
                    transform = transform?.let(::File),
                    ageSecretKey = subscription.ageSecretKey,
                )
            } finally {
                transform?.let { File(it).delete() }
            }
        }
    }

    override suspend fun save(
        subscriptionId: String,
        disabledBuiltinNames: List<String>,
        customChainProxies: List<SubscriptionCustomChainProxy>,
    ): Boolean = ProfileProcessor.withProcessLock {
        val candidate = find(subscriptionId).copy(
            disabledBuiltinChainProxyNames = disabledBuiltinNames,
            customChainProxies = customChainProxies,
        )
        val hasCycle = validate(candidate)
        subscriptions.setChainProxies(subscriptionId, disabledBuiltinNames, customChainProxies)
        hasCycle
    }

    private suspend fun validate(subscription: Subscription): Boolean = withContext(Dispatchers.IO) {
        val transform = transformWriter.write(subscription, VALIDATE_TRANSFORM) ?: return@withContext false
        try {
            StellibertyCoreBridge.validateTransform(
                workDir = File(fileManager.getImportedDir(subscription.id)),
                transform = File(transform),
                ageSecretKey = subscription.ageSecretKey,
            )
        } catch (e: StellibertyCoreError) {
            throw ConfigValidationException(e.message.orEmpty().removePrefix("validate config:").trim())
        } finally {
            File(transform).delete()
        }
    }

    private fun find(id: String): Subscription =
        subscriptionStore.findImported(id) ?: throw IllegalArgumentException("Profile $id not found")

    private companion object {
        const val CONTEXT_TRANSFORM = "chain.context.json"
        const val VALIDATE_TRANSFORM = "chain.validate.json"
    }
}
