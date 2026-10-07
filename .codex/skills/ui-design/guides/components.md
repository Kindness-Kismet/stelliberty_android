# 组件与形状

## miuix 组件

组件用 miuix（内部已按 squircle 渲染圆角）。返回按钮 `MiuixIcons.Back`；底栏图标 Sidebar / Tune / UploadCloud / Settings；Badge 用 `squircleBackground(color, 3.dp)` + 9.sp Bold Monospace；操作 IconButton `minHeight / minWidth = 35.dp` + `secondaryContainer`。

## 自定义形状

非 miuix 组件的形状用 squircle modifier（`top.yukonga.miuix.kmp.squircle.*`），与 miuix 圆角保持一致。按场景选择：

- 纯色背景、不可点击 → `squircleBackground`（无 offscreen layer，再叠 clip 会多一层）；
- 图片或必须裁剪 → `squircleClip`（一个 offscreen layer）；
- 可点击 → `squircleSurface` + `.clickable{}`（涟漪裁进圆角）；条件可点击时，不可点的状态用 `squircleBackground`。

## 多行卡片拆成 lazy item

`LazyColumn` 里的多行卡片用 [GroupedCardItems](../../../../android/app/src/main/kotlin/com/stelliberty/android/ui/component/GroupedCardItems.kt) 拆成逐行的 lazy item：`groupedCardItems(keyPrefix, items = listOf(CardItem("k") { row() }, ...))`，由 `CardSegment` 分角拼回视觉连续的卡片，滚动与展开时按行组合。

- 分角背景：首 / 末段用 `squircleSurface`（fill + clip，把段内 clickable 的涟漪裁进圆角），中间段用纯 `background`。
- 语义对齐 miuix Card：surfaceContainer + 16.dp 圆角；preference 自带内边距，段的 `insidePadding = 0`。
- `outerBottomPadding` 取所替换 Card 的 bottom padding；条件行用 `buildList`。
- `groupedCardItems` 本身不带 item 动画（拆分对用户不可见）。需要动画时在 item 内加 `Modifier.animateItem(...)`，并给 placement spec 一个有效动画，下方各组才会平滑换位。

保持单个 `item { Card }` 的场景：纯静态文本卡（RootSettings 警告）与带视差 + textureBlur 的 AboutScreen。AboutScreen 的内容 Column 用 `heightIn(min = 视口高)`，横屏矮视口下内容仍可滚动（`fillParentMaxHeight()` 会把高度锁成恰好一屏）。
