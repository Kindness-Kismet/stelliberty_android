package com.stelliberty.android.debug

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import org.koin.core.context.GlobalContext
import com.stelliberty.android.platform.PlatformStorage

class DebugCommandProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        if (!isAuthorizedCaller()) {
            return debugResult(false, "Unauthorized caller", method, arg)
        }
        val context = context ?: return debugResult(false, "Context is not ready", method, arg)
        if (!awaitKoin()) {
            return debugResult(false, "Koin graph not ready after ${KOIN_WAIT_MS}ms", method, arg)
        }
        return runCatching {
            when (method) {
                in PAGE_METHODS -> runPageCommand(arg, extras)
                in PROXY_METHODS -> runProxyCommand(context, method.removePrefix("proxy."), arg, extras)
                in SUBSCRIPTION_METHODS -> runSubscriptionCommand(method.removePrefix("subscription."), arg, extras)
                in OVERRIDE_METHODS -> runOverrideCommand(method.removePrefix("override."), arg, extras)
                in CHAIN_METHODS -> runChainCommand(method.removePrefix("chain."), arg, extras)
                in RULE_METHODS -> runRuleCommand(method.removePrefix("rule."), arg, extras)
                in SETTINGS_METHODS -> runSettingsCommand(method.removePrefix("settings."), arg, extras)
                in DNS_METHODS -> runDnsCommand(method.removePrefix("dns."), arg, extras)
                in STATE_METHODS -> runStateCommand()
                in BACKUP_METHODS -> runBackupCommand(context, method.removePrefix("backup."), arg, extras)
                else -> debugResult(false, "Unknown method: $method", method, arg)
            }
        }.getOrElse { error ->
            debugResult(false, error.message ?: error::class.simpleName ?: "Unknown error", method, arg)
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private fun isAuthorizedCaller(): Boolean {
        val uid = Binder.getCallingUid()
        return uid == Process.SHELL_UID || uid == Process.ROOT_UID || uid == Process.myUid()
    }

    private fun awaitKoin(): Boolean {
        val deadline = System.currentTimeMillis() + KOIN_WAIT_MS
        while (true) {
            val ready = runCatching { GlobalContext.getOrNull()?.get<PlatformStorage>() }.getOrNull() != null
            if (ready) return true
            if (System.currentTimeMillis() >= deadline) return false
            Thread.sleep(KOIN_POLL_MS)
        }
    }

    private companion object {
        const val KOIN_WAIT_MS = 5_000L
        const val KOIN_POLL_MS = 25L
    }
}
