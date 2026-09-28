// 由 scripts/gen_icons.py 生成，勿手改。改图标请改脚本里的 ICONS 表后重跑。
package com.stelliberty.android.ui.icon

import androidx.compose.ui.graphics.vector.ImageVector

/** 全应用图标入口，路径来自 MingCute。`Nav*Active` 是 filled 风格，其余为 regular（线性描边）。 */
object AppIcons {
    val Add: ImageVector get() = MingCuteAdd
    val ArrowRight: ImageVector get() = MingCuteArrowRight
    val Back: ImageVector get() = MingCuteArrowLeft
    val Backup: ImageVector get() = MingCuteUpload
    val Check: ImageVector get() = MingCuteCheck
    val ChevronRight: ImageVector get() = MingCuteRightSmall
    val Close: ImageVector get() = MingCuteClose
    val Delete: ImageVector get() = MingCuteDelete2
    val Info: ImageVector get() = MingCuteInformation
    val More: ImageVector get() = MingCuteMore1
    val MoveDown: ImageVector get() = MingCuteDownSmall
    val MoveUp: ImageVector get() = MingCuteUpSmall
    val NavHome: ImageVector get() = MingCuteHome2
    val NavHomeActive: ImageVector get() = MingCuteHome2Filled
    val NavProxy: ImageVector get() = MingCuteShieldShape
    val NavProxyActive: ImageVector get() = MingCuteShieldShapeFilled
    val NavSettings: ImageVector get() = MingCuteSettings3
    val NavSettingsActive: ImageVector get() = MingCuteSettings3Filled
    val NavSubscription: ImageVector get() = MingCuteMoonCloudy
    val NavSubscriptionActive: ImageVector get() = MingCuteMoonCloudyFilled
    val Pin: ImageVector get() = MingCutePin
    val Refresh: ImageVector get() = MingCuteRefresh2
    val Restore: ImageVector get() = MingCuteDownload
    val Search: ImageVector get() = MingCuteSearch
    val SearchCleanup: ImageVector get() = MingCuteCloseCircle
    val Sort: ImageVector get() = MingCuteSortAscending
    val TestDelay: ImageVector get() = MingCuteDashboard2
    val Unpin: ImageVector get() = MingCuteCloseCircle
    val Wifi: ImageVector get() = MingCuteWifi
}
