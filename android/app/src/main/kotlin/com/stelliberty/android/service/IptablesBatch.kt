package com.stelliberty.android.service

internal object IptablesBatch {
    private val staticCommand = Regex("^(ip6?tables) -w ([0-9]+) -t ([a-z]+) (-[NAI] .+)$")
    private const val DELIMITER = "STELLIBERTY_IPTABLES_BATCH"

    // 合并连续的静态建链、追加和挂链命令，条件分支与 Shell 展开保持原有执行顺序。
    fun apply(script: String): String = buildString {
        val batch = mutableListOf<String>()
        var prefix = ""
        var restore = ""
        var wait = ""
        var table = ""

        fun flush() {
            if (batch.size < 2) {
                batch.forEach { appendLine(it) }
            } else {
                appendLine("if command -v $restore >/dev/null 2>&1; then")
                appendLine("$restore -w $wait --noflush <<'$DELIMITER'")
                appendLine("*$table")
                batch.forEach { appendLine(it.removeSuffix(" 2>/dev/null").removePrefix(prefix)) }
                appendLine("COMMIT")
                appendLine(DELIMITER)
                appendLine("else")
                batch.forEach { appendLine(it) }
                appendLine("fi")
            }
            batch.clear()
        }

        script.lineSequence().forEach { line ->
            val command = line.removeSuffix(" 2>/dev/null")
            val match = staticCommand.matchEntire(command)
            if (match == null || command.any { it == '$' || it in "'`\\;&|<>()" }) {
                flush()
                appendLine(line)
            } else {
                val (binary, seconds, target, _) = match.destructured
                val nextPrefix = "$binary -w $seconds -t $target "
                if (prefix != nextPrefix) flush()
                prefix = nextPrefix
                restore = "$binary-restore"
                wait = seconds
                table = target
                batch.add(line)
            }
        }
        flush()
    }
}
