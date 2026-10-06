package com.stelliberty.android.service

internal object RootProcessScript {
    // 可执行文件标识实际程序；安装包更新后仍存活的进程会带 (deleted) 后缀。
    private fun identity(packageName: String): String = """
        package_name=${RootHelper.escapeShellSingleQuoted(packageName)}
        is_mihomo() {
            executable=${'$'}(readlink /proc/"${'$'}1"/exe 2>/dev/null) || return 1
            case "${'$'}executable" in
                /data/app/*/"${'$'}package_name"-*/lib/*/libmihomo_runner.so|/data/app/*/"${'$'}package_name"-*/lib/*/libmihomo_runner.so\ \(deleted\)) return 0 ;;
                *) return 1 ;;
            esac
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
