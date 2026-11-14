# 页面骨架与适配

## 骨架

- Scaffold + TopAppBar(scrollBehavior) + LazyColumn。LazyColumn 加 `.scrollEndHaptic().overScrollVertical().nestedScroll(scrollBehavior.nestedScrollConnection)`；设置页 `contentPadding.top` 为顶栏占高 + 10dp，底距由 `pageBottomPadding` 合并安全区与页面留白，不再叠加末尾导航 Spacer。
- 子页的应用底栏占高为 0。四个 Pager Tab 由外层 `MainPage` Scaffold 持有共享 bottomBar，将实际占高透传；系统导航栏显示时额外留白 12dp，两类底栏均隐藏时 20dp。
- 设置首页水平 12dp、设置子页 16dp，功能块间距 12dp；普通块不显示标题，普通行不显示摘要。间距集中在 `PreferenceLayout`，逐行卡片通过 `LocalPreferenceHorizontalPadding` 继承页面边距。

- 可编辑表单的滚动容器加 `.imePadding()`，键盘弹出时缩小可视区域，焦点输入框才能自动滚动到键盘上方。

## 导航

- miuix NavDisplay + 自定义 Navigator（push / replace / pop / popUntil）+ LocalNavigator。back stack 经 Route sealed 多态序列化持久化（`NavBackStackSaver`），新增路由加 `@Serializable` 即支持进程死亡恢复。`sealed interface Route` 自身保留 `@Serializable`：缺了照样编译通过，恢复时抛 `SerializationException`。
- 根页面使用稳定业务 key 的 `HorizontalPager`，`beyondViewportPageCount = 0`，每页独立保留可保存状态。每页用 `rememberLifecycleOwner` 继承父生命周期，按 `visiblePagesInfo` 限制离屏页到 `CREATED`；逐帧动画另外按 RESUMED 门控。
- 跨页面任务的弹窗在 `AppNavigation` 层收集状态并显示，不放进 Pager 或 NavDisplay 的页面生命周期内；启动时订阅更新由 `SubscriptionUpdateProgressDialog` 统一承接，取消与完成状态才能在首页及时生效。

## 毛玻璃

所有页面 Scaffold 用 `BlurredBar` 包裹 TopAppBar / NavigationBar，MainPage 与每个二级页各持一份 backdrop（允许嵌套 layerBackdrop）。模式：顶层 `val backdrop = rememberBlurBackdrop()`、`blurActive = backdrop != null`、bar 色 `if (blurActive) Color.Transparent else surface`；内容区 LazyColumn 追加 `.then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)`。设置搜索在 BlurredBar 内使用 `SearchTopAppBar`。

## 搜索页

设置搜索使用 `SearchTopAppBar`，组合内置 `SearchBar + InputField`；查询为空显示正常内容，结果进入真实父子路由。输入与返回处理同时受搜索状态、页面 RESUMED 和当前 Tab 控制；返回顺序为键盘、弹层、搜索、当前子页，根页交系统退出。

## 宽屏与刘海

- **宽屏**（窗口宽 ≥ 600dp `WideScreenMinWidth`，用缩放前的 `LocalPlatformDensity` 测量，界面缩放不影响外壳）：底栏换成可展开收起的侧边 `NavigationRail`（默认收起；Home Tab 用 `MiuixIcons.Home`，与展开按钮的图标区分开）。inset 上 rail 吸收起始侧，内容区 `consumeWindowInsets(Start)` + `windowInsetsPadding(systemBars∪displayCutout .only(End))`。TopAppBar 统一走 [AdaptiveTopAppBar](../../../../android/app/src/main/kotlin/com/stelliberty/android/ui/component/AdaptiveTopAppBar.kt)（宽屏固定不折叠，节省纵向空间）；AboutScreen 保留原主体视差布局，SmallTopAppBar 的标题初始隐藏、主体折叠后显示，更新卡片按模糊设置采样背景。搜索框的动态 top padding 在宽屏恒为 0。
- **内容居中**用 `WideContentBox { sidePadding -> ... }`：LazyColumn 保持全宽（两侧也能滚动），只把 `sidePadding` 加进 `contentPadding`，内容宽度限制在 `MaxContentWidth = 800dp`（与 600dp 外壳阈值相互独立）。是否居中复用 `rememberIsWideScreen()`，与外壳共用同一判定（densityScale ≠ 1 时独立比较阈值会得出不同结论）。
- **横屏刘海**：miuix `Scaffold` 只给 bar 处理 inset，二级页 `contentPadding` 只含 top。每个二级页的根 LazyColumn 在 `.fillMaxSize()` 后加 `Modifier.horizontalCutoutPadding()`（只补水平 `displayCutout ∪ navigationBars`，竖屏为 0）；顶栏由自身 inset 处理。AboutScreen 由 `Scaffold.contentWindowInsets` 合并系统栏与刘海的水平 inset，SmallTopAppBar 关闭默认窗口 inset。4 个主 Tab 的内容已居中在缺口内侧。
