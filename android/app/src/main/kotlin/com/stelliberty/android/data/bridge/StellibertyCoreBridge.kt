package com.stelliberty.android.data.bridge

import com.stelliberty.android.domain.model.ChainProxyContext
import com.stelliberty.android.domain.model.ProxyPreview
import com.stelliberty.android.domain.model.RuleOverrideContext
import com.stelliberty.android.service.ProfileFileOps
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class AgeKeyPair(val secretKey: String, val publicKey: String)

@Serializable
data class CoreFetchProgress(
    val action: String,
    val args: List<String> = emptyList(),
    val progress: Int = -1,
    val max: Int = -1,
)

@Serializable
data class CoreFetchResult(
    val upload: Long = 0,
    val download: Long = 0,
    val total: Long = 0,
    val expire: Long = 0,
    val fileName: String = "",
    val builtinChainProxyNames: List<String> = emptyList(),
)

@Serializable
private data class TransformCheck(val hasCycle: Boolean = false)

class StellibertyCoreError(message: String) : RuntimeException(message)

object StellibertyCoreBridge {

    private val tokenSeq = AtomicInteger(1)
    private val json = Json { ignoreUnknownKeys = true }

    init {
        // 加载顺序不能换：桥接库链接了 mihomo 库里的符号，必须先把 mihomo 库装进来。
        System.loadLibrary("mihomo")
        System.loadLibrary("stelliberty_jni")
    }

    fun init(homeDir: String) {
        nativeCoreInit(homeDir)
    }

    // 取消钩子必须在阻塞调用之前挂好：这个原生调用不响应协程取消，等它返回时下层已经清掉了取消登记表，
    // 那时再喊停就是空操作，锁会被一直占到超时。
    suspend fun fetchAndValid(
        workDir: String,
        url: String,
        force: Boolean,
        httpProxy: String?,
        userAgent: String,
        ageSecretKey: String,
        onProgress: suspend (CoreFetchProgress) -> Unit,
    ): CoreFetchResult = coroutineScope {
        val token = tokenSeq.getAndIncrement()
        val nativeDone = AtomicBoolean(false)
        val canceller = launch {
            try {
                awaitCancellation()
            } finally {
                if (!nativeDone.get()) runCatching { nativeCancel(token) }
            }
        }
        val pollerJob = launchProgressPoller(this, token, onProgress)
        try {
            val raw = withContext(Dispatchers.IO) {
                ProfileFileOps.awaitGeodata()
                nativeSetAgeSecretKey(ageSecretKey)
                try {
                    nativeFetchAndValid(workDir, url, force, httpProxy, userAgent, token)
                } finally {
                    nativeDone.set(true)
                    nativeSetAgeSecretKey("")
                }
            }
            decodePayload(raw, CoreFetchResult.serializer())
        } finally {
            canceller.cancel()
            pollerJob.cancel()
        }
    }

    private fun launchProgressPoller(
        scope: CoroutineScope,
        token: Int,
        onProgress: suspend (CoreFetchProgress) -> Unit,
    ) = scope.launch(Dispatchers.IO) {
        var last: String? = null
        while (isActive) {
            val current = nativeQueryProgress(token)
            if (current != null && current != last) {
                last = current
                runCatching { json.decodeFromString(CoreFetchProgress.serializer(), current) }
                    .onSuccess { onProgress(it) }
            }
            delay(PROGRESS_POLL_INTERVAL_MS)
        }
    }

    @JvmStatic
    private external fun nativeCoreInit(homeDir: String)

    @JvmStatic
    private external fun nativeFetchAndValid(
        workDir: String,
        url: String,
        force: Boolean,
        httpProxy: String?,
        userAgent: String,
        token: Int,
    ): String?

    @JvmStatic
    private external fun nativeCancel(token: Int)

    @JvmStatic
    private external fun nativeQueryProgress(token: Int): String?

    fun generateAgeKeyPair(hybrid: Boolean = false): AgeKeyPair? {
        val raw = (if (hybrid) nativeGenAgeHybridKeyPair() else nativeGenAgeKeyPair()) ?: return null
        if (raw.startsWith("error:")) return null
        val lines = raw.split("\n")
        if (lines.size < 2) return null
        val secret = lines[0].trim()
        val public = lines[1].trim()
        if (secret.isEmpty() || public.isEmpty()) return null
        return AgeKeyPair(secretKey = secret, publicKey = public)
    }

    // 把 age 加密的配置解到 target，未加密的原样复制；失败抛 StellibertyCoreError。
    fun decryptFile(source: File, target: File, secretKey: String) {
        val raw = nativeDecryptFile(source.path, target.path, secretKey)
        if (raw != null && raw.startsWith("error:")) {
            throw StellibertyCoreError(raw.removePrefix("error:").trim())
        }
    }

    // 从 APK 里解出 xz 压缩的 asset 写到 target，失败抛 StellibertyCoreError。
    fun extractXzAsset(apkPath: String, entry: String, target: File) {
        val raw = nativeExtractXzAsset(apkPath, entry, target.path)
        if (raw != null && raw.startsWith("error:")) {
            throw StellibertyCoreError(raw.removePrefix("error:").trim())
        }
    }

    @JvmStatic
    private external fun nativeExtractXzAsset(apkPath: String, entry: String, target: String): String?

    @JvmStatic
    private external fun nativeDecryptFile(source: String, target: String, secretKey: String): String?

    // 与运行时同一套变换与解析流程，失败抛 StellibertyCoreError；返回变换后的配置是否含链式代理循环引用。
    fun validateTransform(workDir: File, transform: File, ageSecretKey: String): Boolean {
        ProfileFileOps.awaitGeodata()
        val raw = nativeValidateTransform(workDir.path, transform.path, ageSecretKey)
        return decodePayload(raw, TransformCheck.serializer()).hasCycle
    }

    // transform 为 null 时直接取订阅原文。
    fun chainProxyContext(workDir: File, transform: File?, ageSecretKey: String): ChainProxyContext =
        decodePayload(
            nativeChainProxyContext(workDir.path, transform?.path.orEmpty(), ageSecretKey),
            ChainProxyContext.serializer(),
        )

    // transform 只带覆写与链式代理，得到的是规则覆写之前的基线。
    fun ruleContext(workDir: File, transform: File?, ageSecretKey: String): RuleOverrideContext =
        decodePayload(
            nativeRuleContext(workDir.path, transform?.path.orEmpty(), ageSecretKey),
            RuleOverrideContext.serializer(),
        )

    // 内核未运行时代理页的静态预览，与 ruleContext 同一份配置口径，provider 成员从缓存文件补齐。
    fun proxyPreview(workDir: File, transform: File?, ageSecretKey: String): ProxyPreview =
        decodePayload(
            nativeProxyPreview(workDir.path, transform?.path.orEmpty(), ageSecretKey),
            ProxyPreview.serializer(),
        )

    private fun <T> decodePayload(raw: String?, serializer: KSerializer<T>): T {
        if (raw.isNullOrEmpty()) throw StellibertyCoreError("native returned empty result")
        if (raw.startsWith("error:")) throw StellibertyCoreError(raw.removePrefix("error:").trim())
        return runCatching { json.decodeFromString(serializer, raw) }
            .getOrElse { throw StellibertyCoreError("invalid native payload: $raw") }
    }

    @JvmStatic
    private external fun nativeValidateTransform(workDir: String, transform: String, secretKey: String): String?

    @JvmStatic
    private external fun nativeChainProxyContext(workDir: String, transform: String, secretKey: String): String?

    @JvmStatic
    private external fun nativeRuleContext(workDir: String, transform: String, secretKey: String): String?

    @JvmStatic
    private external fun nativeProxyPreview(workDir: String, transform: String, secretKey: String): String?

    @JvmStatic
    private external fun nativeSetAgeSecretKey(key: String)

    @JvmStatic
    private external fun nativeGenAgeKeyPair(): String?

    @JvmStatic
    private external fun nativeGenAgeHybridKeyPair(): String?

    private const val PROGRESS_POLL_INTERVAL_MS = 150L
}
