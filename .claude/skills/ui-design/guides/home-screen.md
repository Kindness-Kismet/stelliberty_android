# 首页

## 状态卡

- **中性底色 + 仪表布局**：顶部是状态圆点、状态文字和隧道模式；中部是运行时长与当前订阅；底部是内核版本与有效端口。只有状态圆点带状态色，数据保持视觉主体。
- **时长固定为 `HH:mm:ss`**：时长与订阅名统一 20.sp 字号、28.sp 行高与字重，时长启用等宽数字，两列首行按基线对齐。底部内核与端口共用 12.sp 字号、18.sp 行高，也按基线对齐。文字去掉字体额外留白，字间距为 0。停止或尚未取得时长时显示 `00:00:00`；满 100 小时后固定显示 `99:99:99`。订阅名最多两行，超长省略。
- **启动中与停止中都显示不定进度条**，两种操作的耗时都无法预估。端口只展示运行时配置中大于 0 的值，停止后清空。

## 操作区

操作区恒为一张卡，内容随状态切换：运行态是 `热重载 | 停止 | 重启` 三个 `FlatActionButton`，之间用上下内缩 12dp 的 `VerticalDivider` 分栏；停止态是单个「启动」；过渡态是单个禁用提示。按钮前景色走 `StatusColors.actionButton`、背景透明，语义由文字色区分，整卡视觉轻于上方状态卡。Row 用 `height(IntrinsicSize.Min)`，分隔线撑满行高。

## 卡内水印绘制

速度卡折线图 [TrafficSparkline](../../../../android/app/src/main/kotlin/com/stelliberty/android/ui/screen/home/TrafficSparkline.kt) 与订阅卡用量条 [SubscriptionUsageBar](../../../../android/app/src/main/kotlin/com/stelliberty/android/ui/screen/home/SubscriptionUsageBar.kt) 共用 `HomeShared.kt` 的 `Watermark*` 常量（用量条的形状就是折线退化成的方波），新增水印同样复用，同行两卡的绘图区高度与浓度才一致。miuix `Card` 走 `squircleSurface`（fill + clip），子内容自动裁到圆角，卡内直接铺满绘制即可。

1. `insideMargin = PaddingValues(0.dp)` + 内层 `Box(fillMaxSize)`，文字放进 `Column(padding(16.dp))`，绘制层才能铺满（`BasicCard` 用 `propagateMinConstraints = true`，`fillMaxHeight` 的 Card 会把 min 约束传给 content）。
2. 绘制层用 `Modifier.matchParentSize()`，不参与测量，卡片高度由文字决定，同一 `IntrinsicSize.Min` Row 里的邻卡高度保持一致。
3. 绘制层自身加 `clipToBounds()`，把滑动中的曲线限制在卡内。

## 1Hz 数据的连续动画

- `TrafficHistory.seq` 单调递增，既是动画 key（能区分「新采样点与上一点等值」），也是动画目标值本身：`Animatable` 追踪 `seq` 这个绝对量，`animateTo(seq)`，下一帧继续以绝对量衔接；进度由 `1 - (seq - scroll.value)` 反推。新点随重组立即进入绘制，而 `LaunchedEffect` 至少晚一帧才启动动画；绝对量方案下动画未启动时进度天然为 0，画面保持连续（相对进度的 `snapTo(0) + animateTo(1)` 会在这一帧来回抖动）。
- 单格时长取 1100ms，略长于 1Hz 推送周期，动画总在跑完前被下一帧接上（稳态落后约 0.1 格），曲线持续滑动。
- 滚动窗口的 x 步长取 `capacity - 2`：窗口填满后，最老的点恰好从 `x=0` 滑到 `x=-step`，左边缘始终有内容。
- 滑入进度与纵轴上限都在 `onDrawBehind` 里读（只重绘不重组）。
- 滚动与纵轴缩放动画放在 `repeatOnLifecycle(STARTED)` 内：主页面保持组合，离屏时动画随生命周期暂停（仅暂停数据收集停不下进行中的动画）。
