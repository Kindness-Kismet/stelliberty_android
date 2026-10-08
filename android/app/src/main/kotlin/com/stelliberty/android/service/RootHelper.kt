package com.stelliberty.android.service

import com.stelliberty.android.BuildConfig
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

    fun readLogFile(logFile: String, maxLines: Int = 20): String =
        readAsRoot("tail -n $maxLines ${escapeShellSingleQuoted(logFile)} 2>/dev/null")

    // 与 File.readEndLines 相同：开头与末尾各取一段，总行数不超过两段之和时返回全文。
    fun readLogEnds(logFile: String, headLines: Int, tailLines: Int): String {
        val path = escapeShellSingleQuoted(logFile)
        return readAsRoot(
            "n=\$(wc -l < $path 2>/dev/null) || exit 0; " +
                "if [ \"\$n\" -le ${headLines + tailLines} ]; then cat $path; " +
                "else head -n $headLines $path; tail -n $tailLines $path; fi"
        )
    }

    private fun readAsRoot(command: String): String {
        return try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor(3, TimeUnit.SECONDS)
            output.trim()
        } catch (_: Exception) {
            ""
        }
    }

    fun isAliveAsRoot(pid: Int): Boolean {
        return try {
            val script = RootProcessScript.isAlive(BuildConfig.APPLICATION_ID, pid)
            val process = ProcessBuilder("su", "-c", script).redirectErrorStream(true).start()
            awaitDrained(process, 3).code == 0
        } catch (_: Exception) {
            false
        }
    }

    fun stopMihomo(tunDevice: String, pid: Int? = null): Boolean {
        return try {
            AppLogger.info(TAG, "Stopping owned root mihomo processes: pid=$pid")
            val script = RootProcessScript.stop(BuildConfig.APPLICATION_ID, tunDevice, pid)
            val process = ProcessBuilder("su", "-c", script).redirectErrorStream(true).start()
            val result = awaitDrained(process, 8)
            if (result.code != 0) {
                AppLogger.error(TAG, "Failed to stop root mihomo: code=${result.code} ${result.output}")
            }
            result.code == 0
        } catch (e: Exception) {
            AppLogger.error(TAG, "Failed to stop root mihomo", e)
            false
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
