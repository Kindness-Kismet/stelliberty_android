package com.stelliberty.android.service

import com.stelliberty.android.util.AppLogger

internal object RootDnsHijacker {
    const val PORT = 1053
    private const val TAG = "RootDnsHijacker"
    private const val CHAIN_PRE = "stelliberty_dns_pre"
    private const val CHAIN_OUT = "stelliberty_dns_out"
    private const val CHAIN_V6_FALLBACK = "stelliberty_dns_v6"
    private val IPT4 = "iptables -w ${RootTetherHijacker.IPT_WAIT_SECONDS}"
    private val IPT6 = "ip6tables -w ${RootTetherHijacker.IPT_WAIT_SECONDS}"

    fun probeIpv6Nat(): Boolean? {
        val result = runCommand("$IPT6 -t nat -S", RootTetherHijacker.IPT_WAIT_SECONDS + 5L)
        if (result.code == 0) return true
        if (result.code > 0 && result.output.contains("can't initialize ip6tables table", ignoreCase = true) &&
            result.output.contains("nat", ignoreCase = true) &&
            result.output.contains("Table does not exist", ignoreCase = true)
        ) return false
        AppLogger.warn(TAG, "IPv6 DNS NAT probe inconclusive: code=${result.code} ${result.output}")
        return null
    }

    fun apply(appUid: Int, ipv6Nat: Boolean, fallbackInterfaces: List<String>, includeForwarded: Boolean): Boolean {
        val script = buildString {
            appendLine("#!/system/bin/sh")
            appendLine("set +e")
            appendTeardown(this)
            appendLine("set -e")
            appendApply(this, appUid, ipv6Nat, includeForwarded)
            if (fallbackInterfaces.isNotEmpty()) appendIpv6Fallback(this, appUid, fallbackInterfaces, check = false)
        }
        val code = RootHelper.runRootScriptHeredoc(script)
        AppLogger.info(TAG, "DNS interception finished code=$code")
        return code == 0
    }

    fun rulesPresent(appUid: Int, ipv6Nat: Boolean, fallbackInterfaces: List<String>, includeForwarded: Boolean): Boolean {
        val script = buildString {
            appendLine("#!/system/bin/sh")
            appendLine("set -e")
            appendChecks(this, appUid, ipv6Nat, includeForwarded)
            if (fallbackInterfaces.isNotEmpty()) appendIpv6Fallback(this, appUid, fallbackInterfaces, check = true)
        }
        return RootHelper.runRootScriptHeredoc(script) == 0
    }

    private fun appendApply(sb: StringBuilder, appUid: Int, ipv6Nat: Boolean, includeForwarded: Boolean) {
        for (ipt in tables(ipv6Nat)) {
            sb.appendLine("$ipt -t nat -N $CHAIN_OUT")
            // netd 同样使用 UID 0，只有内核的独立 GID 和应用自身可以绕过 DNS 劫持。
            sb.appendLine("$ipt -t nat -A $CHAIN_OUT -m owner --gid-owner $appUid -j RETURN ${tag("bypass-core")}")
            sb.appendLine("$ipt -t nat -A $CHAIN_OUT -m owner --uid-owner $appUid -j RETURN ${tag("bypass-self")}")
            for (protocol in listOf("udp", "tcp")) {
                sb.appendLine("$ipt -t nat -A $CHAIN_OUT -p $protocol --dport 53 -j REDIRECT --to-ports $PORT ${tag("out-$protocol")}")
            }
            sb.appendLine("$ipt -t nat -I OUTPUT -j $CHAIN_OUT")
            if (includeForwarded) {
                sb.appendLine("$ipt -t nat -N $CHAIN_PRE")
                for (protocol in listOf("udp", "tcp")) {
                    sb.appendLine("$ipt -t nat -A $CHAIN_PRE -p $protocol --dport 53 -j REDIRECT --to-ports $PORT ${tag("pre-$protocol")}")
                }
                sb.appendLine("$ipt -t nat -I PREROUTING -j $CHAIN_PRE")
            }
        }
    }

    private fun appendChecks(sb: StringBuilder, appUid: Int, ipv6Nat: Boolean, includeForwarded: Boolean) {
        for (ipt in tables(ipv6Nat)) {
            sb.appendLine("$ipt -t nat -C OUTPUT -j $CHAIN_OUT")
            sb.appendLine("$ipt -t nat -C $CHAIN_OUT -m owner --gid-owner $appUid -j RETURN ${tag("bypass-core")}")
            sb.appendLine("$ipt -t nat -C $CHAIN_OUT -m owner --uid-owner $appUid -j RETURN ${tag("bypass-self")}")
            for (protocol in listOf("udp", "tcp")) {
                sb.appendLine("$ipt -t nat -C $CHAIN_OUT -p $protocol --dport 53 -j REDIRECT --to-ports $PORT ${tag("out-$protocol")}")
            }
            if (includeForwarded) {
                sb.appendLine("$ipt -t nat -C PREROUTING -j $CHAIN_PRE")
                for (protocol in listOf("udp", "tcp")) {
                    sb.appendLine("$ipt -t nat -C $CHAIN_PRE -p $protocol --dport 53 -j REDIRECT --to-ports $PORT ${tag("pre-$protocol")}")
                }
            }
        }
    }

    private fun appendIpv6Fallback(sb: StringBuilder, appUid: Int, interfaces: List<String>, check: Boolean) {
        val operation = if (check) "-C" else "-A"
        if (!check) sb.appendLine("$IPT6 -t filter -N $CHAIN_V6_FALLBACK")
        sb.appendLine("$IPT6 -t filter $operation $CHAIN_V6_FALLBACK -m owner --gid-owner $appUid -j RETURN ${tag("v6-bypass-core")}")
        sb.appendLine("$IPT6 -t filter $operation $CHAIN_V6_FALLBACK -m owner --uid-owner $appUid -j RETURN ${tag("v6-bypass-self")}")
        for (iface in interfaces) {
            val escaped = RootHelper.escapeShellSingleQuoted(iface)
            // 部分系统会追加未公布的 IPv6 DNS；按有 IPv4 DNS 备选的接口回退，不能只匹配已公布的地址。
            sb.appendLine("$IPT6 -t filter $operation $CHAIN_V6_FALLBACK -o $escaped -p udp --dport 53 -j REJECT --reject-with icmp6-port-unreachable ${tag("v6-fallback-udp")}")
            sb.appendLine("$IPT6 -t filter $operation $CHAIN_V6_FALLBACK -o $escaped -p tcp --dport 53 -j REJECT --reject-with tcp-reset ${tag("v6-fallback-tcp")}")
        }
        sb.appendLine("$IPT6 -t filter ${if (check) "-C" else "-I"} OUTPUT -j $CHAIN_V6_FALLBACK")
    }

    fun flushNetworkCache(netId: Int): Boolean {
        // minSdk 31 起的稳定 IDnsResolver AIDL 将 flushNetworkCache 固定为事务 11。
        val result = runCommand("service call dnsresolver 11 i32 $netId", 5)
        val success = result.code == 0 && SUCCESS_PARCEL.matches(result.output)
        if (!success) AppLogger.warn(TAG, "System DNS cache flush failed: netId=$netId code=${result.code} ${result.output}")
        return success
    }

    fun appendTeardown(sb: StringBuilder) {
        for (ipt in listOf(IPT4, IPT6)) {
            sb.appendLine("while $ipt -t nat -D PREROUTING -j $CHAIN_PRE 2>/dev/null; do :; done")
            sb.appendLine("while $ipt -t nat -D OUTPUT -j $CHAIN_OUT 2>/dev/null; do :; done")
            for (chain in listOf(CHAIN_PRE, CHAIN_OUT)) {
                sb.appendLine("$ipt -t nat -F $chain 2>/dev/null")
                sb.appendLine("$ipt -t nat -X $chain 2>/dev/null")
            }
        }
        sb.appendLine("while $IPT6 -t filter -D OUTPUT -j $CHAIN_V6_FALLBACK 2>/dev/null; do :; done")
        sb.appendLine("$IPT6 -t filter -F $CHAIN_V6_FALLBACK 2>/dev/null")
        sb.appendLine("$IPT6 -t filter -X $CHAIN_V6_FALLBACK 2>/dev/null")
    }

    private fun runCommand(command: String, timeoutSeconds: Long): RootHelper.ShellOutcome = try {
        val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        RootHelper.awaitDrained(process, timeoutSeconds)
    } catch (e: Exception) {
        RootHelper.ShellOutcome(-1, e.message ?: e.javaClass.simpleName)
    }

    private fun tables(ipv6Nat: Boolean): List<String> =
        if (ipv6Nat) listOf(IPT4, IPT6) else listOf(IPT4)

    private fun tag(label: String): String = "-m comment --comment stelliberty:dns:$label"

    private val SUCCESS_PARCEL = Regex("""Result:\s*Parcel\(\s*00000000\s+'[^']*'\s*\)""")
}
