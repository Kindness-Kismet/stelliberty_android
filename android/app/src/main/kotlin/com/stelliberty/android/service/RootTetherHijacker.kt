package com.stelliberty.android.service

import com.stelliberty.android.service.RootTetherHijacker.verifyClean
import com.stelliberty.android.util.AppLogger
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface

object RootTetherHijacker {

    private const val TAG = "RootTetherHijacker"

    private const val PRIORITY_V4 = 8000
    private const val PRIORITY_V6 = 8001
    private const val PRIORITY_RETURN_V4 = 8002
    private const val PRIORITY_RETURN_V6 = 8003
    private const val TUN_TABLE = RuntimeOverrideBuilder.ROOT_TUN_TABLE

    private const val BYPASS_GOTO_TARGET = RuntimeOverrideBuilder.ROOT_TUN_RULE_INDEX + 10

    internal const val TPROXY_PORT = 7895

    // 用第 24 位，避开系统网络守护进程占用的低 16 位（那里编码着网络编号和权限信息）。
    // 自己乱设标记会让路由命中一张没有默认路由的表，出站全部报「网络不可达」。
    private const val TPROXY_MARK = 0x01000000
    private const val TPROXY_MASK = 0x01000000

    private const val FWMARK_TABLE = 2024
    private const val PRIORITY_FWMARK = 7999

    private const val CHAIN_NAME = "stelliberty_tether"

    private const val CHAIN_DIVERT_NAME = "stelliberty_tether_divert"

    internal const val COMMENT_TAG_PREFIX = "stelliberty:tether:"

    const val DEFAULT_IFACES = "wlan1,wlan2"

    internal const val IPT_WAIT_SECONDS = 5

    private const val MAX_RULES_PER_PRIORITY = 32

    private const val SECTION_MARK = "--- "
    private const val IPT_LOCK_RETRIES = 3
    private const val IPT_LOCK_RETRY_DELAY_MS = 300L

    // 秒数必须跟在 -w 后面：不带秒数的 -w 是无限等待，一旦撞上别人占着锁，启动流程会永久挂住。
    private val IPT4 = "iptables -w $IPT_WAIT_SECONDS"
    private val IPT6 = "ip6tables -w $IPT_WAIT_SECONDS"
    private val IPT_BINS = listOf(IPT4, IPT6)

    private const val IPT_RESOURCE_PROBLEM_CODE = 4

    enum class Mode(val storageValue: String) {
        BYPASS("bypass"),
        PROXY("proxy");

        companion object {
            fun from(value: String): Mode = entries.firstOrNull { it.storageValue == value } ?: BYPASS
        }
    }

    // 抢不到防火墙锁的返回码和「内核不支持」是同一个，分不出来，所以撞锁就退避重试。
    // 试完仍是锁问题就当作支持：误判支持只是装不上加一条日志，误判不支持会直接把启动打死。
    fun probeTproxySupport(): Boolean {
        val probeName = "stelliberty_probe_${System.currentTimeMillis() % 1_000_000}"
        val cmd = "$IPT4 -t mangle -N $probeName 2>/dev/null; " +
                "$IPT4 -t mangle -A $probeName -p tcp -j TPROXY " +
                "--on-ip 127.0.0.1 --on-port 1 --tproxy-mark 0x1/0x1; " +
                "rc=\$?; " +
                "$IPT4 -t mangle -F $probeName 2>/dev/null; " +
                "$IPT4 -t mangle -X $probeName 2>/dev/null; " +
                "exit \$rc"
        repeat(IPT_LOCK_RETRIES) { attempt ->
            val r = runWithOutput(cmd, timeoutSeconds = IPT_WAIT_SECONDS + 5L)
            if (r.code == 0) {
                AppLogger.info(TAG, "probeTproxySupport: supported=true code=0 out=${r.output.oneLine()}")
                return true
            }
            if (!r.isXtablesLockFailure()) {
                AppLogger.info(TAG, "probeTproxySupport: supported=false code=${r.code} out=${r.output.oneLine()}")
                return false
            }
            AppLogger.warn(TAG, "probeTproxySupport: xtables lock busy (attempt ${attempt + 1}/$IPT_LOCK_RETRIES), retrying")
            Thread.sleep(IPT_LOCK_RETRY_DELAY_MS)
        }
        AppLogger.warn(TAG, "probeTproxySupport: xtables lock never released, assuming supported (apply will verify)")
        return true
    }

    fun apply(mode: Mode, interfaces: List<String>, tproxySupported: Boolean) {
        if (interfaces.isEmpty()) {
            AppLogger.info(TAG, "apply: no interfaces configured, skip (mode=$mode)")
            return
        }
        AppLogger.info(TAG, "Applying tether rules: mode=$mode tproxySupported=$tproxySupported ifaces=$interfaces")
        when (mode) {
            Mode.BYPASS -> applyBypass(interfaces)
            Mode.PROXY -> if (tproxySupported) applyTproxy(interfaces) else applyProxyFallback(interfaces)
        }
        dumpState()
    }

    // 绝不能查主路由表：系统的主表里没有默认路由（默认路由分散在各个上行接口自己的表里），
    // 命中主表会立刻丢包、完全没网。这里跳到底层库自己留的空标记之后，接着命中系统原生的转发规则。
    private fun applyBypass(interfaces: List<String>) =
        applyIpRuleAction(interfaces, action = "goto $BYPASS_GOTO_TARGET", label = "bypass")

    // 关键是那条把整个地址空间声明为「本机」的路由：没有它，带着标记的包找不到接收的套接字会被直接丢掉。
    // 已建立的连接走快速通道，只打标记就放行，省掉每个包重复查套接字。
    private fun applyTproxy(interfaces: List<String>) {
        runTeardownPass()

        val script = buildApplyTproxyScript(interfaces)
        val code = RootHelper.runRootScriptHeredoc(script, timeoutSeconds = 15)
        AppLogger.info(TAG, "applyTproxy finished code=$code ifaces=$interfaces")
    }

    private fun buildApplyTproxyScript(interfaces: List<String>): String {
        val sb = StringBuilder()
        sb.appendLine("#!/system/bin/sh")
        sb.appendLine("set +e")

        sb.appendLine("# === 1. create chains ===")
        for (bin in IPT_BINS) {
            sb.appendLine("$bin -t mangle -N $CHAIN_NAME 2>/dev/null")
            sb.appendLine("$bin -t mangle -N $CHAIN_DIVERT_NAME 2>/dev/null")
        }

        sb.appendLine("# === 2. DIVERT chain (fast path for ESTABLISHED) ===")
        for (bin in IPT_BINS) {
            sb.appendLine("$bin -t mangle -A $CHAIN_DIVERT_NAME -j MARK --set-xmark $TPROXY_MARK/$TPROXY_MASK ${tag("divert-mark")}")
            sb.appendLine("$bin -t mangle -A $CHAIN_DIVERT_NAME -j ACCEPT ${tag("divert-accept")}")
        }

        sb.appendLine("# === 3a. drop INVALID ===")
        for (bin in IPT_BINS) {
            sb.appendLine("$bin -t mangle -A $CHAIN_NAME -m conntrack --ctstate INVALID -j DROP ${tag("invalid-drop")} 2>/dev/null")
        }

        sb.appendLine("# === 3. intranet RETURN (except UDP/53) ===")
        for (net in IptablesIntranet.V4) {
            sb.appendLine("$IPT4 -t mangle -A $CHAIN_NAME -d $net -p udp ! --dport 53 -j RETURN ${tag("intranet-v4")}")
            sb.appendLine("$IPT4 -t mangle -A $CHAIN_NAME -d $net ! -p udp -j RETURN ${tag("intranet-v4")}")
        }
        for (net in IptablesIntranet.V6) {
            sb.appendLine("$IPT6 -t mangle -A $CHAIN_NAME -d $net -p udp ! --dport 53 -j RETURN ${tag("intranet-v6")} 2>/dev/null")
            sb.appendLine("$IPT6 -t mangle -A $CHAIN_NAME -d $net ! -p udp -j RETURN ${tag("intranet-v6")} 2>/dev/null")
        }

        sb.appendLine("# === 4. established socket → DIVERT ===")
        for (bin in IPT_BINS) {
            sb.appendLine("$bin -t mangle -A $CHAIN_NAME -p tcp -m socket -j $CHAIN_DIVERT_NAME ${tag("divert-match-tcp")}")
            sb.appendLine("$bin -t mangle -A $CHAIN_NAME -p udp -m socket -j $CHAIN_DIVERT_NAME ${tag("divert-match-udp")}")
        }

        sb.appendLine("# === 5. new flows → TPROXY ===")
        for (proto in listOf("tcp", "udp")) {
            sb.appendLine(
                "$IPT4 -t mangle -A $CHAIN_NAME -p $proto -j TPROXY " +
                        "--on-ip 127.0.0.1 --on-port $TPROXY_PORT --tproxy-mark $TPROXY_MARK/$TPROXY_MASK ${tag("tproxy-$proto")}"
            )
            sb.appendLine(
                "$IPT6 -t mangle -A $CHAIN_NAME -p $proto -j TPROXY " +
                        "--on-ip ::1 --on-port $TPROXY_PORT --tproxy-mark $TPROXY_MARK/$TPROXY_MASK ${tag("tproxy-$proto-v6")} 2>/dev/null"
            )
        }

        sb.appendLine("# === 6. attach PREROUTING per tether iface ===")
        for (iface in interfaces) {
            val esc = RootHelper.escapeShellSingleQuoted(iface)
            sb.appendLine("$IPT4 -t mangle -A PREROUTING -i $esc -j $CHAIN_NAME ${tag("prejump")}")
            sb.appendLine("$IPT6 -t mangle -A PREROUTING -i $esc -j $CHAIN_NAME ${tag("prejump-v6")} 2>/dev/null")
        }

        sb.appendLine("# === 7. fwmark policy routing ===")
        sb.appendLine("ip rule add fwmark $TPROXY_MARK/$TPROXY_MASK lookup $FWMARK_TABLE priority $PRIORITY_FWMARK 2>/dev/null")
        sb.appendLine("ip -6 rule add fwmark $TPROXY_MARK/$TPROXY_MASK lookup $FWMARK_TABLE priority $PRIORITY_FWMARK 2>/dev/null")
        sb.appendLine("ip route add local default dev lo table $FWMARK_TABLE 2>/dev/null")
        sb.appendLine("ip -6 route add local default dev lo table $FWMARK_TABLE 2>/dev/null")

        sb.appendLine("exit 0")
        return sb.toString()
    }

    private fun applyProxyFallback(interfaces: List<String>) =
        applyIpRuleAction(interfaces, action = "lookup $TUN_TABLE", label = "proxy-fallback")

    private fun applyIpRuleAction(interfaces: List<String>, action: String, label: String) {
        for (iface in interfaces) {
            val escaped = RootHelper.escapeShellSingleQuoted(iface)
            val v4 = runWithOutput("ip rule add iif $escaped $action priority $PRIORITY_V4")
            val v6 = runWithOutput("ip -6 rule add iif $escaped $action priority $PRIORITY_V6")
            AppLogger.info(TAG, "$label iif=$iface v4[code=${v4.code}, out=${v4.output.oneLine()}] v6[code=${v6.code}, out=${v6.output.oneLine()}]")

            val v4Subnets = resolveSubnets(iface, v6 = false)
            val v6Subnets = resolveSubnets(iface, v6 = true)
            for (net in v4Subnets) {
                val r = runWithOutput("ip rule add to $net $action priority $PRIORITY_RETURN_V4")
                AppLogger.info(TAG, "$label return to=$net code=${r.code} out=${r.output.oneLine()}")
            }
            for (net in v6Subnets) {
                val r = runWithOutput("ip -6 rule add to $net $action priority $PRIORITY_RETURN_V6")
                AppLogger.info(TAG, "$label return (v6) to=$net code=${r.code} out=${r.output.oneLine()}")
            }
            if (v4Subnets.isEmpty() && v6Subnets.isEmpty()) {
                AppLogger.warn(TAG, "iif=$iface has no resolvable subnet (interface down or no IP), return-path rule skipped")
            }
        }
    }

    private fun resolveSubnets(iface: String, v6: Boolean): List<String> {
        return try {
            val ni = NetworkInterface.getByName(iface) ?: return emptyList()
            ni.interfaceAddresses.mapNotNull { addr ->
                val inet = addr.address ?: return@mapNotNull null
                if (inet.isLoopbackAddress || inet.isLinkLocalAddress || inet.isAnyLocalAddress) return@mapNotNull null
                val isV4 = inet is Inet4Address
                val isV6 = inet is Inet6Address
                if (v6 && !isV6) return@mapNotNull null
                if (!v6 && !isV4) return@mapNotNull null
                val prefix = addr.networkPrefixLength.toInt()
                if (prefix <= 0) return@mapNotNull null
                cidrNetwork(inet.address, prefix)?.let { "$it/$prefix" }
            }.distinct()
        } catch (e: Exception) {
            AppLogger.warn(TAG, "resolveSubnets $iface failed", e)
            emptyList()
        }
    }

    private fun cidrNetwork(bytes: ByteArray, prefix: Int): String? {
        if (bytes.size != 4 && bytes.size != 16) return null
        val out = bytes.copyOf()
        for (i in out.indices) {
            val keep = (prefix - i * 8).coerceIn(0, 8)
            val mask = if (keep == 0) 0 else (0xFF shl (8 - keep)) and 0xFF
            out[i] = (out[i].toInt() and mask).toByte()
        }
        return if (out.size == 4) {
            out.joinToString(".") { (it.toInt() and 0xFF).toString() }
        } else {
            (0 until 8).joinToString(":") {
                val hi = out[it * 2].toInt() and 0xFF
                val lo = out[it * 2 + 1].toInt() and 0xFF
                ((hi shl 8) or lo).toString(16)
            }
        }
    }

    fun teardown() {
        AppLogger.info(TAG, "Tearing down tether rules")
        runTeardownPass()
        val report = verifyClean()
        if (report.residual.isEmpty()) {
            AppLogger.info(TAG, "teardown verified clean")
            return
        }
        AppLogger.warn(TAG, "teardown residual after first pass (${report.residual.size}): ${report.residual}, retrying")
        runTeardownPass()
        val retryReport = verifyClean()
        if (retryReport.residual.isEmpty()) {
            AppLogger.info(TAG, "teardown verified clean after retry")
        } else {
            AppLogger.warn(
                TAG,
                "teardown residual after retry (${retryReport.residual.size}): ${retryReport.residual} — manual intervention may be needed"
            )
        }
    }

    private fun runTeardownPass() {
        RootHelper.runRootScriptHeredoc(buildTeardownScript())
    }

    private fun buildTeardownScript(): String = buildString {
        appendLine("#!/system/bin/sh")
        for ((v6, priority) in listOf(
            false to PRIORITY_V4,
            true to PRIORITY_V6,
            false to PRIORITY_RETURN_V4,
            true to PRIORITY_RETURN_V6,
            false to PRIORITY_FWMARK,
            true to PRIORITY_FWMARK,
        )) {
            val ip = if (v6) "ip -6" else "ip"
            appendLine("i=0; while [ \$i -lt $MAX_RULES_PER_PRIORITY ] && $ip rule del priority $priority 2>/dev/null; do i=\$((i+1)); done")
        }
        for (bin in IPT_BINS) {
            appendLine(
                "$bin -t mangle -S PREROUTING 2>/dev/null | grep -- '-j $CHAIN_NAME' | " +
                        "sed 's/^-A/-D/' | while read -r r; do $bin -t mangle \$r 2>/dev/null; done"
            )
            for (chain in listOf(CHAIN_NAME, CHAIN_DIVERT_NAME)) {
                appendLine("$bin -t mangle -F $chain 2>/dev/null")
                appendLine("$bin -t mangle -X $chain 2>/dev/null")
            }
        }
        appendLine("ip route flush table $FWMARK_TABLE 2>/dev/null")
        appendLine("ip -6 route flush table $FWMARK_TABLE 2>/dev/null")
        appendLine("exit 0")
    }

    private data class TeardownReport(val residual: List<String>)

    private fun verifyClean(): TeardownReport {
        val probes = listOf(
            "rule4" to "ip rule show",
            "rule6" to "ip -6 rule show",
            "mangle4" to "$IPT4 -t mangle -S",
            "mangle6" to "$IPT6 -t mangle -S",
            "table4" to "ip route show table $FWMARK_TABLE",
            "table6" to "ip -6 route show table $FWMARK_TABLE",
        )
        val sections = runSections(probes)

        val residual = mutableListOf<String>()
        for (pri in listOf(PRIORITY_V4, PRIORITY_V6, PRIORITY_RETURN_V4, PRIORITY_RETURN_V6, PRIORITY_FWMARK)) {
            if (sections.hasRulePriority("rule4", pri)) residual += "v4 ip rule priority $pri"
            if (sections.hasRulePriority("rule6", pri)) residual += "v6 ip rule priority $pri"
        }
        for ((key, bin) in listOf("mangle4" to "iptables", "mangle6" to "ip6tables")) {
            val mangle = sections[key].orEmpty()
            if (mangle.contains(CHAIN_NAME)) residual += "$bin mangle chain $CHAIN_NAME present"
            if (mangle.contains(CHAIN_DIVERT_NAME)) residual += "$bin mangle chain $CHAIN_DIVERT_NAME present"
        }
        if (sections["table4"]?.isNotBlank() == true) residual += "v4 route table $FWMARK_TABLE non-empty"
        if (sections["table6"]?.isNotBlank() == true) residual += "v6 route table $FWMARK_TABLE non-empty"
        return TeardownReport(residual)
    }

    private fun Map<String, String>.hasRulePriority(section: String, priority: Int): Boolean =
        this[section]?.lineSequence()?.any { it.startsWith("$priority:") } == true

    private fun runSections(probes: List<Pair<String, String>>): Map<String, String> {
        val script = probes.joinToString("\n") { (key, cmd) -> "echo '$SECTION_MARK$key'; $cmd" }
        val sections = mutableMapOf<String, StringBuilder>()
        var current: StringBuilder? = null
        runWithOutput(script).output.lineSequence().forEach { line ->
            if (line.startsWith(SECTION_MARK)) {
                current = StringBuilder().also { sections[line.removePrefix(SECTION_MARK)] = it }
            } else {
                current?.appendLine(line)
            }
        }
        return sections.mapValues { it.value.toString().trim() }
    }

    fun parseInterfaces(raw: String): List<String> =
        raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    fun anyRulesPresent(): Boolean {
        val mangle4 = runWithOutput("iptables -t mangle -S").output
        if (mangle4.contains(COMMENT_TAG_PREFIX) || mangle4.contains(CHAIN_NAME)) return true
        val mangle6 = runWithOutput("ip6tables -t mangle -S").output
        if (mangle6.contains(COMMENT_TAG_PREFIX) || mangle6.contains(CHAIN_NAME)) return true
        val v4Rules = runWithOutput("ip rule show").output
        if (v4Rules.lineSequence().any { it.startsWith("$PRIORITY_V4:") || it.startsWith("$PRIORITY_FWMARK:") }) {
            return true
        }
        return false
    }

    private fun tag(label: String): String = "-m comment --comment \"$COMMENT_TAG_PREFIX$label\""

    private fun dumpState() {
        val script = listOf(
            "ip rule show (v4)" to "ip rule show",
            "ip -6 rule show" to "ip -6 rule show",
            "route table main" to "ip route show table main",
            "route table $TUN_TABLE" to "ip route show table $TUN_TABLE",
            "route table $FWMARK_TABLE" to "ip route show table $FWMARK_TABLE",
            "iptables mangle -S" to "$IPT4 -t mangle -S",
        ).joinToString("\n") { (label, cmd) -> "echo '$SECTION_MARK$label'; $cmd" }
        AppLogger.info(TAG, "tether state dump:\n${runWithOutput(script).output}")
    }

    private data class ShellResult(val code: Int, val output: String)

    private fun ShellResult.isXtablesLockFailure(): Boolean =
        code == IPT_RESOURCE_PROBLEM_CODE || output.contains("xtables lock", ignoreCase = true)

    private val IPT_WAIT_PRESENT = Regex("""\s-w\s+\d""")

    private fun withIptablesWait(command: String): String {
        if (!command.startsWith("iptables ") && !command.startsWith("ip6tables ")) return command
        if (IPT_WAIT_PRESENT.containsMatchIn(command)) return command
        return command.replaceFirst(Regex("^(ip6?tables)\\s+"), "$1 -w $IPT_WAIT_SECONDS ")
    }

    private fun runWithOutput(command: String, timeoutSeconds: Long = IPT_WAIT_SECONDS + 5L): ShellResult {
        return try {
            val process = ProcessBuilder("su", "-c", withIptablesWait(command))
                .redirectErrorStream(true)
                .start()
            val (code, output) = RootHelper.awaitDrained(process, timeoutSeconds)
            ShellResult(code, output)
        } catch (e: Exception) {
            ShellResult(-1, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun String.oneLine(): String =
        if (isEmpty()) "<ok>" else replace('\n', '|').take(120)
}
