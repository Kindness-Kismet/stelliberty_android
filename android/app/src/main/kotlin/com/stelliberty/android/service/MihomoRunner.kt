package com.stelliberty.android.service

import android.content.Context
import android.os.SystemClock
import com.stelliberty.android.R
import com.stelliberty.android.domain.model.LogLevel
import com.stelliberty.android.domain.model.LogSource
import com.stelliberty.android.platform.PlatformStorage
import com.stelliberty.android.platform.StorageKeys
import com.stelliberty.android.util.AppLogger
import java.io.File
import java.time.Instant
import java.time.OffsetDateTime
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

    // 进程归属与接口身份必须同时匹配，避免重连到复用端口或 PID 的其他进程。
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
        if (!MihomoApiProbe.isReady(pid, secret, externalController, timeoutMs = 1500)) {
            AppLogger.warn(TAG, "Attach failed: runtime identity check failed (pid=$pid)")
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

        try {
            val args = buildList {
                add("-d"); add(workDir.absolutePath)
                add("-f"); add(configFile.absolutePath)
                add("--override-json"); add(overrideJsonPath)
                if (transformPath != null) {
                    add("--transform"); add(transformPath)
                }
                add("--secret"); add(secret)
                add("--ext-ctl"); add(externalController)
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

            val result = waitForReady(useRoot, workDir)
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
        }
    }

    fun stop(): Boolean {
        if (childPid > 0) {
            AppLogger.info(TAG, "Stopping mihomo pid=$childPid (root=$isRootMode)")
            if (isRootMode) {
                val tunDevice = PlatformStorage(context).getString(StorageKeys.ROOT_TUN_DEVICE, "Stelliberty")
                if (!RootHelper.stopMihomo(tunDevice, childPid)) {
                    errorMessage = context.getString(R.string.error_root_stop_failed)
                    return false
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
        return true
    }

    private suspend fun waitForReady(useRoot: Boolean, workDir: File): String? {
        val deadline = SystemClock.elapsedRealtime() + STARTUP_TIMEOUT_MS
        var nextLivenessCheck = 0L
        while (SystemClock.elapsedRealtime() < deadline) {
            if (MihomoApiProbe.isReady(childPid, secret, externalController)) {
                val log = readStartupLog(useRoot, workDir)
                recordStartupLog(log)
                return scanLogForTunError(log)
            }
            if (SystemClock.elapsedRealtime() >= nextLivenessCheck) {
                val alive = if (useRoot) RootHelper.isAliveAsRoot(childPid) else isProcessAlive(childPid)
                if (!alive) {
                    val logContent = readStartupLog(useRoot, workDir)
                    recordStartupLog(logContent)
                    return if (logContent.isNotBlank()) {
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
        recordStartupLog(logContent)
        return if (logContent.isNotBlank()) {
            context.getString(R.string.error_mihomo_not_ready) + "\n" + extractErrorMessage(logContent)
        } else {
            context.getString(R.string.error_mihomo_not_ready)
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

    // API 就绪前 WebSocket 还没连上，这段内核日志只能从启动日志文件补进核心日志。
    // 不是 logrus 格式的连续行（崩溃调用栈）合并成一条错误。
    private fun recordStartupLog(log: String) {
        val raw = mutableListOf<String>()
        fun flushRaw() {
            if (raw.isEmpty()) return
            AppLogger.logs.append(LogSource.Core, LogLevel.Error, CORE_LOG_TAG, raw.joinToString("\n"))
            raw.clear()
        }
        log.lineSequence().filter { it.isNotBlank() }.forEach { line ->
            val match = STARTUP_LOG_LINE.find(line)
            if (match == null) {
                raw += line
                return@forEach
            }
            flushRaw()
            val (time, level, quoted, bare) = match.destructured
            AppLogger.logs.append(
                source = LogSource.Core,
                level = when (level) {
                    "trace", "debug" -> LogLevel.Debug
                    "info" -> LogLevel.Info
                    "warning" -> LogLevel.Warning
                    else -> LogLevel.Error
                },
                tag = CORE_LOG_TAG,
                message = bare.ifEmpty { unquoteGo(quoted) },
                receivedAt = runCatching { OffsetDateTime.parse(time).toInstant() }.getOrElse { Instant.now() },
            )
        }
        flushRaw()
    }

    // logrus 用 Go 的 %q 引用含空格的值，这里只还原日志里会出现的转义。
    private fun unquoteGo(text: String): String {
        if ('\\' !in text) return text
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i++]
            if (c != '\\' || i == text.length) {
                out.append(c)
                continue
            }
            when (val escaped = text[i++]) {
                'n' -> out.append('\n')
                't' -> out.append('\t')
                else -> out.append(escaped)
            }
        }
        return out.toString()
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
        private const val CORE_LOG_TAG = "mihomo"
        private val STARTUP_LOG_LINE = Regex("""^time="([^"]*)" level=(\w+) msg=(?:"((?:[^"\\]|\\.)*)"|(\S*))""")
    }
}
