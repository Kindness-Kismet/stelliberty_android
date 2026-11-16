package com.stelliberty.android.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.stelliberty.android.R
import com.stelliberty.android.data.api.MihomoConnectionManager
import com.stelliberty.android.platform.PlatformStorage
import com.stelliberty.android.platform.ProxyServiceBridge
import com.stelliberty.android.platform.ProxyState
import com.stelliberty.android.platform.StorageKeys
import com.stelliberty.android.platform.TunMode
import com.stelliberty.android.util.AppLogger
import com.stelliberty.android.util.FormatUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

class DynamicNotificationManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val connectionManager: MihomoConnectionManager,
) {

    @Volatile
    private var notificationJob: Job? = null

    fun start(storage: PlatformStorage, tunMode: TunMode = TunMode.Vpn) {
        stop()
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationJob = scope.launch(Dispatchers.Main.immediate) {
            // 划掉后在本次服务会话内保持隐藏；权限变化靠广播唤醒，禁用时不保留流量连接或轮询。
            combine(
                merge(permissionEvents(), ProxyServiceBridge.notificationRefresh),
                ProxyServiceBridge.notificationDismissed,
                ProxyServiceBridge.state,
            ) { _, dismissed, status -> !dismissed && status.state == ProxyState.Running }
                .collectLatest { visible ->
                if (!visible || !NotificationHelper.canShowProxyNotification(context)) return@collectLatest
                if (storage.getString(StorageKeys.DYNAMIC_NOTIFICATION, "true") != "true") {
                    val mode = context.getString(
                        when (tunMode) {
                            TunMode.RootTun -> R.string.settings_tun_mode_root_tun
                            TunMode.RootTproxy -> R.string.settings_tun_mode_root_tproxy
                            TunMode.Vpn -> R.string.settings_tun_mode_vpn
                        }
                    )
                    notificationManager.notify(
                        NotificationHelper.NOTIFICATION_ID_VPN,
                        NotificationHelper.buildRunningNotification(context, mode),
                    )
                    return@collectLatest
                }
                val profileName = storage.getString(StorageKeys.ACTIVE_PROFILE_NAME, "Stelliberty")
                connectionManager.repository.collectLatest { repo ->
                    repo?.trafficFlow()?.collect { traffic ->
                        currentCoroutineContext().ensureActive()
                        if (connectionManager.repository.value !== repo ||
                            ProxyServiceBridge.state.value.state != ProxyState.Running
                        ) return@collect
                        // 通知异常只影响当前帧，流量流继续接收后续帧。
                        runCatching {
                            notificationManager.notify(
                                NotificationHelper.NOTIFICATION_ID_VPN,
                                NotificationHelper.buildDynamicNotification(
                                    context = context,
                                    profileName = profileName,
                                    uploadTotal = FormatUtils.formatBytes(traffic.upTotal),
                                    downloadTotal = FormatUtils.formatBytes(traffic.downTotal),
                                    uploadSpeed = FormatUtils.formatSpeed(traffic.up),
                                    downloadSpeed = FormatUtils.formatSpeed(traffic.down),
                                ),
                            )
                        }.onFailure { AppLogger.warn(TAG, "Notify failed: $it") }
                    }
                }
            }
        }
    }

    private fun permissionEvents() = callbackFlow<Unit> {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == NotificationManager.ACTION_NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED &&
                    intent.getStringExtra(NotificationManager.EXTRA_NOTIFICATION_CHANNEL_ID) != NotificationHelper.CHANNEL_VPN
                ) return
                trySend(Unit)
            }
        }
        val filter = IntentFilter().apply {
            addAction(NotificationManager.ACTION_APP_BLOCK_STATE_CHANGED)
            addAction(NotificationManager.ACTION_NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        trySend(Unit)
        awaitClose { context.unregisterReceiver(receiver) }
    }

    fun stop() {
        notificationJob?.cancel()
        notificationJob = null
    }

    companion object {
        private const val TAG = "DynamicNotification"
    }
}
