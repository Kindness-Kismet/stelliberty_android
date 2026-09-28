package com.stelliberty.android.service

import com.stelliberty.android.util.AppLogger
import java.util.concurrent.TimeUnit

object RootHelper {

    private const val TAG = "RootHelper"

    internal data class ShellOutcome(val code: Int, val output: String)

    // 必须开独立线程读输出：在当前线程读会一直阻塞到输出结束，而在等锁的子进程永远不会结束，
    // 后面那句带超时的等待就成了永远走不到的死代码。只等不读则会撑爆管道缓冲。
    internal fun awaitDrained(process: Process, timeoutSeconds: Long): ShellOutcome {
        val buffer = StringBuffer()
        val drain = Thread {
            runCatching {
                process.inputStream.bufferedReader().forEachLine { buffer.append(it).append('\n') }
            }
        }.apply { isDaemon = true; start() }

        val exited = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!exited) {
            process.destroyForcibly()
            drain.join(DRAIN_JOIN_MS)
            return ShellOutcome(-1, "<timeout>\n$buffer")
        }
        drain.join(DRAIN_JOIN_MS)
        return ShellOutcome(process.exitValue(), buffer.toString().trim())
    }

    private const val DRAIN_JOIN_MS = 500L

    fun hasRootAccess(): Boolean {
        return try {
            val process = ProcessBuilder("su", "-c", "id")
                .redirectErrorStream(true)
                .start()
            val exited = process.waitFor(3, TimeUnit.SECONDS)
            if (!exited) {
                process.destroyForcibly()
                return false
            }
            val output = process.inputStream.bufferedReader().readText()
            process.exitValue() == 0 && output.contains("uid=0")
        } catch (_: Exception) {
            false
        }
    }

    fun startAsRoot(binary: String, args: Array<String>, workDir: String, logFile: String): Int {
        val argsStr = args.joinToString(" ") { escapeShellSingleQuoted(it) }
        val command = "cd ${escapeShellSingleQuoted(workDir)} || exit 1; " +
                "${escapeShellSingleQuoted(binary)} $argsStr > ${escapeShellSingleQuoted(logFile)} 2>&1 & echo \$!"
        AppLogger.info(TAG, "Starting as root: ${redactArgs(args)}")
        return try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val reader = process.inputStream.bufferedReader()
            val pidLine = reader.readLine()?.trim() ?: ""
            val pid = pidLine.toIntOrNull() ?: -1
            AppLogger.info(TAG, "mihomo actual PID: $pid")
            process.inputStream.close()
            pid
        } catch (e: Exception) {
            AppLogger.error(TAG, "Failed to start as root: ${e.message}")
            -1
        }
    }

    fun readLogFile(logFile: String, maxLines: Int = 20): String {
        return try {
            val path = escapeShellSingleQuoted(logFile)
            val process = ProcessBuilder("su", "-c", "tail -n $maxLines $path 2>/dev/null")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor(3, TimeUnit.SECONDS)
            output.trim()
        } catch (_: Exception) {
            ""
        }
    }

    fun readRootCmdline(pid: Int): String {
        return try {
            val process = ProcessBuilder("su", "-c", "cat /proc/$pid/cmdline 2>/dev/null")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor(3, TimeUnit.SECONDS)
            output
        } catch (_: Exception) {
            ""
        }
    }

    fun isAliveAsRoot(pid: Int): Boolean {
        return try {
            val process = ProcessBuilder("su", "-c", "kill -0 $pid")
                .redirectErrorStream(true)
                .start()
            process.waitFor(3, TimeUnit.SECONDS)
            process.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }

    fun killAsRoot(pid: Int, tunDevice: String = "Stelliberty"): Boolean {
        try {
            AppLogger.info(TAG, "Killing root process: pid=$pid")
            // 判活留在同一次 su 内，避免轮询每次都支付提权开销；正常退出无需等满一个长轮询周期。
            val script = """
                kill -TERM $pid 2>/dev/null
                i=0
                while kill -0 $pid 2>/dev/null; do
                    [ ${'$'}i -ge 30 ] && break
                    sleep 0.1
                    i=${'$'}((i+1))
                done
                kill -0 $pid 2>/dev/null || exit 0
                kill -KILL $pid 2>/dev/null
                i=0
                while kill -0 $pid 2>/dev/null; do
                    [ ${'$'}i -ge 20 ] && exit 1
                    sleep 0.1
                    i=${'$'}((i+1))
                done
                ip link delete ${escapeShellSingleQuoted(tunDevice)} 2>/dev/null
                exit 2
            """.trimIndent()
            val process = ProcessBuilder("su", "-c", script).redirectErrorStream(true).start()
            val result = awaitDrained(process, 8)
            when (result.code) {
                0 -> AppLogger.info(TAG, "Process $pid terminated after SIGTERM")
                2 -> AppLogger.warn(TAG, "Process $pid terminated after SIGKILL")
                else -> {
                    AppLogger.error(TAG, "Failed to stop root process $pid: code=${result.code} ${result.output}")
                    return false
                }
            }
            return true
        } catch (e: Exception) {
            AppLogger.warn(TAG, "Failed to kill root process: ${e.message}")
            return false
        }
    }

    // 按 PID 停不掉时的最后手段。这两步本身就是收拾残局，再失败也没有下一招，出错只能咽下。
    fun killMihomoByName(tunDevice: String = "Stelliberty") {
        runCatching {
            AppLogger.warn(TAG, "Falling back to pkill for libmihomo_runner.so")
            runRootCommand("pkill -TERM -f libmihomo_runner.so")
            Thread.sleep(1000)
            runRootCommand("pkill -9 -f libmihomo_runner.so")
            cleanupRootNetwork(tunDevice)
        }
    }

    private fun cleanupRootNetwork(tunDevice: String) {
        runCatching {
            AppLogger.info(TAG, "Cleaning up root network state")
            runRootCommand("ip link delete ${escapeShellSingleQuoted(tunDevice)} 2>/dev/null; true")
        }
    }

    private fun runRootCommand(command: String): Boolean {
        return try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            process.waitFor(3, TimeUnit.SECONDS)
            process.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }

    // 上次被强杀的 mihomo 没机会清理，留下的 TUN 网卡会让下次启动创建网卡时报「已存在」，
    // 于是 mihomo 照常运行其他入口但实际没有网卡，界面显示已连接却上不了网。
    fun cleanupOrphanedMihomo(tunDevice: String? = null) {
        val tunCleanupLine = tunDevice?.let {
            "ip link delete ${escapeShellSingleQuoted(it)} 2>/dev/null; true"
        } ?: "true"

        val script = """
            pgrep -f libmihomo_runner.so >/dev/null 2>&1 && {
                pkill -TERM -f libmihomo_runner.so 2>/dev/null
                i=0; while [ ${'$'}i -lt 6 ]; do
                    sleep 0.5
                    pgrep -f libmihomo_runner.so >/dev/null 2>&1 || break
                    i=${'$'}((i+1))
                done
                pgrep -f libmihomo_runner.so >/dev/null 2>&1 && {
                    pkill -KILL -f libmihomo_runner.so 2>/dev/null
                    i=0; while [ ${'$'}i -lt 4 ]; do
                        sleep 0.5
                        pgrep -f libmihomo_runner.so >/dev/null 2>&1 || break
                        i=${'$'}((i+1))
                    done
                }
            }
            # 最后再删一次 TUN 网卡，避免下次启动时报「已存在」
            $tunCleanupLine
            pgrep -f libmihomo_runner.so >/dev/null 2>&1 && exit 1
            exit 0
        """.trimIndent()

        // 没有 root、su 被拒都会抛到这里，此时也没有别的办法清掉孤儿进程，记日志就够了。
        runCatching {
            val process = ProcessBuilder("su", "-c", script)
                .redirectErrorStream(true)
                .start()
            if (!process.waitFor(8, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                AppLogger.error(TAG, "cleanupOrphanedMihomo timed out")
                return
            }
            if (process.exitValue() == 1) {
                AppLogger.error(TAG, "Orphaned mihomo still alive after SIGKILL")
            }
        }
    }

    // 任何外部传进来的值送进 su -c 之前都要过这里，这是唯一一道防线。
    // 用双引号包起来是挡不住 $(...) 这种命令替换的。
    internal fun escapeShellSingleQuoted(s: String): String =
        "'" + s.replace("'", "'\\''") + "'"

    private fun redactArgs(args: Array<String>): String {
        var maskNext = false
        return args.joinToString(" ") { arg ->
            when {
                maskNext -> "***".also { maskNext = false }
                arg == "--secret" || arg == "--age-secret-key" -> arg.also { maskNext = true }
                else -> arg
            }
        }
    }

    fun rmRfAsRoot(path: String): Boolean {
        return try {
            val escaped = escapeShellSingleQuoted(path)
            val process = ProcessBuilder("su", "-c", "rm -rf $escaped")
                .redirectErrorStream(true)
                .start()
            val exited = process.waitFor(8, TimeUnit.SECONDS)
            if (!exited) {
                process.destroyForcibly()
                return false
            }
            process.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }

    fun runRootScriptHeredoc(script: String, timeoutSeconds: Long = 15): Int {
        return try {
            val process = ProcessBuilder("su")
                .redirectErrorStream(true)
                .start()
            process.outputStream.bufferedWriter().use { it.write(script); it.flush() }
            val (code, output) = awaitDrained(process, timeoutSeconds)
            if (code == -1) {
                AppLogger.error(TAG, "runRootScriptHeredoc timed out\noutput:\n${output.take(2000)}")
            } else if (code != 0 && output.isNotBlank()) {
                AppLogger.warn(TAG, "runRootScriptHeredoc code=$code output:\n${output.take(2000)}")
            }
            code
        } catch (e: Exception) {
            AppLogger.warn(TAG, "runRootScriptHeredoc failed: ${e.message}")
            -1
        }
    }
}
