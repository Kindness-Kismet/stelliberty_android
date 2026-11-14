package com.stelliberty.android.data.repository

import com.stelliberty.android.data.bridge.StellibertyCoreBridge
import com.stelliberty.android.data.bridge.StellibertyCoreError
import com.stelliberty.android.data.store.OverrideProfileStore
import com.stelliberty.android.data.store.OverrideReplacement
import com.stelliberty.android.data.store.ProfileTransformWriter
import com.stelliberty.android.data.store.SubscriptionStore
import com.stelliberty.android.domain.model.OverrideFormat
import com.stelliberty.android.domain.model.OverrideProfile
import com.stelliberty.android.domain.model.OverrideSourceType
import com.stelliberty.android.domain.model.Subscription
import com.stelliberty.android.domain.model.SubscriptionUpdateProxyMode
import com.stelliberty.android.domain.repository.OverrideProfileRepository
import com.stelliberty.android.platform.ProfileFileManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Url
import io.ktor.http.isSuccess
import java.io.File
import java.net.Proxy
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed class OverrideInputError(message: String) : Exception(message) {
    class NameRequired : OverrideInputError("empty override name")
    class InvalidUrl : OverrideInputError("override url must start with http:// or https://")
}

// 写入与备份共用 processLock，锁顺序为 lock → processLock → profileLock；下载在锁外进行。
class OverrideProfileRepositoryImpl(
    private val store: OverrideProfileStore,
    private val transformWriter: ProfileTransformWriter,
    private val subscriptionStore: SubscriptionStore,
    private val subscriptions: SubscriptionRepositoryImpl,
    private val fileManager: ProfileFileManager,
    private val proxyResolver: SubscriptionProxyResolver,
    scope: CoroutineScope,
) : OverrideProfileRepository {

    private val lock = Mutex()

    override val profiles: StateFlow<ImmutableList<OverrideProfile>> = store.profiles
        .map { it.toPersistentList() }
        .stateIn(scope, SharingStarted.Eagerly, store.all().toPersistentList())

    override fun isUsedByCurrent(id: String): Boolean = subscriptionStore.current()?.overrideIds?.contains(id) == true

    override suspend fun readContent(id: String): String = withContext(Dispatchers.IO) {
        store.find(id)?.let(store::readContent).orEmpty()
    }

    override suspend fun addRemote(
        name: String,
        url: String,
        format: OverrideFormat,
        updateProxyMode: SubscriptionUpdateProxyMode,
    ): OverrideProfile {
        requireName(name)
        requireUrl(url)
        val content = download(url.trim(), updateProxyMode)
        val profile = newProfile(name, OverrideSourceType.Remote, format, url.trim())
            .copy(updateProxyMode = updateProxyMode)
        mutate { store.save(profile, content) }
        return profile
    }

    override suspend fun addLocal(
        name: String,
        fileName: String,
        format: OverrideFormat,
        content: String,
    ): OverrideProfile {
        requireName(name)
        val profile = newProfile(name, OverrideSourceType.Local, format, fileName)
        mutate { store.save(profile, content) }
        return profile
    }

    override suspend fun addBlank(name: String, format: OverrideFormat): OverrideProfile {
        requireName(name)
        val profile = newProfile(name, OverrideSourceType.Local, format, "")
        mutate { store.save(profile, "") }
        return profile
    }

    override suspend fun edit(
        id: String,
        name: String,
        url: String,
        format: OverrideFormat,
        updateProxyMode: SubscriptionUpdateProxyMode,
    ): Unit = mutate {
        val profile = find(id)
        requireName(name)
        if (profile.isRemote) requireUrl(url)
        val next = profile.copy(
            name = name.trim(),
            sourceLocation = if (profile.isRemote) url.trim() else profile.sourceLocation,
            format = format,
            updateProxyMode = if (profile.isRemote) updateProxyMode else profile.updateProxyMode,
        )
        if (next.format != profile.format) validateForCurrent(profile, next.format, content = null)
        store.save(next, content = null)
    }

    override suspend fun saveContent(id: String, content: String): Unit = mutate {
        val profile = find(id)
        validateForCurrent(profile, profile.format, content)
        store.save(profile, content)
    }

    override suspend fun update(id: String) {
        val profile = find(id)
        require(profile.isRemote) { "Override $id is local" }
        val content = download(profile.sourceLocation, profile.updateProxyMode)
        mutate {
            val current = store.find(id) ?: return@mutate
            if (current.sourceLocation != profile.sourceLocation) return@mutate
            validateForCurrent(current, current.format, content)
            store.save(current.copy(lastUpdatedAt = Clock.System.now()), content)
        }
    }

    override suspend fun delete(id: String): Unit = mutate {
        subscriptions.removeOverrideReferences(id)
        store.delete(id)
    }

    override suspend fun setSelection(
        subscriptionId: String,
        overrideIds: List<String>,
        sortPreference: List<String>,
    ): Unit = mutate {
        val subscription = subscriptionStore.findImported(subscriptionId)
            ?: throw IllegalArgumentException("Profile $subscriptionId not found")
        val candidate = subscription.copy(overrideIds = overrideIds, overrideSortPreference = sortPreference)
        validate(candidate, replacement = null)
        subscriptions.setOverrides(subscriptionId, overrideIds, sortPreference)
    }

    private fun find(id: String): OverrideProfile =
        store.find(id) ?: throw IllegalArgumentException("Override $id not found")

    private suspend fun <T> mutate(block: suspend () -> T): T = lock.withLock {
        ProfileProcessor.withProcessLock(block)
    }

    // 只校验当前订阅：其余订阅要等切换过去才会用到，届时由内核启动报错。
    private suspend fun validateForCurrent(profile: OverrideProfile, format: OverrideFormat, content: String?) {
        val current = subscriptionStore.current()?.takeIf { profile.id in it.overrideIds } ?: return
        withContext(Dispatchers.IO) {
            val path = if (content != null) {
                fileManager.writeMihomoFile(VALIDATE_CONTENT, content)
                VALIDATE_CONTENT
            } else store.contentPath(profile)
            try {
                validate(current, OverrideReplacement(profile.id, format, path))
            } finally {
                if (content != null) File(fileManager.getMihomoWorkDir(), VALIDATE_CONTENT).delete()
            }
        }
    }

    // 调用方持有 processLock，校验到保存期间订阅与覆写文件保持一致。
    private suspend fun validate(subscription: Subscription, replacement: OverrideReplacement?) =
        withContext(Dispatchers.IO) {
            val transform = transformWriter.write(subscription, VALIDATE_TRANSFORM, replacement)
                ?: return@withContext
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

    private suspend fun download(url: String, mode: SubscriptionUpdateProxyMode): String {
        val proxyUrl = proxyResolver.resolveForSubscription(mode, url)
        return HttpClient {
            install(HttpTimeout) { requestTimeoutMillis = DOWNLOAD_TIMEOUT_MS }
            // 不指定时 OkHttp 会读系统代理，直连必须显式写 NO_PROXY。
            engine { proxy = proxyUrl?.let { ProxyBuilder.http(Url(it)) } ?: Proxy.NO_PROXY }
        }.use { client ->
            val response = client.get(url)
            if (!response.status.isSuccess()) throw ImportError.HttpStatus(response.status.value)
            response.bodyAsText()
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun newProfile(
        name: String,
        sourceType: OverrideSourceType,
        format: OverrideFormat,
        sourceLocation: String,
    ): OverrideProfile {
        val now = Clock.System.now()
        return OverrideProfile(
            id = Uuid.random().toHexString(),
            name = name.trim(),
            sourceType = sourceType,
            format = format,
            sourceLocation = sourceLocation,
            createdAt = now,
            lastUpdatedAt = now,
        )
    }

    private fun requireName(name: String) {
        if (name.isBlank()) throw OverrideInputError.NameRequired()
    }

    private fun requireUrl(url: String) {
        val lower = url.trim().lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) throw OverrideInputError.InvalidUrl()
    }

    private companion object {
        const val VALIDATE_TRANSFORM = "override.validate.json"
        const val VALIDATE_CONTENT = "override.validate.content"
        const val DOWNLOAD_TIMEOUT_MS = 30_000L
    }
}
