package com.stelliberty.android.platform

import com.stelliberty.android.util.AppLogger
import java.net.Inet4Address
import java.net.NetworkInterface

fun scanTetherInterfacesAsRoot(): List<String> {
    return try {
        NetworkInterface.getNetworkInterfaces()
            .toList()
            .filter { iface ->
                iface.isUp && !iface.isLoopback && isCandidateName(iface.name) && hasSiteLocalIpv4(iface)
            }
            .map { it.name }
            .distinct()
    } catch (e: Exception) {
        AppLogger.warn("TetherScanner", "enumerate interfaces failed", e)
        emptyList()
    }
}

private fun isCandidateName(name: String): Boolean {
    if (name.startsWith("tun")) return false
    if (name.startsWith("utun")) return false
    if (name.startsWith("rmnet")) return false
    if (name.startsWith("dummy")) return false
    if (name.startsWith("p2p")) return false
    if (name.startsWith("ccmni")) return false
    if (name == "lo") return false
    return true
}

private fun hasSiteLocalIpv4(iface: NetworkInterface): Boolean {
    val addresses = iface.inetAddresses ?: return false
    for (addr in addresses) {
        if (addr !is Inet4Address) continue
        if (addr.isLoopbackAddress) continue
        if (addr.isLinkLocalAddress) continue
        if (addr.isSiteLocalAddress) return true
    }
    return false
}
