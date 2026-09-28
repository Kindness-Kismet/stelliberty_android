package com.stelliberty.android.service

import android.app.NotificationManager
import android.content.Context
import com.stelliberty.android.R
import com.stelliberty.android.data.api.MihomoConnectionManager
import com.stelliberty.android.platform.PlatformStorage
import com.stelliberty.android.platform.StorageKeys
import com.stelliberty.android.platform.TunMode
import com.stelliberty.android.util.AppLogger
import com.stelliberty.android.util.FormatUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

class DynamicNotificationManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val connectionManager: MihomoConnectionManager,
) {

    private var trafficJob: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(profileName: String) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)

        // 瞬时异常（比如系统偶发拒绝刷新通知）要在收集内部处理掉。给整条流挂捕获是终结型的，
        // 捕获之后流就结束、不会重订阅，曾经因此让流量通知永久停更。
        trafficJob = scope.launch {
            connectionManager.repository
                .filterNotNull()
                .flatMapLatest { it.trafficFlow() }
                .collect { traffic ->
                    runCatching {
                        val notification = NotificationHelper.buildDynamicNotification(
                            context = context,
                            profileName = profileName,
                            uploadTotal = FormatUtils.formatBytes(traffic.upTotal),
                            downloadTotal = FormatUtils.formatBytes(traffic.downTotal),
                            uploadSpeed = FormatUtils.formatSpeed(traffic.up),
                            downloadSpeed = FormatUtils.formatSpeed(traffic.down),
                        )
                        notificationManager?.notify(NotificationHelper.NOTIFICATION_ID_VPN, notification)
                    }.onFailure { AppLogger.warn(TAG, "Notify failed: $it") }
                }
        }
    }

    // ROOT 模式一律用静态通知：应用进程没有 VPN 服务那层系统照顾，退到后台后系统会把每秒一次的
    // 流量推送和通知刷新合并掉，动态通知会卡住不动。
    fun startOrFallbackStatic(storage: PlatformStorage, tunMode: TunMode = TunMode.Vpn) {
        val isDynamicEnabled = storage.getString(StorageKeys.DYNAMIC_NOTIFICATION, "true") == "true"
        val isDynamic = isDynamicEnabled && tunMode == TunMode.Vpn
        if (isDynamic) {
            val profileName = storage.getString(StorageKeys.ACTIVE_PROFILE_NAME, "Stelliberty")
            start(profileName)
        } else {
            val mode = context.getString(
                when (tunMode) {
                    TunMode.RootTun -> R.string.settings_tun_mode_root_tun
                    TunMode.RootTproxy -> R.string.settings_tun_mode_root_tproxy
                    TunMode.Vpn -> R.string.settings_tun_mode_vpn
                }
            )
            val notification = NotificationHelper.buildRunningNotification(context, mode)
            context.getSystemService(NotificationManager::class.java)
                ?.notify(NotificationHelper.NOTIFICATION_ID_VPN, notification)
        }
    }

    fun stop() {
        trafficJob?.cancel()
        trafficJob = null
    }

    companion object {
        private const val TAG = "DynamicNotification"
    }
}
