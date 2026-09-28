# Baseline Profile

Baseline Profile 在本地真机生成：`:baselineprofile` 采集冷启动 + 4 Tab 路径，`:app:generateReleaseBaselineProfile` 回写 `android/app/src/release/generated/baselineProfiles/`，产物提交进仓库。CI 只消费（采集需要已授权的真机，发布任务不启动设备），因此 `automaticGenerationDuringBuild = false`，采集任务与 `assembleRelease` 保持独立。

## 配置约束

1. `androidx.profileinstaller` 是必需依赖：侧载分发拿不到 Play 云端 profile，靠它让 ART 安装 APK 内的 `baseline.prof`。
2. 在 `finalizeDsl` 里关闭 `nonMinifiedRelease` 的 `optimization.enable`：插件只处理旧 DSL 的 `isMinifyEnabled`，AGP 9 的新开关要自己关。开着时 generator 采到的是混淆后的类名，与 release 的 mapping 对不上，profile 静默失效。判据：`mapping/nonMinifiedRelease/mapping.txt` 不存在，APK 内 `com/stelliberty/android` 下的类名有数千个。
3. benchmark 使用 `1.5.0-alpha07+`：stable 的 `1.4.1` 在 AGP 9 下 apply 即失败。

## generator 的三段 workaround

三段缺一不可，任一缺失都会得到「Generated Profile is empty」：

- `cmd package compile -f -m verify` 强制降级：HyperOS 装包时就按 APK 内的 prof AOT 成 speed-profile，benchmark 的 `compile --reset` 只回到这个状态，运行期不再 JIT。
- 先 `pm grant POST_NOTIFICATIONS`：首帧前的授权框会挡住 MainActivity，ROM 弹窗按钮没有 AOSP resource-id，按文案点击会随 locale 变化。
- `startup` 末尾等够 ART profile saver 的延迟（`-Xps-save-resolved-classes-delay-ms` 默认 5s）：`startActivityAndWait()` 返回时 profile 尚未写出。

切 Tab 用 `HorizontalPager` 横滑，`swipe` 后 `SystemClock.sleep` 等动画收敛：Compose 动画不向 accessibility 报告 busy，`waitForIdle()` 会提前返回。

收益范围：`System.loadLibrary("mihomo")` 加载 56MB 库属于 native 固定开销，Baseline Profile 优化的是 Compose 首帧、Koin 图构建这类字节码路径。
