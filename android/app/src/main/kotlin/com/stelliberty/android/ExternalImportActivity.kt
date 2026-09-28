package com.stelliberty.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.stelliberty.android.platform.showToast
import java.util.UUID

class ExternalImportActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data?.takeIf { it.isHierarchical }
        val url = uri?.getQueryParameter(QUERY_URL)?.trim()
        if (url.isNullOrEmpty() || !(url.startsWith("http://") || url.startsWith("https://"))) {
            showToast(getString(R.string.deep_link_invalid))
            finish()
            return
        }
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                action = ACTION_IMPORT_SUBSCRIPTION
                putExtra(EXTRA_IMPORT_URL, url)
                putExtra(EXTRA_IMPORT_NAME, uri.getQueryParameter(QUERY_NAME)?.trim().orEmpty())
                putExtra(
                    EXTRA_IMPORT_INTERVAL_MINUTES,
                    uri.getQueryParameter(QUERY_UPDATE_INTERVAL)?.toLongOrNull()?.coerceAtLeast(0) ?: 0L,
                )
                putExtra(EXTRA_IMPORT_NONCE, UUID.randomUUID().toString())
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
            }
        )
        finish()
    }

    companion object {
        const val ACTION_IMPORT_SUBSCRIPTION = "com.stelliberty.android.action.IMPORT_SUBSCRIPTION"
        const val EXTRA_IMPORT_URL = "import_url"
        const val EXTRA_IMPORT_NAME = "import_name"
        const val EXTRA_IMPORT_INTERVAL_MINUTES = "import_interval_minutes"
        // 用随机串区分新旧深链，不能用「有没有恢复状态」判：进程被杀后重放的旧深链和新送达的
        // 深链部可能带或不带恢复状态，只有 nonce 能分开。
        const val EXTRA_IMPORT_NONCE = "import_nonce"

        private const val QUERY_URL = "url"
        private const val QUERY_NAME = "name"
        private const val QUERY_UPDATE_INTERVAL = "update-interval"
    }
}

data class DeepLinkImportRequest(
    val url: String,
    val name: String,
    val intervalMinutes: Long,
)
