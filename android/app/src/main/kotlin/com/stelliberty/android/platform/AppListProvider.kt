package com.stelliberty.android.platform

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppInfo(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
)

class AppListProvider(private val context: PlatformContext) {

    suspend fun resolveUids(packageNames: Set<String>): Set<Int> = withContext(Dispatchers.IO) {
        if (packageNames.isEmpty()) return@withContext emptySet()
        val pm = context.packageManager
        packageNames.mapNotNullTo(mutableSetOf()) { pkg ->
            try {
                pm.getApplicationInfo(pkg, 0).uid
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }
    }

    // 故意不用带权限查询的那个接口：它会把每个应用的完整权限数组通过跨进程调用搬过来，
    // 装了很多应用时直接因数据量过大崩溃。改成两次窄查询，各自只取包名和基本信息。
    suspend fun getInstalledApps(): ImmutableList<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val selfPackage = context.packageName

        @Suppress("DEPRECATION")
        val internetHolders = pm
            .getPackagesHoldingPermissions(arrayOf(Manifest.permission.INTERNET), 0)
            .mapTo(HashSet()) { it.packageName }

        @Suppress("DEPRECATION")
        pm.getInstalledApplications(0)
            .filter { app ->
                app.packageName != selfPackage &&
                        (app.packageName in internetHolders ||
                                app.flags and ApplicationInfo.FLAG_SYSTEM != 0)
            }
            .map { app ->
                AppInfo(
                    packageName = app.packageName,
                    appName = app.loadLabel(pm).toString(),
                    isSystemApp = app.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                )
            }
            .sortedBy { it.appName.lowercase() }
            .toPersistentList()
    }
}
