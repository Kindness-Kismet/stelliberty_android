package com.stelliberty.android.data.repository

import com.stelliberty.android.data.bridge.CoreFetchProgress
import com.stelliberty.android.data.bridge.StellibertyCoreBridge
import com.stelliberty.android.data.bridge.StellibertyCoreError
import com.stelliberty.android.domain.model.Subscription
import com.stelliberty.android.domain.model.SubscriptionTrafficInfo
import com.stelliberty.android.domain.model.SubscriptionUpdateProxyMode
import com.stelliberty.android.platform.ProfileFileManager
import com.stelliberty.android.util.AppLogger
import com.stelliberty.android.util.describe
import java.net.URI
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class ImportStep { Downloading, Prefetching, Validating, Other }

data class ImportProgress(
    val step: ImportStep,
    val providerName: String = "",
    val rawLabel: String = "",
    val current: Int = 0,
    val total: Int = 0,
)

class ConfigValidationException(message: String) : Exception(message)

class ProfileProcessor(
    private val repo: SubscriptionRepositoryImpl,
    private val fileManager: ProfileFileManager,
    private val defaultProfileName: String,
    private val proxyResolver: SubscriptionProxyResolver,
) {

    // 启动时清理：临时目录的残留，以及没人认领的订阅目录（删订阅是先删列表条目再删目录两步，
    // 中间进程挂了就会留下孤儿）。必须和处理流程用同一把锁，否则会擦掉后台任务正在写的内容。
    suspend fun cleanupResidual() = withContext(Dispatchers.IO) {
        processLock.withLock {
            fileManager.cleanupProcessing()
            val known = repo.knownUuidsOrNull()
            if (known == null) {
                AppLogger.warn(TAG, "Subscription list incomplete, skipping orphan cleanup")
                return@withLock
            }
            val orphans = fileManager.deleteOrphanDirs(known)
            if (orphans.isNotEmpty()) AppLogger.info(TAG, "Removed orphan profile dirs: $orphans")
        }
    }

    suspend fun apply(uuid: String, onProgress: (ImportProgress) -> Unit = {}) {
        runProcess(uuid, isUpdate = false, onProgress)
    }

    suspend fun update(uuid: String, onProgress: (ImportProgress) -> Unit = {}) {
        runProcess(uuid, isUpdate = true, onProgress)
    }

    private suspend fun runProcess(
        uuid: String,
        isUpdate: Boolean,
        onProgress: (ImportProgress) -> Unit,
    ) = withContext(Dispatchers.IO) {
        processLock.withLock {
            val (snapshot, workDir) = repo.withProfileLock {
                if (isUpdate) {
                    val imported = repo.queryImported(uuid)
                        ?: throw IllegalArgumentException("Profile $uuid not found")
                    val snap = PendingSnapshot.of(imported)
                    val dir = fileManager.prepareProcessing(uuid)
                    fileManager.readImportedFile(uuid, "config.yaml")?.let {
                        fileManager.writeProcessingConfig(dir, it)
                    }
                    snap to dir
                } else {
                    val pending = repo.queryPending(uuid)
                        ?: throw IllegalArgumentException("No pending profile for $uuid")
                    pending.enforceFieldValid()
                    val dir = fileManager.prepareProcessing(uuid)
                    PendingSnapshot.of(pending) to dir
                }
            }

            try {
                val proxyUrl = if (snapshot.isLocalFile) null
                else proxyResolver.resolveForSubscription(snapshot.updateProxyMode)

                val result = try {
                    StellibertyCoreBridge.fetchAndValid(
                        workDir = workDir,
                        url = if (snapshot.isLocalFile) "" else snapshot.sourceLocation,
                        force = !snapshot.isLocalFile,
                        httpProxy = proxyUrl,
                        userAgent = snapshot.userAgent,
                        ageSecretKey = snapshot.ageSecretKey,
                        onProgress = { p -> onProgress(mapProgress(p)) },
                    )
                } catch (e: StellibertyCoreError) {
                    throw translateCoreError(e)
                }

                val trafficInfo = SubscriptionTrafficInfo.ofOrNull(
                    result.upload, result.download, result.total, result.expire,
                )
                withContext(NonCancellable) {
                    repo.withProfileLock {
                        if (isUpdate) {
                            val current = repo.queryImported(uuid)
                                ?: throw IllegalArgumentException("Imported profile $uuid disappeared during update")
                            check(current.id == snapshot.uuid)
                            fileManager.commitProcessingToImported(uuid)
                            repo.markUpdated(uuid, trafficInfo, result.builtinChainProxyNames)
                        } else {
                            val currentPending = repo.queryPending(uuid)
                                ?: throw IllegalArgumentException("Pending profile $uuid disappeared during commit")
                            check(currentPending.id == snapshot.uuid)
                            fileManager.commitProcessingToImported(uuid)
                            repo.commitPending(
                                uuid = uuid,
                                fetched = true,
                                trafficInfo = trafficInfo,
                                builtinChainProxyNames = result.builtinChainProxyNames,
                                fallbackName = autoProfileName(snapshot, result.fileName),
                            )
                        }
                    }
                }
            } catch (t: Throwable) {
                if (t !is CancellationException) {
                    AppLogger.error(TAG, "Profile pipeline failed for $uuid (update=$isUpdate)", t)
                }
                withContext(NonCancellable) {
                    fileManager.cleanupProcessing()
                    if (isUpdate && t !is CancellationException) {
                        repo.withProfileLock { repo.markFailed(uuid, t.describe()) }
                    }
                }
                throw t
            }
        }
    }

    private fun autoProfileName(snapshot: PendingSnapshot, dispositionName: String): String {
        if (snapshot.isLocalFile) return ""
        return dispositionName
            .ifBlank { runCatching { URI(snapshot.sourceLocation).host.orEmpty() }.getOrDefault("") }
            .ifBlank { defaultProfileName }
    }

    // Go 侧只能返回英文字符串，界面要按语言显示就得在这里认出来换成带类型的异常。
    // 这几条文案由 native/stelliberty_core/fetch.go 产生，改那边必须同步改这里。
    private fun translateCoreError(e: StellibertyCoreError): Throwable {
        val msg = e.message ?: return e
        return when {
            msg.startsWith("validate config:") ->
                ConfigValidationException(msg.removePrefix("validate config:").trim())

            msg == "empty response body" -> ImportError.EmptyBody()

            msg.startsWith("http status ") ->
                msg.removePrefix("http status ").trim().toIntOrNull()
                    ?.let { ImportError.HttpStatus(it) } ?: e

            else -> e
        }
    }

    private fun mapProgress(p: CoreFetchProgress): ImportProgress = when (p.action) {
        "FetchConfiguration" -> ImportProgress(ImportStep.Downloading)
        "FetchProviders" -> ImportProgress(
            step = ImportStep.Prefetching,
            providerName = p.args.firstOrNull().orEmpty(),
            current = p.progress,
            total = p.max,
        )

        "Verifying" -> ImportProgress(ImportStep.Validating)
        else -> ImportProgress(ImportStep.Other, rawLabel = p.action)
    }

    companion object {
        // 必须全进程共享：前台页面和后台任务各自会创建独立的处理器实例，锁若只属于单个实例，
        // 两个更新会交错清空同一个临时目录，最后「界面显示订阅 A、点启动实际跑的是 B」。
        private val processLock = Mutex()

        private const val TAG = "ProfileProcessor"

        suspend fun <T> withProcessLock(block: suspend () -> T): T = processLock.withLock { block() }
    }
}

internal data class PendingSnapshot(
    val uuid: String,
    val isLocalFile: Boolean,
    val sourceLocation: String,
    val userAgent: String,
    val ageSecretKey: String,
    val updateProxyMode: SubscriptionUpdateProxyMode,
) {
    companion object {
        fun of(subscription: Subscription) = PendingSnapshot(
            uuid = subscription.id,
            isLocalFile = subscription.isLocalFile,
            sourceLocation = subscription.sourceLocation,
            userAgent = subscription.userAgent,
            ageSecretKey = subscription.ageSecretKey,
            updateProxyMode = subscription.updateProxyMode,
        )
    }
}
