# 首页

## 状态卡

- **中性卡片与模式胶囊**：状态卡用 22.dp 圆角与 16.dp 内边距，顶部为状态圆点、19.sp 半粗体状态文字与盾牌模式胶囊；中部为时长和当前订阅，标签配时钟 / RSS 图标；底部为内核版本与有效端口。
- **时长固定为 `HH:mm:ss`**：20.sp 等宽数字，订阅名为 15.sp、单行省略，与状态标题统一使用 600 字重，两列数值按基线对齐。标签和底部为 11.sp，文字去掉额外留白。过渡态保留最后一次运行时长，停止或没有时长时显示 `00:00:00`；满 100 小时显示 `99:99:99`。
- **过渡保持卡片高度**：启动与停止使用底边 3.dp 不定流光，底部 18.dp 信息行放 14.dp / 1.5.dp 圆环，与提示间隔 6.dp。圆环和文案使用当前操作的语义色。进度只表达真实状态，不显示模拟阶段或百分比；停止后不显示端口。圆点脉冲与流光只在绘制阶段读取动画，按页面生命周期暂停。

## 操作区

独立操作卡使用 16.dp 圆角、4.dp 内边距与 38.dp 按钮，文字为 13.sp。运行态为「热重载 / 停止 / 重启」，之间保留短分隔线；过渡态收起三栏和分隔线，改为单个居中的禁用文字提示，不放圆环。启动提示用 warning 色，停止提示用主题主色，均为半透明文字。停止态显示填充主色的启动按钮。`HomeControlAction` 仅为上方状态卡保留点击意图。

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
