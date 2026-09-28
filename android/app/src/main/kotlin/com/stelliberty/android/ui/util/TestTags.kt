package com.stelliberty.android.ui.util

// 调试脚本定位控件用的稳定 ID，命名「页面.控件」。改名等于改调试脚本的契约，动手前先查
// debug-app skill 里的引用；写进无障碍树仅 debug 包（见 App.kt 的 testTagsAsResourceId）。
object TestTags {

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
        const val SORT = "Proxy.SortButton"
        const val MORE = "Proxy.MoreButton"
        const val SEARCH_FIELD = "Proxy.SearchField"
        const val MODE_ENTRY = "Proxy.ModeEntry"
        const val TUN_STACK_ENTRY = "Proxy.TunStackEntry"
        const val GROUP_LAYOUT = "Proxy.GroupLayout"

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
        const val AUTO_DELAY = "Subscription.AutoDelayField"

        fun item(uuid: String) = "Subscription.Item.$uuid"
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
        const val TUN_MODE = "Settings.TunModeEntry"

        fun entry(key: String) = "Settings.Entry.$key"
    }

    object AppProxy {
        fun mode(mode: String) = "AppProxy.Mode.$mode"

        fun app(packageName: String) = "AppProxy.App.$packageName"
    }

    object Log {
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
