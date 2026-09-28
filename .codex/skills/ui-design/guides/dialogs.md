# 弹窗与 BottomSheet

## Dialog

- **编辑类 Dialog 按钮顺序** `not_modified | cancel | confirm`：三个按钮 weight(1f) + `spacedBy(8.dp)`，confirm 用 `textButtonColorsPrimary()`。
- **长内容 Dialog**：miuix `WindowDialog` 在手机上不限制内容高度。外层包 `Column(Modifier.heightIn(max = 500.dp))`，滚动区用 `weight(1f, fill = false).verticalScroll(...)`，按钮作为非加权子项固定在底部。
- **选项列表 Dialog**（点入口行弹出、内含若干操作行）：`insideMargin = DpSize(0.dp, 24.dp)`。水平 0 让 `ArrowPreference` 全出血、涟漪铺满整宽，行自带 `horizontal = 24.dp` 内缩；垂直 24 补足内置 title 的顶距（miuix title 自身只有 `bottom 12dp`）。
- **弹 Dialog 的入口行设 `holdDownState`**，Dialog 打开期间保持按下态（MIUI 惯例）；先关自身再弹下一层的操作行没有按下态窗口，无需设置。
- **单选菜单**（模式、TUN 栈、日志等级这类互斥选项）用 miuix `WindowListPopup` + `ListPopupColumn` + `DropdownImpl`，选中行高亮并勾选，点选即生效并关闭。工具栏入口使用 `MenuPositionProvider` + `TopEnd` 对齐，打开期间设置 `holdDownState`，菜单内容单独启用测试 ID 导出。

## BottomSheet

内容自己负责 inset 与高度过渡：

- `WindowBottomSheet` 的 `defaultWindowInsetsPadding = true` 只装了 `imePadding()`；水平 24dp 来自 `insideMargin` 默认值；`displayCutout` 与 `captionBar`（小窗 / 桌面窗口模式的系统标题栏）只有 top 分量参与计算 `safeTopInset`、用于 `heightIn(max)`，不产生 padding。
- 内容根节点加 [`sheetContentSafePadding()`](../../../../android/app/src/main/kotlin/com/stelliberty/android/ui/util/WindowSize.kt)：取 `systemBars`（含 statusBars / navigationBars / captionBar）∪ `displayCutout` 的底部分量。它只含底部且不含 ime：sheet 已装 `imePadding()`；sheet 以 `BottomCenter` 对齐并 `widthIn(max = 640.dp)`，水平 inset 只在有缺口的一侧生效，会把居中内容推偏（二级页内容是全宽的，因此用 `horizontalCutoutPadding()`）。
- 高度过渡用 `Modifier.sheetHeightTransition()`：内容 Column 是 `wrapContentHeight()`，sheet 高度随内容瞬间变化；spec 与 sheet 入场动画同参（用 miuix 公开的 `folmeSpring`）。
