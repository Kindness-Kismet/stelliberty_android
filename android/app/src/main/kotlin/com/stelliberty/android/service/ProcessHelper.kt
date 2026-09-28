package com.stelliberty.android.service

// 用 JNI 自己 fork+exec，是因为 Android 的 ProcessBuilder 会强制关掉所有非标准文件描述符，
// 而 VPN 模式必须把 TUN 的描述符传给 mihomo 子进程。
object ProcessHelper {

    init {
        System.loadLibrary("stelliberty")
    }

    external fun nativeForkExec(binary: String, args: Array<String>, workDir: String, logFile: String? = null): Int

    external fun nativeKill(pid: Int, force: Boolean)

    // 判断存活只能用 waitpid，不能看 /proc：mihomo 是本进程亲手 fork 出来的子进程，退出后会变成
    // 僵尸进程，而僵尸进程的 /proc 目录依然存在，照着看会永远以为它还活着。waitpid 顺带把僵尸收掉。
    external fun nativeIsAlive(pid: Int): Boolean

    external fun nativeWaitpid(pid: Int, timeoutMs: Int): Int
}
