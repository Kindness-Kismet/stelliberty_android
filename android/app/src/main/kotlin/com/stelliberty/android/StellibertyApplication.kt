package com.stelliberty.android

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Build
import com.stelliberty.android.data.api.AutoDelayTester
import com.stelliberty.android.data.api.MihomoConnectionManager
import com.stelliberty.android.data.bridge.StellibertyCoreBridge
import com.stelliberty.android.di.androidAppModule
import com.stelliberty.android.di.androidPlatformModule
import com.stelliberty.android.di.dataModule
import com.stelliberty.android.di.viewModelModule
import com.stelliberty.android.platform.PlatformStorage
import com.stelliberty.android.platform.ProxyServiceBridge
import com.stelliberty.android.platform.StorageKeys
import com.stelliberty.android.platform.TunMode
import com.stelliberty.android.platform.initToastPlatform
import com.stelliberty.android.service.NotificationHelper
import com.stelliberty.android.service.ProfileFileOps
import com.stelliberty.android.service.ProfileUpdateScheduler
import com.stelliberty.android.util.AppLogger
import kotlin.concurrent.thread
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.lsposed.hiddenapibypass.HiddenApiBypass

class StellibertyApplication : Application() {

    val connectionManager: MihomoConnectionManager by inject()

    private val updateScheduler: ProfileUpdateScheduler by inject()

    private val autoDelayTester: AutoDelayTester by inject()

    private val storage: PlatformStorage by inject()

    override fun onCreate() {
        super.onCreate()
        instance = this
        // 排在最前：后面每一步都要打日志，未初始化时文件写入会静默降级、只剩 logcat。
        AppLogger.initialize(this, PlatformStorage(this).getString(StorageKeys.APP_LOG_LEVEL, AppLogger.DEFAULT_LEVEL))
        startKoin {
            androidContext(this@StellibertyApplication)
            modules(dataModule, androidPlatformModule, androidAppModule, viewModelModule)
        }
        // 必须在任何人读桥之前填：MainActivity 与通知都在 onCreate 之后才起来，这里是唯一
        // 既拿得到设置、又早于全部读取方的位置。
        ProxyServiceBridge.setSelectedTunMode(
            TunMode.fromStorage(storage.getString(StorageKeys.TUN_MODE, TunMode.Vpn.storageValue))
        )
        initToastPlatform(this)
        NotificationHelper.createChannels(this)
        StellibertyCoreBridge.init(homeDir = ProfileFileOps.getGeodataDir(this).absolutePath)
        // 解压不能堵住启动；读地理数据的地方各自等 awaitGeodata()。
        thread(name = "geodata-extract") { ProfileFileOps.extractGeodata(this) }
        updateScheduler.start()
        autoDelayTester.start()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val enable = storage.getString(StorageKeys.PREDICTIVE_BACK, "false") == "true"
            HiddenApiBypass.addHiddenApiExemptions("Landroid/content/pm/ApplicationInfo;->setEnableOnBackInvokedCallback")
            setEnableOnBackInvokedCallback(applicationInfo, enable)
        }
    }

    companion object {
        lateinit var instance: StellibertyApplication
            private set

        fun setEnableOnBackInvokedCallback(appInfo: ApplicationInfo, enable: Boolean) {
            runCatching {
                val method = ApplicationInfo::class.java.getDeclaredMethod(
                    "setEnableOnBackInvokedCallback",
                    Boolean::class.javaPrimitiveType,
                )
                method.isAccessible = true
                method.invoke(appInfo, enable)
            }
        }
    }
}
