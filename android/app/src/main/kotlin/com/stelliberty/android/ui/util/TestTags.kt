package com.stelliberty.android.ui.util

// 调试脚本定位控件用的稳定 ID，命名「页面.控件」。改名等于改调试脚本的契约，动手前先查
// debug-app skill 里的引用；写进无障碍树仅 debug 包（见 App.kt 的 testTagsAsResourceId）。
object TestTags {

    object Dialog {
        fun tetherInterface(name: String) = "Dialog.Interface.$name"
        const val INPUT = "Dialog.Input"
        const val SCAN_INTERFACES = "Dialog.ScanInterfaces"
        const val CANCEL = "Dialog.Cancel"
        const val CONFIRM = "Dialog.Confirm"
        const val LIST_EDITOR = "Dialog.ListEditor"
        const val PORT_EDITOR = "Dialog.PortEditor"
        const val TEXT_EDITOR = "Dialog.TextEditor"
        const val PROVIDER_PROGRESS = "Dialog.ProviderProgress"
        const val TEMPLATE_PICKER = "Dialog.TemplatePicker"
        const val TEMPLATE_SAVE = "Dialog.TemplateSave"
        const val AGE_KEYS = "Dialog.AgeKeys"
        const val BACKUP_LOCAL = "Dialog.LocalBackup"
        const val BACKUP_RESTORE_MODE = "Dialog.RestoreMode"
        const val BACKUP_WEBDAV = "Dialog.WebDav"
        const val ROOT_TUN_DEVICE = "Dialog.RootTunDevice"
        const val TETHER_EDITOR = "Dialog.TetherEditor"
        const val TETHER_SCAN = "Dialog.TetherScan"
        const val CACHE_ACTIONS = "Dialog.CacheActions"
        const val CLOSE_CONNECTIONS = "Dialog.CloseConnections"
        const val DELETE_SUBSCRIPTION = "Dialog.DeleteSubscription"
        const val DELETE_OVERRIDE = "Dialog.DeleteOverride"
    }

    object Dns {
        const val CLEAR_DNS = "Dns.ClearDnsCache"
        const val CLEAR_FAKEIP = "Dns.ClearFakeIpCache"
    }

    object AgeKey {
        const val COPY_SECRET = "AgeKey.CopySecret"
        const val COPY_PUBLIC = "AgeKey.CopyPublic"
    }

    object Backup {
        const val RESTORE_MERGE = "Backup.RestoreMerge"
        const val RESTORE_OVERWRITE = "Backup.RestoreOverwrite"
        const val EXPORT_LOCAL = "Backup.ExportLocal"
        const val RESTORE_LOCAL = "Backup.RestoreLocal"
        const val WEBDAV_URL = "Backup.WebDavUrl"
        const val WEBDAV_USERNAME = "Backup.WebDavUsername"
        const val WEBDAV_PASSWORD = "Backup.WebDavPassword"

        fun webDavAction(name: String) = "Backup.WebDavAction.$name"
    }

    object About {
        const val CHECK_UPDATE = "About.CheckUpdate"
        const val UPDATE_CHANNEL = "About.UpdateChannel"
        const val UPDATE_STARTUP = "About.UpdateStartup"
        const val UPDATE_DIALOG = "About.UpdateDialog"
        const val RELEASE_NOTES = "About.ReleaseNotes"
        const val UPDATE_DISMISS = "About.UpdateDismiss"
        const val UPDATE_DOWNLOAD = "About.UpdateDownload"

        fun channel(name: String) = "About.Channel.$name"
    }

    object Home {
        const val START = "Home.StartButton"
        const val STOP = "Home.StopButton"
        const val RESTART = "Home.RestartButton"
        const val RELOAD = "Home.ReloadButton"
        const val STATUS_CARD = "Home.StatusCard"
        const val LATENCY_TEST = "Home.LatencyTestButton"
        const val SPEED_CARD = "Home.SpeedCard"
        const val SUBSCRIPTION_CARD = "Home.SubscriptionCard"
    }

    object Proxy {
        const val SEARCH = "Proxy.SearchButton"
        const val REFRESH = "Proxy.RefreshButton"
        const val SETTINGS = "Proxy.SettingsButton"
        const val SEARCH_FIELD = "Proxy.SearchField"
        const val SETTINGS_DIALOG = "Proxy.SettingsDialog"
        const val SETTINGS_CANCEL = "Proxy.SettingsCancel"
        const val SETTINGS_CONFIRM = "Proxy.SettingsConfirm"
        const val MODE_ENTRY = "Proxy.ModeEntry"
        const val TUN_STACK_ENTRY = "Proxy.TunStackEntry"
        const val GROUP_LAYOUT = "Proxy.GroupLayout"
        const val SINGLE_COLUMN = "Proxy.SingleColumn"
        const val HIDE_UNAVAILABLE = "Proxy.HideUnavailable"
        const val SHOW_GLOBAL_GROUP = "Proxy.ShowGlobalGroup"
        const val SORT_ENTRY = "Proxy.SortEntry"
        const val SORT_REVERSE = "Proxy.SortReverse"
        const val REFRESH_ICONS = "Proxy.RefreshIcons"

        fun groupTab(name: String) = "Proxy.GroupTab.$name"

        fun groupUnfix(name: String) = "Proxy.Group.$name.UnfixButton"

        fun nodeTest(name: String) = "Proxy.Node.$name.TestButton"

        fun group(name: String) = "Proxy.Group.$name"

        fun groupTest(name: String) = "Proxy.Group.$name.TestButton"

        fun groupToggle(name: String) = "Proxy.Group.$name.ToggleButton"

        fun node(name: String) = "Proxy.Node.$name"
    }

    object Subscription {
        const val ADD = "Subscription.AddButton"
        const val UPDATE_ALL = "Subscription.UpdateAllButton"
        const val PROGRESS = "Subscription.ProgressDialog"
        const val CANCEL_UPDATE = "Subscription.CancelUpdateButton"
        const val AUTO_DELAY = "Subscription.AutoDelayField"
        const val NAME = "Subscription.NameField"
        const val URL = "Subscription.UrlField"
        const val USER_AGENT = "Subscription.UserAgentField"
        const val AGE_KEY = "Subscription.AgeKeyField"
        const val INTERVAL = "Subscription.IntervalField"

        fun item(uuid: String) = "Subscription.Item.$uuid"

        fun delete(uuid: String) = "Subscription.Item.$uuid.DeleteButton"
    }

    object Overrides {
        const val ADD = "Overrides.AddButton"
        const val UPDATE_ALL = "Overrides.UpdateAllButton"
        const val SAVE = "Overrides.SaveButton"
        const val NAME = "Overrides.NameField"
        const val URL = "Overrides.UrlField"

        fun item(id: String) = "Overrides.Item.$id"

        fun update(id: String) = "Overrides.Item.$id.UpdateButton"

        fun select(id: String) = "Overrides.Select.$id"

        fun moveUp(id: String) = "Overrides.Select.$id.MoveUpButton"

        fun moveDown(id: String) = "Overrides.Select.$id.MoveDownButton"

        fun edit(id: String) = "Overrides.Item.$id.EditButton"

        fun delete(id: String) = "Overrides.Item.$id.DeleteButton"
    }

    // 内置链的开关、所属代理组与候选项由 groupedCardItems 生成 Settings.Entry.<key>。
    object ChainProxy {
        const val ADD = "ChainProxy.AddButton"
        const val SAVE = "ChainProxy.SaveButton"
        const val NAME = "ChainProxy.NameField"

        fun custom(id: String) = "ChainProxy.Custom.$id"

        fun toggle(id: String) = "ChainProxy.Custom.$id.Toggle"

        fun delete(id: String) = "ChainProxy.Custom.$id.DeleteButton"

        fun hop(index: Int) = "ChainProxy.Hop.$index"

        fun moveUp(index: Int) = "ChainProxy.Hop.$index.MoveUpButton"

        fun moveDown(index: Int) = "ChainProxy.Hop.$index.MoveDownButton"

        fun remove(index: Int) = "ChainProxy.Hop.$index.RemoveButton"
    }

    // 规则行按在完整列表中的序号（从 1 起）编号，搜索结果里沿用同一序号。
    object RuleOverride {
        fun template(id: String) = "RuleOverride.Template.$id"
        fun deleteTemplate(id: String) = "RuleOverride.Template.$id.Delete"
        const val ADD = "RuleOverride.AddButton"
        const val MORE = "RuleOverride.MoreButton"
        const val SAVE = "RuleOverride.SaveButton"
        const val PAYLOAD = "RuleOverride.PayloadField"
        const val POSITION = "RuleOverride.PositionField"
        const val DELETE = "RuleOverride.DeleteButton"
        const val TEMPLATE_NAME = "RuleOverride.TemplateNameField"

        fun row(number: Int) = "RuleOverride.Row.$number"

        fun toggle(number: Int) = "RuleOverride.Row.$number.Toggle"

        fun menu(index: Int) = "RuleOverride.Menu.$index"
    }

    object Settings {
        const val CHOICE_DIALOG = "Settings.ChoiceDialog"

        fun option(index: Int) = "Settings.Choice.$index"
        fun entry(key: String) = "Settings.Entry.$key"
    }

    object AppProxy {
        const val APP_LIST = "AppProxy.AppList"

        fun mode(mode: String) = "AppProxy.Mode.$mode"

        fun app(packageName: String) = "AppProxy.App.$packageName"
    }

    object Log {
        const val SOURCES = "Log.SourceTabs"
        const val CLEAR = "Log.ClearButton"
        const val EXPORT = "Log.ExportButton"
        const val LEVEL_FILTER = "Log.LevelFilter"
        const val LIST = "Log.List"
        const val STATUS = "Log.Status"

        fun level(level: String) = "Log.Level.$level"
    }

    object Connection {
        const val CLOSE_ALL = "Connection.CloseAllButton"

        fun close(id: String) = "Connection.Close.$id"
    }

    object Nav {
        fun tab(index: Int) = "Nav.Tab.$index"

        const val BACK = "Nav.BackButton"
    }
}
