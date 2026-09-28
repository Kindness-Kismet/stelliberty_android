package com.stelliberty.android.domain.model

// 三种隧道模式各用自己的机制落实这同一套语义：VPN 走系统接口，
// ROOT TUN 交给 mihomo 的包名名单，ROOT TPROXY 靠防火墙按调用方 UID 匹配。
enum class AppProxyMode {
    AllowAll,
    AllowSelected,
    DenySelected;

    companion object {
        // 存储里是枚举名。读不出来就按全部代理走，别让一个坏值把代理拦在启动之外。
        fun parse(name: String?): AppProxyMode = entries.firstOrNull { it.name == name } ?: AllowAll
    }
}
