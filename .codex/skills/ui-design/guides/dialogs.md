# 弹窗与 BottomSheet

## Dialog

- **Dialog 底部按钮按行为选择**：选项点击后关闭的弹窗与信息弹窗只放全宽「取消」，使用 miuix 默认次要按钮配色。输入、勾选、调整或附加操作后仍保持打开的弹窗放「取消 / 确定」，等宽、间距 12dp；确定使用 `textButtonColorsPrimary()`，内容到按钮 12dp。统一使用 [DialogButtons](../../../../android/app/src/main/kotlin/com/stelliberty/android/ui/component/DialogButtons.kt)。
- **持续交互使用草稿**：输入、选择和待执行动作先保存到弹窗草稿，确定才应用，取消与返回放弃草稿；关闭动画结束前保留当前内容，下次打开重新读取已保存值。覆写字段留空确定即使用默认值。
- **动作确认弹窗**（删除、关闭连接等）保留「取消 / 确定」，确定才执行动作；已完成的备份恢复自动重启进程，使恢复的设置重新加载。
- **附加功能放标题右侧唯一入口**：`WindowDialog` 不传 title / summary，内容首行用 [DialogHeader](../../../../android/app/src/main/kotlin/com/stelliberty/android/ui/component/DialogHeader.kt)。单功能用语义图标，多个功能用竖向三点菜单；按钮 40dp、图标 24dp，标题行到正文 12dp。清除只清空草稿，确定才写回。
- **长内容 Dialog**：miuix `WindowDialog` 在手机上不限制内容高度。外层包 `Column(Modifier.heightIn(max = 500.dp))`，滚动区用 `weight(1f, fill = false).verticalScroll(...)`，按钮作为非加权子项固定在底部。
- **Dialog 容器**保留默认 insideMargin 四周 24dp、outsideMargin 四周 12dp、maxWidth 420dp。操作行与候选行放在 Card 内，逐行四角 16dp 裁剪；普通摘要为 null。
- **弹 Dialog 的入口行设 `holdDownState`**，Dialog 打开期间保持按下态（MIUI 惯例）；先关自身再弹下一层的操作行没有按下态窗口，无需设置。
- **设置选择 Dialog**用 `SingleChoicePreference` / `SingleChoiceDialog` 组合 WindowDialog、Card 与右侧 RadioButtonPreference；入口摘要为当前值，使用内置上下箭头，点选关闭并回传选择，底部为全宽取消按钮。父弹窗有草稿时只更新父草稿；多选改用右侧 CheckboxPreference，确定才应用。
- **工具栏菜单**用 `WindowListPopup + ListPopupColumn`，自定义菜单行复用 `PopupMenuItem` 包装 `DropdownImpl`；它支持菜单的固有尺寸测量，带前后操作区的 `BasicComponent` 不适用于此容器。互斥选项保留单选语义，开关用 Checkbox，普通动作用 Button；每行独立裁剪，锚点末端对齐，菜单单独启用测试 ID 导出。
- **退出动画与草稿**：关闭先置 show=false，组件继续参与组合，在 onDismissFinished 清理临时内容；确认与 IME 提交共用校验，取消放弃草稿。

## 操作反馈

订阅、覆写与外部资源操作结果由导航层 `OperationFeedbackHost` 本地化后交给 Android 系统 Toast，使用简短文案；详情写日志，列表不保留原始错误块。宿主只在界面可见时收集消息，重复结果仍可再次提示。

## BottomSheet

内容自己负责 inset 与高度过渡：

- `WindowBottomSheet` 的 `defaultWindowInsetsPadding = true` 只装了 `imePadding()`；水平 24dp 来自 `insideMargin` 默认值；`displayCutout` 与 `captionBar`（小窗 / 桌面窗口模式的系统标题栏）只有 top 分量参与计算 `safeTopInset`、用于 `heightIn(max)`，不产生 padding。
- 内容根节点加 [`sheetContentSafePadding()`](../../../../android/app/src/main/kotlin/com/stelliberty/android/ui/util/WindowSize.kt)：取 `systemBars`（含 statusBars / navigationBars / captionBar）∪ `displayCutout` 的底部分量。它只含底部且不含 ime：sheet 已装 `imePadding()`；sheet 以 `BottomCenter` 对齐并 `widthIn(max = 640.dp)`，水平 inset 只在有缺口的一侧生效，会把居中内容推偏（二级页内容是全宽的，因此用 `horizontalCutoutPadding()`）。
- 高度过渡用 `Modifier.sheetHeightTransition()`：内容 Column 是 `wrapContentHeight()`，sheet 高度随内容瞬间变化；spec 与 sheet 入场动画同参（用 miuix 公开的 `folmeSpring`）。
