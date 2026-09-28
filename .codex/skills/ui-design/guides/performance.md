# 状态形状与帧率

## Flow 与状态形状

- 屏幕一律用 `collectAsStateWithLifecycle()`，进入后台即停止收集（`collectAsState` 在后台仍驱动重组）。
- UiState `data class` 标 `@Immutable`；大集合字段用 `ImmutableList` / `ImmutableMap` / `PersistentSet`，且从生产端（Repository / Provider）起就是这个类型。作为 composable 参数的集合全程保持不可变类型，composable 才可 skip。

## 帧率级读取

- 滚动折叠比例、动画进度这类每帧变化的值，以 `State<T>` 或 `() -> T` 透传，由消费方在 **layout / draw 阶段**读：布局用自定义 `layout{}` modifier（`SearchBar.topInset` 是现成范式），绘制用 `graphicsLayer{}` / `onDrawBehind{}`。`derivedStateOf` + `by` 解包、`Modifier.padding(value)`、`Modifier.rotate/scale/alpha` 都属于组合期读，会让整个 restart scope 每帧失效；后几种合并进已有的 `graphicsLayer{}` 即可。夹紧成布尔或离散档位后可以在组合期读。
- 持续动画在不可见时停止：[BgEffectModifier](../../../../android/app/src/main/kotlin/com/stelliberty/android/ui/component/effect/BgEffectModifier.kt) 的帧循环与 [BgEffectBackground](../../../../android/app/src/main/kotlin/com/stelliberty/android/ui/component/effect/BgEffectBackground.kt) 的色阶推进都由 `alpha()` 门控，About 页滚到底、alpha 归零时两者停转。判定放在 draw 里：alpha 是延迟读取的 lambda，draw 阶段的快照读会在 alpha 恢复非零时自动重新触发 draw。协程侧的门控用 `snapshotFlow { alpha() > 0f }.first { it }` 挂起。

## ViewModel 侧的生命周期门控

- **Koin single VM 的初始化挂在订阅上**：ViewModel 随冷启动构造、`onCleared` 不会触发，放在 `init { load() }` 等于每次启动都为可能不打开的页面提前干活。分应用代理的 PackageManager 全量枚举（加逐包 `loadLabel`，数百毫秒 CPU + 常驻全量列表）挂在 `filteredAppsFlow` 的 `onStart` 上，`stateIn` 用 `WhileSubscribed`。分应用列表用「持 INTERNET 权限的包名集合 + `getInstalledApplications`」两次窄查询（`getInstalledPackages(GET_PERMISSIONS)` 会把完整权限数组过 Binder，易触发 `TransactionTooLargeException`）。
- **viewModelScope 的轮询按 UI 可见性门控**：`HomeViewModel` 的系统信息采样（`NetworkInterface` 枚举 + `/proc/<pid>/stat`，均阻塞，放在 IO）、`/configs` 轮询、uptime 计数都挂在 `viewModelScope` 上，统一收敛到 `pollWhileVisible(interval)`，由 `MainActivity.onStart/onStop` 经 `setUiVisible` 驱动。
- **日志列表按显示帧率发射**：日志风暴可达每秒数百行。`appendLog` 只写 buffer 并置 `logsDirty`；独立的 `flushJob` 每 120ms 执行一次 `_logs.value = buffer.toPersistentList()`，把重组与 500 条 key diff 降到显示帧率。autoScroll 的 `LaunchedEffect` key 用单调递增的 `logs.lastOrNull()?.id`（缓冲写满后 `logs.size` 恒为 `MAX_LOGS`）。
