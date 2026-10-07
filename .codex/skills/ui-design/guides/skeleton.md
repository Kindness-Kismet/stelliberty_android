# 页面骨架与适配

## 骨架

- Scaffold + TopAppBar(scrollBehavior) + LazyColumn。LazyColumn 加 `.scrollEndHaptic().overScrollVertical().nestedScroll(scrollBehavior.nestedScrollConnection)`；`contentPadding` 只设 top，底部由末尾 Spacer 承担。首个 item 是 Card / 表单时加 `item { Spacer(12.dp) }`（SmallTitle / RestartRequiredHint 自带 8dp 上下边距，直接作为开头）；末尾统一 `item { Spacer(Modifier.height(24.dp).navigationBarsPadding()) }`。
- 二级页用末尾 Spacer 自适应底距，签名里只保留这一种方案（再加 `bottomPadding: Dp` 会叠成双倍底距）。4 个 Pager Tab 由外层 `MainPage` Scaffold 持有 bottomBar，接收 `bottomPadding` 透传给 `contentPadding`。
- Card 水平间距 12.dp，每项统一 `padding(horizontal = 12.dp).padding(bottom = 12.dp)`（`Arrangement.spacedBy` 会在 stickyHeader 与内容之间多出一份间距）。TextField 表单直接用同样的 padding，外层不包 Card。

- 可编辑表单的滚动容器加 `.imePadding()`，键盘弹出时缩小可视区域，焦点输入框才能自动滚动到键盘上方。

## 导航

- miuix NavDisplay + 自定义 Navigator（push / replace / pop / popUntil）+ LocalNavigator。back stack 经 Route sealed 多态序列化持久化（`NavBackStackSaver`），新增路由加 `@Serializable` 即支持进程死亡恢复。`sealed interface Route` 自身保留 `@Serializable`：缺了照样编译通过，恢复时抛 `SerializationException`。
- 四个主页面保持组合：`HorizontalPager` 的 `beyondViewportPageCount = pageCount - 1`，切页动画中节点与毛玻璃保持存活。每页用 `rememberLifecycleOwner` 继承父生命周期，按 `visiblePagesInfo` 把离屏页限制到 `CREATED`，暂停其 `collectAsStateWithLifecycle`；逐帧动画也单独跟随生命周期。此策略针对固定四页，页数增加时重新评估内存与首屏成本。
- 跨页面任务的弹窗在 `AppNavigation` 层收集状态并显示，不放进 Pager 或 NavDisplay 的页面生命周期内；启动时订阅更新由 `SubscriptionUpdateProgressDialog` 统一承接，取消与完成状态才能在首页及时生效。

## 毛玻璃

所有页面 Scaffold 用 `BlurredBar` 包裹 TopAppBar / NavigationBar，MainPage 与每个二级页各持一份 backdrop（允许嵌套 layerBackdrop）。模式：顶层 `val backdrop = rememberBlurBackdrop()`、`blurActive = backdrop != null`、bar 色 `if (blurActive) Color.Transparent else surface`；内容区 LazyColumn 追加 `.then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)`。搜索页在 BlurredBar 内套 `searchStatus.TopAppBarAnim(backgroundColor = 同 bar 色)`。

## 搜索页

`rememberSearchScreenStatus(label)` 持有状态并同步 label，`SearchResultStatusEffect(status, isResultEmpty)` 回填三分支结果态，`rememberSearchBarTopPadding(scrollBehavior)` 提供顶距。拆成两步：搜索词决定过滤结果、过滤结果决定结果态，中间隔着调用方的过滤逻辑，分开后结果态由 Effect 回填，组合期无需回写 State。

## 宽屏与刘海

- **宽屏**（窗口宽 ≥ 600dp `WideScreenMinWidth`，用缩放前的 `LocalPlatformDensity` 测量，界面缩放不影响外壳）：底栏换成可展开收起的侧边 `NavigationRail`（默认收起；Home Tab 用 `MiuixIcons.Home`，与展开按钮的图标区分开）。inset 上 rail 吸收起始侧，内容区 `consumeWindowInsets(Start)` + `windowInsetsPadding(systemBars∪displayCutout .only(End))`。TopAppBar 统一走 [AdaptiveTopAppBar](../../../../android/app/src/main/kotlin/com/stelliberty/android/ui/component/AdaptiveTopAppBar.kt)（宽屏固定不折叠，节省纵向空间）；AboutScreen 例外，用固定的 SmallTopAppBar 配合 hero 视差。搜索框的动态 top padding 在宽屏恒为 0。
- **内容居中**用 `WideContentBox { sidePadding -> ... }`：LazyColumn 保持全宽（两侧也能滚动），只把 `sidePadding` 加进 `contentPadding`，内容宽度限制在 `MaxContentWidth = 800dp`（与 600dp 外壳阈值相互独立）。是否居中复用 `rememberIsWideScreen()`，与外壳共用同一判定（densityScale ≠ 1 时独立比较阈值会得出不同结论）。
- **横屏刘海**：miuix `Scaffold` 只给 bar 处理 inset，二级页 `contentPadding` 只含 top。每个二级页的根 LazyColumn 在 `.fillMaxSize()` 后加 `Modifier.horizontalCutoutPadding()`（只补水平 `displayCutout ∪ navigationBars`，竖屏为 0）；顶栏由自身 inset 处理。AboutScreen 的内容侧自行处理，只给它的 `SmallTopAppBar(defaultWindowInsetsPadding = false)` 加该 modifier。4 个主 Tab 的内容已居中在缺口内侧。
