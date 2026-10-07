package com.stelliberty.android.service

internal object RootProcessScript {
    // 可执行文件确认是 runner，启动参数里的应用数据目录确认归属。安装包更新后旧安装目录可能被改名
    // （如 ==deleted==），exe 路径里不再有包名，数据目录则跨更新不变。
    private fun identity(packageName: String): String = """
        data_dir=${RootHelper.escapeShellSingleQuoted("/$packageName/files/mihomo/")}
        is_mihomo() {
            executable=${'$'}(readlink /proc/"${'$'}1"/exe 2>/dev/null) || return 1
            case "${'$'}executable" in
                /data/app/*/lib/*/libmihomo_runner.so|/data/app/*/lib/*/libmihomo_runner.so\ \(deleted\)) ;;
                *) return 1 ;;
            esac
            grep -qaF "${'$'}data_dir" /proc/"${'$'}1"/cmdline 2>/dev/null
        }
    """.trimIndent()

    fun isAlive(packageName: String, pid: Int): String = identity(packageName) + "\nis_mihomo $pid"

    fun stop(packageName: String, tunDevice: String, pid: Int?): String {
        val candidates = pid?.toString() ?: "${'$'}(pidof libmihomo_runner.so)"
        return identity(packageName) + "\n" + """
            remaining=""
            for pid in $candidates; do
                is_mihomo "${'$'}pid" && remaining="${'$'}remaining ${'$'}pid"
            done
            for signal in TERM KILL; do
                [ -z "${'$'}remaining" ] && break
                for pid in ${'$'}remaining; do
                    is_mihomo "${'$'}pid" && kill -"${'$'}signal" "${'$'}pid" 2>/dev/null
                done
                attempts=30
                [ "${'$'}signal" = KILL ] && attempts=20
                i=0
                while [ "${'$'}i" -lt "${'$'}attempts" ]; do
                    alive=""
                    for pid in ${'$'}remaining; do
                        is_mihomo "${'$'}pid" && alive="${'$'}alive ${'$'}pid"
                    done
                    remaining="${'$'}alive"
                    [ -z "${'$'}remaining" ] && break
                    sleep 0.1
                    i=${'$'}((i+1))
                done
            done
            if [ -n "${'$'}remaining" ]; then
                echo "Failed to stop mihomo PIDs:${'$'}remaining"
                exit 1
            fi
            ip link delete ${RootHelper.escapeShellSingleQuoted(tunDevice)} 2>/dev/null
            exit 0
        """.trimIndent()
    }
}
