package com.stelliberty.android.service

import com.stelliberty.android.util.AppLogger

internal object RootDnsHijacker {
    const val PORT = 1053
    private const val TAG = "RootDnsHijacker"
    private const val CHAIN_PRE = "stelliberty_dns_pre"
    private const val CHAIN_OUT = "stelliberty_dns_out"
    private val IPT4 = "iptables -w ${RootTetherHijacker.IPT_WAIT_SECONDS}"
    private val IPT6 = "ip6tables -w ${RootTetherHijacker.IPT_WAIT_SECONDS}"

    fun apply(appUid: Int, ipv6Enabled: Boolean): Boolean {
        val script = buildString {
            appendLine("#!/system/bin/sh")
            appendLine("set +e")
            appendTeardown(this)
            appendLine("set -e")
            appendApply(this, appUid, ipv6Enabled, includeForwarded = false)
        }
        val code = RootHelper.runRootScriptHeredoc(script)
        AppLogger.info(TAG, "DNS interception finished code=$code")
        return code == 0
    }

    fun rulesPresent(appUid: Int, ipv6Enabled: Boolean): Boolean {
        val script = buildString {
            appendLine("#!/system/bin/sh")
            appendLine("set -e")
            appendChecks(this, appUid, ipv6Enabled)
        }
        return RootHelper.runRootScriptHeredoc(script) == 0
    }

    fun appendApply(sb: StringBuilder, appUid: Int, ipv6Enabled: Boolean, includeForwarded: Boolean) {
        for (ipt in tables(ipv6Enabled)) {
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

    fun appendChecks(sb: StringBuilder, appUid: Int, ipv6Enabled: Boolean, includeForwarded: Boolean = false) {
        for (ipt in tables(ipv6Enabled)) {
            sb.appendLine("$ipt -t nat -C OUTPUT -j $CHAIN_OUT")
            sb.appendLine("$ipt -t nat -C $CHAIN_OUT -m owner --gid-owner $appUid -j RETURN ${tag("bypass-core")}")
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

    fun appendTeardown(sb: StringBuilder) {
        for (ipt in listOf(IPT4, IPT6)) {
            sb.appendLine("while $ipt -t nat -D PREROUTING -j $CHAIN_PRE 2>/dev/null; do :; done")
            sb.appendLine("while $ipt -t nat -D OUTPUT -j $CHAIN_OUT 2>/dev/null; do :; done")
            for (chain in listOf(CHAIN_PRE, CHAIN_OUT)) {
                sb.appendLine("$ipt -t nat -F $chain 2>/dev/null")
                sb.appendLine("$ipt -t nat -X $chain 2>/dev/null")
            }
        }
    }

    private fun tables(ipv6Enabled: Boolean): List<String> =
        if (ipv6Enabled) listOf(IPT4, IPT6) else listOf(IPT4)

    private fun tag(label: String): String = "-m comment --comment stelliberty:dns:$label"
}
