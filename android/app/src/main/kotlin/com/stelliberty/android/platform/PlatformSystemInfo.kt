package com.stelliberty.android.platform

import android.os.SystemClock
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

data class NetworkInfoData(
    val localIp: String = "0.0.0.0",
    val interfaceName: String = "--",
)

class PlatformSystemInfo {

    private var prevCpuTime = 0L
    private var prevRealTime = 0L

    // 优先挑 198.18/198.19 网段的地址：那是 mihomo 虚拟网卡自己的网段，代理在跑时它才是要显示的那张网卡。
    // 没有就退回第一张可用网卡。枚举网卡会阻塞，只能在 IO 线程调用。
    fun getNetworkInfo(): NetworkInfoData = runCatching {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return NetworkInfoData()
        var fallback: NetworkInfoData? = null
        for (intf in interfaces) {
            if (intf.isLoopback || !intf.isUp) continue
            for (addr in intf.inetAddresses) {
                if (addr !is Inet4Address || addr.isLoopbackAddress) continue
                val ip = addr.hostAddress ?: continue
                val info = NetworkInfoData(localIp = ip, interfaceName = intf.name)
                if (ip.startsWith("198.18.") || ip.startsWith("198.19.")) return info
                if (fallback == null) fallback = info
            }
        }
        fallback ?: NetworkInfoData()
    }.getOrDefault(NetworkInfoData())

    fun getCpuUsage(pid: Int = -1): Float {
        if (pid <= 0) return -1f
        val line = readProcStat(pid) ?: return -1f
        return parseProcStat(line)
    }

    // ROOT 模式下 mihomo 是别的用户的进程，直接读会被 Android 10+ 的 hidepid 挡住，只能借 su 读。
    private fun readProcStat(pid: Int): String? {
        runCatching {
            File("/proc/$pid/stat").bufferedReader().use { it.readLine() }
        }.getOrNull()?.let { return it }

        return runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat /proc/$pid/stat"))
            val line = process.inputStream.bufferedReader().use { it.readLine() }
            process.waitFor()
            line
        }.getOrNull()
    }

    // 从 ')' 之后开始切字段：进程名本身可能带空格和括号，从行首数下标会错位。
    // 占用率要靠两次采样相减，所以首次调用只留基准、返回 -1 表示还没有结果。
    private fun parseProcStat(line: String): Float = runCatching {
        val parts = line.substringAfterLast(')').trim().split(" ")
        if (parts.size < 13) return -1f
        val cpuTime = parts[11].toLong() + parts[12].toLong()
        val realTime = SystemClock.elapsedRealtime()

        if (prevCpuTime == 0L) {
            prevCpuTime = cpuTime
            prevRealTime = realTime
            return -1f
        }

        val cpuDiff = cpuTime - prevCpuTime
        val timeDiff = realTime - prevRealTime
        prevCpuTime = cpuTime
        prevRealTime = realTime

        // 内核记的是时钟节拍，Android 上固定 100 Hz，一拍 10 毫秒，乘 10 换成毫秒再算占比。
        if (timeDiff == 0L) 0f
        else (cpuDiff.toFloat() * MILLIS_PER_TICK / timeDiff * 100).coerceIn(0f, 100f)
    }.getOrDefault(-1f)

    private companion object {
        const val MILLIS_PER_TICK = 10
    }
}
