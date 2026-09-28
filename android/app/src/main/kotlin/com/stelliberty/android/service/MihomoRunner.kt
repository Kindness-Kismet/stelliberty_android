package com.stelliberty.android.service

import android.content.Context
import android.os.SystemClock
import com.stelliberty.android.R
import com.stelliberty.android.platform.PlatformStorage
import com.stelliberty.android.platform.StorageKeys
import com.stelliberty.android.util.AppLogger
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MihomoRunner(private val context: Context) {

    private var childPid: Int = -1
    private var isRootMode = false
    val pid: Int get() = childPid
    var secret: String = ""
    var externalController: String = "127.0.0.1:9090"
    var activeSubscriptionId: String? = null
        private set
    var errorMessage: String = ""
        private set

    val isRunning: Boolean
        // 两条分支的代价差一个量级：VPN 那条只是查一下本进程的子进程，几乎免费；ROOT 那条每次都要
        // 启动一个 su 进程，因为 Android 10 以后普通应用读不到 root 进程的 /proc。轮询它的地方要自己控制频率。
        get() = childPid > 0 && if (isRootMode) RootHelper.isAliveAsRoot(childPid) else isProcessAlive(childPid)

    // 重连上次留下的 mihomo 进程，三道检查全过才算成功：进程还活着、命令行确实是 mihomo
    // （防止 PID 被别的进程复用）、用存下来的密码能通过接口鉴权（防止密码变了却显示已连接）。
    fun attachToExisting(
        pid: Int,
        secret: String,
        externalController: String,
        subscriptionId: String?,
    ): Boolean {
        if (pid <= 0 || !RootHelper.isAliveAsRoot(pid)) {
            AppLogger.warn(TAG, "Attach failed: pid dead (pid=$pid)")
            return false
        }
        val cmdline = RootHelper.readRootCmdline(pid)
        if (!cmdline.contains("libmihomo_runner.so")) {
            AppLogger.warn(TAG, "Attach failed: wrong cmdline (pid=$pid, cmdline=${cmdline.take(64)})")
            return false
        }
        if (!isApiAuthorized(secret, externalController)) {
            AppLogger.warn(TAG, "Attach failed: auth failed (pid=$pid)")
            return false
        }
        childPid = pid
        isRootMode = true
        this.secret = secret
        this.externalController = externalController
        activeSubscriptionId = subscriptionId
        AppLogger.info(TAG, "Attached to existing mihomo process: pid=$pid")
        return true
    }

    suspend fun start(
        subscriptionId: String? = null,
        useRoot: Boolean = false,
        overrideJsonPath: String,
        secret: String,
        externalController: String,
        ageSecretKey: String = "",
        transformPath: String? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        if (isRunning) {
            AppLogger.warn(TAG, "mihomo already running")
            return@withContext true
        }

        val binary = getMihomoBinary() ?: run {
            errorMessage = context.getString(R.string.error_mihomo_not_found)
            AppLogger.error(TAG, errorMessage)
            return@withContext false
        }

        isRootMode = useRoot
        activeSubscriptionId = subscriptionId
        this@MihomoRunner.secret = secret
        this@MihomoRunner.externalController = externalController

        val workDir = when {
            useRoot && subscriptionId != null -> ProfileFileOps.getRuntimeDir(context, subscriptionId)
            subscriptionId != null -> ProfileFileOps.getSubscriptionDir(context, subscriptionId)
            else -> ConfigGenerator.getWorkDir(context)
        }
        val configFile = File(workDir, "config.yaml")

        if (!useRoot) {
            ProfileFileOps.ensureGeodataLinks(context, workDir)
        }

        var readyFile: File? = null
        try {
            readyFile = File.createTempFile("mihomo-ready-", ".status", context.cacheDir)
            val args = buildList {
                add("-d"); add(workDir.absolutePath)
                add("-f"); add(configFile.absolutePath)
                add("--override-json"); add(overrideJsonPath)
                if (transformPath != null) {
                    add("--transform"); add(transformPath)
                }
                add("--secret"); add(secret)
                add("--ext-ctl"); add(externalController)
                add("--ready-file"); add(readyFile.absolutePath)
                if (ageSecretKey.isNotEmpty()) {
                    add("--age-secret-key"); add(ageSecretKey)
                }
            }.toTypedArray()
            val logFile = File(workDir, "mihomo.log")

            childPid = if (useRoot) {
                RootHelper.startAsRoot(
                    binary.absolutePath,
                    args,
                    workDir.absolutePath,
                    logFile.absolutePath
                )
            } else {
                ProcessHelper.nativeForkExec(
                    binary.absolutePath,
                    args,
                    workDir.absolutePath,
                    logFile.absolutePath,
                )
            }

            if (childPid <= 0) {
                errorMessage =
                    if (useRoot) context.getString(R.string.error_root_start_failed) else context.getString(R.string.error_fork_failed)
                AppLogger.error(TAG, errorMessage)
                return@withContext false
            }

            AppLogger.info(TAG, "mihomo child pid=$childPid (root=$useRoot)")

            val result = waitForReady(useRoot, workDir, readyFile)
            if (result != null) {
                errorMessage = result
                AppLogger.error(TAG, errorMessage)
                stop()
                return@withContext false
            }

            errorMessage = ""
            AppLogger.info(TAG, "mihomo started: pid=$childPid (root=$useRoot)")
            true
        } catch (e: CancellationException) {
            stop()
            throw e
        } catch (e: Exception) {
            stop()
            errorMessage = context.getString(R.string.error_generic_start_failed, e.message ?: "")
            AppLogger.error(TAG, "Failed to start mihomo", e)
            false
        } finally {
            readyFile?.delete()
        }
    }

    fun stop() {
        if (childPid > 0) {
            AppLogger.info(TAG, "Stopping mihomo pid=$childPid (root=$isRootMode)")
            if (isRootMode) {
                val tunDevice = PlatformStorage(context).getString(StorageKeys.ROOT_TUN_DEVICE, "Stelliberty")
                val killed = RootHelper.killAsRoot(childPid, tunDevice)
                if (!killed) {
                    RootHelper.killMihomoByName(tunDevice)
                }
            } else {
                ProcessHelper.nativeKill(childPid, force = false)
                if (ProcessHelper.nativeWaitpid(childPid, GRACEFUL_STOP_TIMEOUT_MS) < 0) {
                    AppLogger.warn(TAG, "mihomo pid=$childPid ignored SIGTERM, escalating to SIGKILL")
                    ProcessHelper.nativeKill(childPid, force = true)
                    ProcessHelper.nativeWaitpid(childPid, FORCE_STOP_TIMEOUT_MS)
                }
            }
            childPid = -1
        }
        secret = ""
        isRootMode = false
    }

    // 独立就绪文件不受日志级别、日志滚动和提权读取开销影响。
    private suspend fun waitForReady(useRoot: Boolean, workDir: File, readyFile: File): String? {
        val deadline = SystemClock.elapsedRealtime() + STARTUP_TIMEOUT_MS
        var nextLivenessCheck = 0L
        while (SystemClock.elapsedRealtime() < deadline) {
            if (readyFile.length() > 0 && isApiReady()) {
                val log = readStartupLog(useRoot, workDir)
                return scanLogForTunError(log)
            }
            if (SystemClock.elapsedRealtime() >= nextLivenessCheck) {
                val alive = if (useRoot) RootHelper.isAliveAsRoot(childPid) else isProcessAlive(childPid)
                if (!alive) {
                    val logContent = readStartupLog(useRoot, workDir)
                    return if (logContent.isNotBlank()) {
                        AppLogger.error(TAG, "mihomo log:\n$logContent")
                        context.getString(R.string.error_mihomo_start_failed, extractErrorMessage(logContent))
                    } else {
                        context.getString(R.string.error_mihomo_exited)
                    }
                }
                nextLivenessCheck = SystemClock.elapsedRealtime() + LIVENESS_CHECK_INTERVAL_MS
            }
            delay(READY_POLL_INTERVAL_MS)
        }
        val logContent = readStartupLog(useRoot, workDir)
        return if (logContent.isNotBlank()) {
            AppLogger.warn(TAG, "API timeout, mihomo log:\n$logContent")
            context.getString(R.string.error_api_not_ready) + "\n" + extractErrorMessage(logContent)
        } else {
            context.getString(R.string.error_api_not_ready)
        }
    }

    private fun scanLogForTunError(log: String): String? {
        if (log.isBlank()) return null
        val tunErrorPatterns = listOf(
            "Start TUN listening error",
            "configure tun interface",
            "create NetworkUpdateMonitor",
        )
        val errorLine = log.lines().firstOrNull { line ->
            (line.contains("level=error") || line.contains("level=fatal")) &&
                    tunErrorPatterns.any { line.contains(it, ignoreCase = true) }
        } ?: return null
        AppLogger.error(TAG, "TUN init failed: $errorLine")
        return context.getString(R.string.error_tun_init_failed, extractErrorMessage(errorLine))
    }

    // 200 行够装下 Go 崩溃调用栈（通常三五十行）加崩溃前的首条错误，再少会被调用栈挤掉。
    private fun readStartupLog(useRoot: Boolean, workDir: File): String {
        val logFile = File(workDir, "mihomo.log")
        return if (useRoot && !logFile.canRead()) {
            RootHelper.readLogFile(logFile.absolutePath, STARTUP_LOG_LINES)
        } else {
            logFile.readLastLines(STARTUP_LOG_LINES)
        }
    }

    private fun extractErrorMessage(logContent: String): String {
        val errorLines = logContent.lines().filter {
            it.contains("level=error") || it.contains("level=fatal")
        }
        if (errorLines.isEmpty()) return logContent.lines().takeLast(5).joinToString("\n")

        val msgRegex = Regex("""msg="(.+?)"""")
        val messages = errorLines.mapNotNull { msgRegex.find(it)?.groupValues?.get(1) }
        return if (messages.isNotEmpty()) messages.joinToString("\n") else errorLines.joinToString("\n")
    }

    private fun isApiReady(): Boolean {
        return try {
            val conn = URL("http://$externalController/version").openConnection() as HttpURLConnection
            conn.connectTimeout = 500
            conn.readTimeout = 500
            conn.responseCode
            conn.disconnect()
            true
        } catch (_: Exception) {
            false
        }
    }

    // 用 /configs 而不是 /version 来验密码：/version 在某些 mihomo 版本上不要求鉴权，验不出真假。
    private fun isApiAuthorized(secret: String, externalController: String, timeoutMs: Int = 1500): Boolean {
        return try {
            val conn = URL("http://$externalController/configs").openConnection() as HttpURLConnection
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("Authorization", "Bearer $secret")
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (_: Exception) {
            false
        }
    }

    private fun isProcessAlive(pid: Int): Boolean = ProcessHelper.nativeIsAlive(pid)

    private fun getMihomoBinary(): File? {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val binary = File(nativeDir, "libmihomo_runner.so")
        if (binary.exists()) return binary
        return null
    }

    companion object {
        private const val TAG = "MihomoRunner"

        private const val LIVENESS_CHECK_INTERVAL_MS = 2000L
        private const val READY_POLL_INTERVAL_MS = 100L
        private const val STARTUP_TIMEOUT_MS = 10_000L

        private const val GRACEFUL_STOP_TIMEOUT_MS = 3000
        private const val FORCE_STOP_TIMEOUT_MS = 500

        private const val STARTUP_LOG_LINES = 200
    }
}
