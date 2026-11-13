# 主题与配色

## 深浅色判定

- `ThemeConfig.resolveIsDark(systemDark)` 是 colorMode → isDark 的唯一实现，组合树内一律读 `LocalAppDarkMode.current`，用户强制深 / 浅色时各处保持一致（`isSystemInDarkTheme()` 只反映系统设置）。
- 主题枚举的用户可见名集中在 [ThemeLabels.kt](../../../../android/app/src/main/kotlin/com/stelliberty/android/ui/theme/ThemeLabels.kt)，各处复用。
- XML 主题层只认系统 uiMode：splash 与 windowBackground 走资源限定符，`values-night/themes.xml` 与 `values/` 成对维护（缺失时深色冷启动会闪白帧）。
- 冷启动背景交接由 core-splashscreen 完成：MainActivity 挂 `Theme.Stelliberty.Starting`；`installSplashScreen()` 先于 `super.onCreate`（`postSplashScreenTheme` 靠它换回 `Theme.Stelliberty`）；`setKeepOnScreenCondition { !contentReady }` 把 splash 保持到 Compose 首帧，中间的窗口背景始终被遮住。app 内强制深色而系统为浅色时，XML 层仍按系统显示，根治需要 `UiModeManager.setApplicationNightMode`。
- `LocalAppMonetEnabled` 标记 Monet：StatusColors 仅 Running 态跟随动态取色，Pending / Stopped 的警示黄 / 红固定。
- 顶栏毛玻璃由 `blurEnabled` 控制，底栏由 `bottomBarBlurEnabled` 独立控制；旧设置只用于初始化新增的底栏值。低于 Android 13 的模糊入口禁用，保留已保存的值。

## 语义色

状态、延迟、按钮、错误色统一走 `ui.theme.StatusColors`（`runState` / `delay` / `actionButton` / `danger` / `healthy` / `warning` / `neutral` / `trafficUpload` / `trafficDownload` / `usage`）。界面颜色来自 `MiuixTheme.colorScheme.*` 与 `StatusColors`；关于页使用原启动前景图形；开启模糊且支持采样时，图标与应用名使用 LogoLight / LogoDark 对流光混色，关闭效果时使用 onBackground。流光按 `LocalAppDarkMode` 选择项目预设。
