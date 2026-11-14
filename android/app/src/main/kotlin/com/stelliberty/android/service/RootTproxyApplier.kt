package com.stelliberty.android.service

import com.stelliberty.android.domain.model.AppProxyMode
import com.stelliberty.android.util.AppLogger

object RootTproxyApplier {

    private const val TAG = "RootTproxyApplier"

    internal const val MARK = 0x01000000
    internal const val MASK = 0x01000000
    internal const val TABLE = 2024
    internal const val PRIORITY = 7999

    // 端口的唯一定义在热点劫持那边，这里只是转出来给别处用，不要另写一个数字。
    internal const val TPROXY_PORT = RootTetherHijacker.TPROXY_PORT

    private const val CHAIN_PRE = "stelliberty_tproxy_pre"
    private const val CHAIN_OUT = "stelliberty_tproxy_out"
    private const val CHAIN_DIVERT = "stelliberty_tproxy_divert"

    private val MANGLE_CHAINS = listOf(CHAIN_PRE, CHAIN_OUT, CHAIN_DIVERT)

    internal const val COMMENT_TAG_PREFIX = "stelliberty:tproxy:"

    private fun tag(label: String): String = "-m comment --comment \"$COMMENT_TAG_PREFIX$label\""

    fun probeTproxySupport(): Boolean = RootTetherHijacker.probeTproxySupport()

    fun apply(
        appUid: Int,
        selectedUids: Set<Int>,
        mode: AppProxyMode,
        tetherIfaces: List<String>,
        ipv6Enabled: Boolean,
    ): Boolean {
        AppLogger.info(TAG, "apply: appUid=$appUid mode=$mode selected=${selectedUids.size} ifaces=$tetherIfaces ipv6=$ipv6Enabled")
        val script = buildApplyScript(appUid, selectedUids, mode, tetherIfaces, ipv6Enabled)
        val code = RootHelper.runRootScriptHeredoc(script, timeoutSeconds = 20)
        AppLogger.info(TAG, "apply finished code=$code")
        return code == 0
    }

    fun teardown() {
        AppLogger.info(TAG, "teardown")
        val code = RootHelper.runRootScriptHeredoc(buildTeardownScript(), timeoutSeconds = 15)
        AppLogger.info(TAG, "teardown finished code=$code")
    }

    // 重连时用它判断规则是否还齐全，齐全就跳过重装。规则可能被第三方模块清掉，所以不能假定还在。
    fun rulesPresent(appUid: Int, ipv6Enabled: Boolean): Boolean {
        val w = RootTetherHijacker.IPT_WAIT_SECONDS
        val script = buildString {
            appendLine("#!/system/bin/sh")
            appendLine("set -e")
            for (ipt in if (ipv6Enabled) TABLES else V4_ONLY_TABLES) {
                appendLine("$ipt -t mangle -C PREROUTING -j $CHAIN_PRE")
                appendLine("$ipt -t mangle -C OUTPUT -j $CHAIN_OUT")
                appendLine("$ipt -t mangle -C $CHAIN_OUT -m owner --gid-owner $appUid -j RETURN ${tag("out-bypass-core")}")
            }
            appendLine("ip rule show | grep -q '^$PRIORITY:.*fwmark 0x1000000/0x1000000 lookup $TABLE'")
            appendLine("ip route show table $TABLE | grep -q '^local default dev lo'")
            if (ipv6Enabled) {
                appendLine("ip -6 rule show | grep -q '^$PRIORITY:.*fwmark 0x1000000/0x1000000 lookup $TABLE'")
                appendLine("ip -6 route show table $TABLE | grep -q '^local default dev lo'")
            }
        }
        val code = RootHelper.runRootScriptHeredoc(script, timeoutSeconds = w + 5L)
        return code == 0
    }

    private fun buildApplyScript(
        appUid: Int,
        selectedUids: Set<Int>,
        mode: AppProxyMode,
        tetherIfaces: List<String>,
        ipv6Enabled: Boolean,
    ): String {
        val tables = if (ipv6Enabled) TABLES else V4_ONLY_TABLES
        val sb = StringBuilder()
        sb.appendLine("#!/system/bin/sh")
        sb.appendLine("set +e")
        sb.append(buildTeardownScript(includeShebang = false))
        sb.appendLine("set -e")
        sb.appendLine()
        sb.appendLine("# === 1. ip rule + route ===")
        sb.appendLine("ip rule  add fwmark $MARK/$MASK lookup $TABLE priority $PRIORITY 2>/dev/null")
        sb.appendLine("ip  route add local default dev lo table $TABLE 2>/dev/null")
        if (ipv6Enabled) {
            sb.appendLine("ip -6 rule add fwmark $MARK/$MASK lookup $TABLE priority $PRIORITY 2>/dev/null")
            sb.appendLine("ip -6 route add local default dev lo table $TABLE 2>/dev/null")
        }

        sb.appendLine()
        sb.appendLine("# === 2. 自建 chain ===")
        for (t in tables) {
            sb.appendLine("$t -t mangle -N $CHAIN_PRE")
            sb.appendLine("$t -t mangle -N $CHAIN_OUT")
            sb.appendLine("$t -t mangle -N $CHAIN_DIVERT")
        }

        sb.appendLine()
        sb.appendLine("# === 3. DIVERT chain: ESTABLISHED 流快速通道 (MARK + ACCEPT) ===")
        for (t in tables) {
            sb.appendLine("$t -t mangle -A $CHAIN_DIVERT -j MARK --set-xmark $MARK/$MASK ${tag("divert-mark")}")
            sb.appendLine("$t -t mangle -A $CHAIN_DIVERT -j ACCEPT ${tag("divert-accept")}")
        }

        sb.appendLine()
        sb.appendLine("# === 4. PREROUTING 主链 (intranet RETURN + LOCAL RETURN + DIVERT + TPROXY) ===")
        appendIntranetReturns(sb, table = "mangle", chain = CHAIN_PRE, tagLabel = "pre-intranet", ipv6Enabled = ipv6Enabled)
        appendAddressTypeReturn(sb, IPT4, CHAIN_PRE, "LOCAL", "-4", "pre-local")
        if (ipv6Enabled) {
            appendAddressTypeReturn(sb, IPT6, CHAIN_PRE, "LOCAL", "-6", "pre-local-v6")
        }
        for (t in tables) {
            sb.appendLine("$t -t mangle -A $CHAIN_PRE -p tcp -m socket -j $CHAIN_DIVERT ${tag("pre-divert-tcp")}")
            sb.appendLine("$t -t mangle -A $CHAIN_PRE -p udp -m socket -j $CHAIN_DIVERT ${tag("pre-divert-udp")}")
        }
        for (proto in listOf("tcp", "udp")) {
            sb.appendLine(
                "$IPT4 -t mangle -A $CHAIN_PRE -p $proto -i lo -j TPROXY --on-ip 127.0.0.1 --on-port $TPROXY_PORT --tproxy-mark $MARK/$MASK ${
                    tag(
                        "pre-tproxy-lo-$proto"
                    )
                }"
            )
            if (ipv6Enabled) {
                sb.appendLine(
                    "$IPT6 -t mangle -A $CHAIN_PRE -p $proto -i lo -j TPROXY --on-ip ::1 --on-port $TPROXY_PORT --tproxy-mark $MARK/$MASK ${
                        tag(
                            "pre-tproxy-lo-$proto-v6"
                        )
                    } 2>/dev/null"
                )
            }
        }
        for (iface in tetherIfaces) {
            val esc = RootHelper.escapeShellSingleQuoted(iface)
            for (proto in listOf("tcp", "udp")) {
                sb.appendLine(
                    "$IPT4 -t mangle -A $CHAIN_PRE -p $proto -i $esc -j TPROXY --on-ip 127.0.0.1 --on-port $TPROXY_PORT --tproxy-mark $MARK/$MASK ${
                        tag(
                            "pre-tproxy-tether-$proto"
                        )
                    }"
                )
                if (ipv6Enabled) {
                    sb.appendLine(
                        "$IPT6 -t mangle -A $CHAIN_PRE -p $proto -i $esc -j TPROXY --on-ip ::1 --on-port $TPROXY_PORT --tproxy-mark $MARK/$MASK ${
                            tag(
                                "pre-tproxy-tether-$proto-v6"
                            )
                        } 2>/dev/null"
                    )
                }
            }
        }

        sb.appendLine()
        sb.appendLine("# === 5. OUTPUT 主链 (本机按 AppProxyMode 打 mark) ===")
        for (t in tables) {
            sb.appendLine("$t -t mangle -A $CHAIN_OUT -m owner --gid-owner $appUid -j RETURN ${tag("out-bypass-core")}")
            sb.appendLine("$t -t mangle -A $CHAIN_OUT -m owner --uid-owner $appUid -j RETURN ${tag("out-bypass-self")}")
            appendAddressTypeReturn(sb, t, CHAIN_OUT, "LOCAL", if (t == IPT4) "-4" else "-6", "out-local")
            if (t == IPT4) appendAddressTypeReturn(sb, t, CHAIN_OUT, "BROADCAST", "-4", "out-broadcast")
            sb.appendLine("$t -t mangle -A $CHAIN_OUT -p udp --dport 53 -j RETURN ${tag("out-dns-udp")}")
            sb.appendLine("$t -t mangle -A $CHAIN_OUT -p tcp --dport 53 -j RETURN ${tag("out-dns-tcp")}")
        }
        appendIntranetReturns(sb, table = "mangle", chain = CHAIN_OUT, tagLabel = "out-intranet", ipv6Enabled = ipv6Enabled)

        sb.appendLine("# --- AppProxyMode: $mode ---")
        when (mode) {
            AppProxyMode.AllowAll -> {
                for (t in tables) {
                    for (proto in listOf("tcp", "udp")) {
                        sb.appendLine("$t -t mangle -A $CHAIN_OUT -p $proto -j MARK --set-xmark $MARK/$MASK ${tag("out-mark-$proto")}")
                    }
                }
            }

            AppProxyMode.AllowSelected -> {
                for (uid in selectedUids) {
                    if (uid == appUid) continue
                    for (t in tables) {
                        for (proto in listOf("tcp", "udp")) {
                            sb.appendLine(
                                "$t -t mangle -A $CHAIN_OUT -p $proto -m owner --uid-owner $uid -j MARK --set-xmark $MARK/$MASK ${
                                    tag(
                                        "out-mark-uid-$proto"
                                    )
                                }"
                            )
                        }
                    }
                }
            }

            AppProxyMode.DenySelected -> {
                for (uid in selectedUids) {
                    for (t in tables) {
                        sb.appendLine("$t -t mangle -A $CHAIN_OUT -m owner --uid-owner $uid -j RETURN ${tag("out-deny-uid")}")
                    }
                }
                for (t in tables) {
                    for (proto in listOf("tcp", "udp")) {
                        sb.appendLine("$t -t mangle -A $CHAIN_OUT -p $proto -j MARK --set-xmark $MARK/$MASK ${tag("out-mark-$proto")}")
                    }
                }
            }
        }

        sb.appendLine()
        sb.appendLine("# === 7. 挂主链 ===")
        for (t in tables) {
            sb.appendLine("$t -t mangle -I PREROUTING -j $CHAIN_PRE")
            sb.appendLine("$t -t mangle -I OUTPUT -j $CHAIN_OUT")
        }

        sb.appendLine()
        sb.appendLine("exit 0")
        return sb.toString()
    }

    private fun buildTeardownScript(includeShebang: Boolean = true): String {
        val sb = StringBuilder()
        if (includeShebang) {
            sb.appendLine("#!/system/bin/sh")
            sb.appendLine("set +e")
        }
        sb.appendLine("# === 卸主链 (没挂过 -D 返回非零，忽略) ===")
        for (t in TABLES) {
            sb.appendLine("$t -t mangle -D PREROUTING -j $CHAIN_PRE 2>/dev/null")
            sb.appendLine("$t -t mangle -D OUTPUT -j $CHAIN_OUT 2>/dev/null")
        }
        RootDnsHijacker.appendTeardown(sb)
        sb.appendLine()
        // 必须先把所有链清空再统一删：`-X` 要求引用计数为 0，而 CHAIN_PRE 里挂着
        // `-j CHAIN_DIVERT`。清空与删除交替做，删 DIVERT 时 PRE 还没清，它就会被静默留下。
        sb.appendLine("# === flush 全部自建链 ===")
        for (t in TABLES) {
            for (c in MANGLE_CHAINS) sb.appendLine("$t -t mangle -F $c 2>/dev/null")
        }
        sb.appendLine()
        sb.appendLine("# === delete 全部自建链 ===")
        for (t in TABLES) {
            for (c in MANGLE_CHAINS) sb.appendLine("$t -t mangle -X $c 2>/dev/null")
        }
        sb.appendLine()
        sb.appendLine("# === ip rule + route ===")
        sb.appendLine("i=0; while [ \$i -lt 32 ] && ip rule del priority $PRIORITY 2>/dev/null; do i=\$((i+1)); done")
        sb.appendLine("i=0; while [ \$i -lt 32 ] && ip -6 rule del priority $PRIORITY 2>/dev/null; do i=\$((i+1)); done")
        sb.appendLine("ip  route flush table $TABLE 2>/dev/null")
        sb.appendLine("ip -6 route flush table $TABLE 2>/dev/null")
        if (includeShebang) {
            sb.appendLine()
            sb.appendLine("exit 0")
        }
        return sb.toString()
    }

    private fun appendIntranetReturns(
        sb: StringBuilder,
        table: String,
        chain: String,
        tagLabel: String,
        ipv6Enabled: Boolean,
    ) {
        for (net in IptablesIntranet.V4) {
            sb.appendLine("$IPT4 -t $table -A $chain -d $net -p udp ! --dport 53 -j RETURN ${tag(tagLabel)}")
            sb.appendLine("$IPT4 -t $table -A $chain -d $net ! -p udp -j RETURN ${tag(tagLabel)}")
        }
        if (ipv6Enabled) {
            for (net in IptablesIntranet.V6) {
                sb.appendLine("$IPT6 -t $table -A $chain -d $net -p udp ! --dport 53 -j RETURN ${tag("$tagLabel-v6")} 2>/dev/null")
                sb.appendLine("$IPT6 -t $table -A $chain -d $net ! -p udp -j RETURN ${tag("$tagLabel-v6")} 2>/dev/null")
            }
        }
    }

    private fun appendAddressTypeReturn(
        sb: StringBuilder,
        iptables: String,
        chain: String,
        type: String,
        family: String,
        label: String,
    ) {
        val ruleTag = tag(label)
        sb.appendLine("if ! $iptables -t mangle -A $chain -m addrtype --dst-type $type -j RETURN $ruleTag 2>/dev/null; then")
        // 部分内核没有 xt_addrtype，从 local 表保留本机与广播地址的排除规则。
        sb.appendLine("  local_routes=\$(ip $family route show table local)")
        sb.appendLine("  local_addresses=\$(printf '%s\\n' \"\$local_routes\" | awk '\$1 == \"${type.lowercase()}\" {print \$2}')")
        sb.appendLine("  for address in \$local_addresses; do")
        sb.appendLine("    $iptables -t mangle -A $chain -d \"\$address\" -j RETURN $ruleTag")
        sb.appendLine("  done")
        sb.appendLine("fi")
    }

    private val IPT4 = "iptables -w ${RootTetherHijacker.IPT_WAIT_SECONDS}"
    private val IPT6 = "ip6tables -w ${RootTetherHijacker.IPT_WAIT_SECONDS}"

    private val TABLES = listOf(IPT4, IPT6)
    private val V4_ONLY_TABLES = listOf(IPT4)
}
