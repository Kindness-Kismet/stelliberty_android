# 组件与形状

## miuix 组件

组件用 miuix，图标统一经 `AppIcons` 使用 MingCute 线框；底栏当前项使用同款填充态。页面和 Dialog 的 IconButton 为 40×40dp，图标为 24×24dp；底栏图标为 26dp，点击采用 `NavigationTabItem` 的透明度反馈。Badge 用 `squircleBackground(color, 3.dp)` + 9.sp Bold Monospace。

## 自定义形状

小尺寸 `CircularProgressIndicator` 必须显式传 `size` 与 `strokeWidth`。绘制直径取自 `size` 参数，单独用 `Modifier.size` 缩小占位会让默认圆环画到控件外面。

非 miuix 组件的形状用 squircle modifier（`top.yukonga.miuix.kmp.squircle.*`），与 miuix 圆角保持一致。按场景选择：

- 纯色背景、不可点击 → `squircleBackground`（无 offscreen layer，再叠 clip 会多一层）；
- 图片或必须裁剪 → `squircleClip`（一个 offscreen layer）；
- 可点击 → 在点击与状态层外侧放 `squircleClip` 或 `squircleSurface`，保留 miuix 默认状态层；每行独立四角 16dp，按钮复用自身 Shape。

## 多行卡片拆成 lazy item

`LazyColumn` 里的多行卡片用 [GroupedCardItems](../../../../android/app/src/main/kotlin/com/stelliberty/android/ui/component/GroupedCardItems.kt) 拆成逐行的 lazy item：`groupedCardItems(keyPrefix, items = listOf(CardItem("k") { row() }, ...))`，由 `CardSegment` 分角拼回视觉连续的卡片，滚动与展开时按行组合。

- 分角背景：首 / 末段用 `squircleBackground`，中间段用纯 `background`；每行内容单独 `squircleClip(16.dp)`，按压轮廓不随分段缺角。
- 语义对齐 miuix Card：surfaceContainer + 16.dp 圆角；preference 自带内边距，段的 `insidePadding = 0`。
- `outerBottomPadding` 取所替换 Card 的 bottom padding；条件行用 `buildList`。
- `groupedCardItems` 本身不带 item 动画（拆分对用户不可见）。需要动画时在 item 内加 `Modifier.animateItem(...)`，并给 placement spec 一个有效动画，下方各组才会平滑换位。

纯静态文本、表单与少量固定行保留单个 `item { Card }`。AboutScreen 保留原主体视差与采样材质，更新区以视口最小高度保证可滚动；版本保留 v 前缀，更新卡片直接展示控件。版本下方快捷入口置于滚动层：前方 LibraryBooks 打开独立开源库页，后方 GitHub 打开项目源码；按钮底部到更新卡片保留 70dp。开源库列表复用设置子页骨架与逐行 lazy 卡片，返回关于页。关于页外链经 `openUriOrShowToast` 处理；设备缺少浏览器时保留当前页并显示本地化提示。
