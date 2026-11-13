package com.stelliberty.android.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.stelliberty.android.util.AppLogger
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class RootDnsCoordinator(context: Context, parentScope: CoroutineScope) {
    private data class DnsNetwork(val netId: Int, val interfaceName: String?, val servers: List<InetAddress>)

    private val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
    private val mutex = Mutex()
    private var registered = false
    private var appUid = -1
    private var includeForwarded = false
    private var ipv6Nat = false
    private var previousNetworks: List<DnsNetwork>? = null

    @Volatile
    private var observing = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = scheduleRefresh()
        override fun onLost(network: Network) = scheduleRefresh()
        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) = scheduleRefresh()
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = scheduleRefresh()
    }

    suspend fun start(appUid: Int, includeForwarded: Boolean): Boolean = mutex.withLock {
        try {
            currentCoroutineContext().ensureActive()
            this.appUid = appUid
            this.includeForwarded = includeForwarded
            val supported = RootDnsHijacker.probeIpv6Nat() ?: return@withLock false
            currentCoroutineContext().ensureActive()
            ipv6Nat = supported
            beginObserving()
            val success = refresh(force = true)
            if (!success) stopObserving()
            success
        } catch (e: CancellationException) {
            stopObserving()
            throw e
        } catch (e: Exception) {
            stopObserving()
            AppLogger.warn(TAG, "Cannot start system DNS interception", e)
            false
        }
    }

    @Synchronized
    private fun beginObserving() {
        if (!registered) {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()
            connectivity.registerNetworkCallback(request, callback)
            registered = true
        }
        observing = true
    }

    @Synchronized
    fun stopObserving() {
        observing = false
        if (!registered) return
        registered = false
        runCatching { connectivity.unregisterNetworkCallback(callback) }
            .onFailure { AppLogger.warn(TAG, "Cannot unregister system DNS network callback", it) }
    }

    // 先封住新回调，再等待已进入 su 的刷新结束；调用方随后拆规则，停止后不会被旧刷新重新装回。
    suspend fun stop() {
        stopObserving()
        mutex.withLock { previousNetworks = null }
    }

    fun close() {
        stopObserving()
        scope.cancel()
    }

    private fun scheduleRefresh() {
        if (!observing) return
        scope.launch {
            mutex.withLock {
                if (!observing) return@withLock
                try {
                    if (!refresh(force = false) && observing) AppLogger.warn(TAG, "System DNS interception refresh failed")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.warn(TAG, "System DNS interception refresh failed", e)
                }
            }
        }
    }

    private suspend fun refresh(force: Boolean): Boolean {
        val networks = dnsNetworks()
        if (!force && networks == previousNetworks) return true
        currentCoroutineContext().ensureActive()
        if (!observing) return false
        val fallbackInterfaces = if (ipv6Nat) emptyList() else networks
            .filter { it.servers.any { address -> address is Inet4Address } && it.servers.any { address -> address is Inet6Address } }
            .mapNotNull { it.interfaceName }
            .distinct()
            .sorted()
        previousNetworks = null
        val installed = RootDnsHijacker.apply(appUid, ipv6Nat, fallbackInterfaces, includeForwarded)
        currentCoroutineContext().ensureActive()
        if (!observing || !installed) return false
        val present = RootDnsHijacker.rulesPresent(appUid, ipv6Nat, fallbackInterfaces, includeForwarded)
        currentCoroutineContext().ensureActive()
        if (!observing || !present) return false
        for (network in networks) {
            if (!RootDnsHijacker.flushNetworkCache(network.netId)) return false
            currentCoroutineContext().ensureActive()
            if (!observing) return false
        }
        previousNetworks = networks
        AppLogger.info(TAG, "System DNS interception ready: ipv6Nat=$ipv6Nat fallbackInterfaces=$fallbackInterfaces networks=${networks.size}")
        return true
    }

    private fun dnsNetworks(): List<DnsNetwork> = connectivity.allNetworks.mapNotNull { network ->
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return@mapNotNull null
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        ) return@mapNotNull null
        val properties = connectivity.getLinkProperties(network) ?: return@mapNotNull null
        // Android 的 Network handle 高 32 位是 netId，低 32 位是固定标记。
        DnsNetwork((network.networkHandle ushr 32).toInt(), properties.interfaceName, properties.dnsServers.toList())
    }.sortedBy { it.netId }

    private companion object {
        const val TAG = "RootDnsCoordinator"
    }
}
