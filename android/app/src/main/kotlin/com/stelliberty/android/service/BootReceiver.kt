package com.stelliberty.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.stelliberty.android.platform.PlatformStorage
import com.stelliberty.android.platform.ProxyServiceBridge
import com.stelliberty.android.platform.ProxyServiceController
import com.stelliberty.android.platform.ProxyState
import com.stelliberty.android.platform.StorageKeys
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

// 有些系统不给没运行的应用发开机广播，而是等进程起来后补发，那时「打开应用自动连接」往往已经启动过了。
// 已在运行就不必再发；正在启动中仍然要发——服务端会挡掉重复启动，而正在重连的那条正好被抢占。
class BootReceiver : BroadcastReceiver(), KoinComponent {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val storage = PlatformStorage(context)
                val wasRunning = storage.getString(StorageKeys.SERVICE_WAS_RUNNING, "false") == "true"
                if (!wasRunning) return
                if (ProxyServiceBridge.state.value.state == ProxyState.Running) return
                get<ProxyServiceController>().start()
            }
        }
    }
}
