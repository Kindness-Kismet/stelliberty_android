package com.stelliberty.android.service

import android.content.Context
import com.stelliberty.android.domain.model.AppProxyMode
import com.stelliberty.android.domain.model.ConfigurationOverride
import com.stelliberty.android.domain.model.DnsOverride
import com.stelliberty.android.domain.model.ProfileOverride
import com.stelliberty.android.domain.model.TunOverride
import com.stelliberty.android.platform.PlatformStorage
import com.stelliberty.android.platform.StorageKeys
import com.stelliberty.android.platform.TunMode
import com.stelliberty.android.service.RuntimeOverrideBuilder.DEFAULT_MIXED_PORT
import com.stelliberty.android.util.AppLogger
import java.io.File
import kotlinx.serialization.json.Json

object RuntimeOverrideBuilder {

    private const val TAG = "RuntimeOverride"
    private const val FILE_NAME = "override.run.json"
    internal const val DEFAULT_TUN_DEVICE = "Stelliberty"

    // VPN 模式下这个值必须和 VpnService 设的 MTU 一致：网卡底层用它算读缓冲，两边不一致或为 0 时
    // 所有读取都会失败，表现成「延迟测得出来但完全上不了网」。两边共用这个常量，谁都不要写死数字。
    internal const val VPN_TUN_MTU = 9000

    internal const val ROOT_TUN_TABLE = 2022
    internal const val ROOT_TUN_RULE_INDEX = 9000

    internal const val DEFAULT_MIXED_PORT = 7890

    private val json = Json {
        encodeDefaults = false
        explicitNulls = false
    }

    fun buildAndWriteForRun(
        context: Context,
        userOverride: ConfigurationOverride,
        tunFd: Int,
        tunMode: TunMode,
        subscriptionUpdateViaCore: Boolean,
        subscriptionMixedPort: Int?,
        tproxyForTether: Boolean = false,
    ): File {
        val storage = PlatformStorage(context)
        val wifiRuntimeMode = storage.getString(StorageKeys.WIFI_POLICY_RUNTIME_MODE, "")
            .takeIf { it == "rule" || it == "global" || it == "direct" }
        val merged = userOverride.copy(
            externalController = null,
            secret = null,
            mode = wifiRuntimeMode ?: userOverride.mode,
            // 端口取值顺序：用户设了就用用户的；订阅文件里自带就不动，免得用默认值盖掉订阅原本的设置；
            // 只有存在经内核更新的订阅才补一个默认端口，否则不写。
            mixedPort = when {
                userOverride.mixedPort != null -> userOverride.mixedPort
                subscriptionMixedPort != null -> null
                subscriptionUpdateViaCore -> DEFAULT_MIXED_PORT
                else -> null
            },
            tproxyPort = when (tunMode) {
                TunMode.RootTproxy -> RootTproxyApplier.TPROXY_PORT
                TunMode.RootTun -> if (tproxyForTether) RootTetherHijacker.TPROXY_PORT else userOverride.tproxyPort
                TunMode.Vpn -> userOverride.tproxyPort
            },
            routingMark = userOverride.routingMark,
            tcpConcurrent = userOverride.tcpConcurrent ?: true,
            // 默认关掉进程查找：分应用代理已经由系统和防火墙规则处理了，运行期再去翻 /proc 纯属多余开销。
            findProcessMode = userOverride.findProcessMode ?: "off",
            dns = buildDnsOverride(tunMode, userOverride.dns),
            tun = buildTunOverride(context, tunMode, tunFd, userOverride.tun),
            profile = ProfileOverride(storeSelected = false, storeFakeIp = true),
        )
        val file = File(ConfigGenerator.getWorkDir(context), FILE_NAME)
        ProfileFileOps.writeAtomically(file, json.encodeToString(merged))
        // 分应用包名可能上百个，日志只记数量。
        val tun = merged.tun
        val logged = merged.copy(tun = tun?.copy(includePackage = null, excludePackage = null))
        AppLogger.info(
            TAG,
            "Runtime override for $tunMode: ${json.encodeToString(logged)}; " +
                "includePackages=${tun?.includePackage?.size ?: 0}, excludePackages=${tun?.excludePackage?.size ?: 0}",
        )
        return file
    }

    private fun buildDnsOverride(tunMode: TunMode, userDns: DnsOverride?): DnsOverride? {
        if (tunMode != TunMode.RootTproxy) return userDns
        val base = userDns ?: DnsOverride()
        return base.copy(
            enable = true,
            listen = "0.0.0.0:${RootTproxyApplier.DNS_PORT}",
        )
    }

    private fun buildTunOverride(
        context: Context,
        tunMode: TunMode,
        tunFd: Int,
        userTun: TunOverride?,
    ): TunOverride {
        if (tunMode == TunMode.RootTproxy) {
            return TunOverride(enable = false)
        }

        val storage = PlatformStorage(context)
        val isRootTun = tunMode == TunMode.RootTun

        val include: List<String>?
        val exclude: List<String>?
        if (isRootTun) {
            val selfPkg = context.packageName
            val proxyMode = AppProxyMode.parse(storage.getString(StorageKeys.APP_PROXY_MODE, ""))
            val packages = storage.getStringSet(StorageKeys.APP_PROXY_PACKAGES, emptySet())
            when (proxyMode) {
                AppProxyMode.AllowSelected -> {
                    val filtered = packages.filter { it != selfPkg }
                    include = if (filtered.isNotEmpty()) filtered else listOf("-")
                    exclude = null
                }

                AppProxyMode.DenySelected -> {
                    include = null
                    exclude = (packages + selfPkg).distinct()
                }

                AppProxyMode.AllowAll -> {
                    include = null
                    exclude = listOf(selfPkg)
                }
            }
        } else {
            include = null
            exclude = null
        }

        val ipv6Enabled = storage.getString(StorageKeys.VPN_ALLOW_IPV6, "false") == "true"
        val inet6 = when {
            isRootTun && ipv6Enabled -> listOf("fdfe:dcba:9876::1/126")
            !isRootTun -> emptyList()
            else -> null
        }

        // ROOT TUN 模式必须显式排除内网段：底层库在这一项为空时会把 0.0.0.0/0 全铺进 TUN，
        // 局域网和组播流量一起被吸走，同一个网内的设备发现、投屏、点对点直连全部失效。
        val routeExclude: List<String>? = when {
            !isRootTun -> userTun?.routeExcludeAddress
            else -> userTun?.routeExcludeAddress ?: buildList {
                addAll(IptablesIntranet.V4)
                if (ipv6Enabled) addAll(IptablesIntranet.V6)
            }
        }

        val device = userTun?.device
            ?: if (isRootTun) storage.getString(StorageKeys.ROOT_TUN_DEVICE, DEFAULT_TUN_DEVICE) else null

        // 大包聚合：把 MTU 调大并开启分片卸载，能减少读网卡的系统调用次数。
        // 关掉就退回普通 1500 字节。VPN 模式不碰这些，那边由系统的 VpnService 管。
        val jumbo = storage.getString(StorageKeys.ROOT_TUN_JUMBO_MTU, "true") == "true"
        val defaultMtu: Int = if (isRootTun && !jumbo) 1500 else VPN_TUN_MTU
        val rootTunGso: Boolean? = if (isRootTun) jumbo else null
        val rootTunGsoMax: Int? = if (isRootTun && jumbo) 65535 else null

        return TunOverride(
            enable = true,
            device = device,
            stack = userTun?.stack,
            fileDescriptor = tunFd.takeIf { it >= 0 && !isRootTun },
            autoRoute = isRootTun,
            autoDetectInterface = isRootTun,
            routeExcludeAddress = routeExclude,
            inet6Address = inet6,
            dnsHijack = listOf("0.0.0.0:53"),
            includePackage = include,
            excludePackage = exclude,
            iproute2TableIndex = if (isRootTun) ROOT_TUN_TABLE else null,
            iproute2RuleIndex = if (isRootTun) ROOT_TUN_RULE_INDEX else null,
            mtu = userTun?.mtu ?: defaultMtu,
            gso = userTun?.gso ?: rootTunGso,
            gsoMaxSize = userTun?.gsoMaxSize ?: rootTunGsoMax,
        )
    }

}
