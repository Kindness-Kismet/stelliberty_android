package com.stelliberty.android.service

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import com.stelliberty.android.platform.ProxyServiceController
import org.koin.android.ext.android.inject

// 磁贴和通知这类没有界面的入口要弹 VPN 授权只能借道这里：授权框必须由 Activity 发起。
class VpnPermissionActivity : Activity() {

    private val controller: ProxyServiceController by inject()

    // 缺省即「没有订阅」，交给控制器用内置配置启动，不能因为空值就直接退出。
    private val subscriptionId: String?
        get() = intent.getStringExtra(EXTRA_SUBSCRIPTION_ID)?.ifEmpty { null }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val permissionIntent = VpnService.prepare(this)
        if (permissionIntent == null) {
            controller.start(subscriptionId)
            finish()
        } else {
            @Suppress("DEPRECATION")
            startActivityForResult(permissionIntent, VPN_REQUEST_CODE)
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            controller.start(subscriptionId)
        }
        finish()
    }

    companion object {
        const val EXTRA_SUBSCRIPTION_ID = "subscription_id"
        private const val VPN_REQUEST_CODE = 1002
    }
}
