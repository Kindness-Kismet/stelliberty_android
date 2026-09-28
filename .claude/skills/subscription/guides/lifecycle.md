# 活跃订阅与更新

## 导入后的活跃订阅

`addSubscription` / `addFromFile` 成功后保持当前 active；仅首次导入（提交后列表只有这一条）由 `commitPending` 自动激活。当前订阅存在 `subscriptions/selection_state.json`，经 `SubscriptionStore.currentId()` 读取。

## 更新后自动重启

mihomo 只在进程启动时读一次 config.yaml，embed mode 下 `/configs` / `/restart` 返回 404，更新生效只能靠重启进程。由 `RESTART_AFTER_PROFILE_UPDATE`（默认开）控制，三个更新入口成功后调用 `restartAfterProfileUpdate(uuid)`：`fetchSubscription` / 批量更新（`updateAllSubscriptions` 与 `runStartupUpdates`）/ `ProfileWorker.runUpdate`；编辑订阅不触发。**active 判定放在 controller 内**（读 `SubscriptionStore`）：前台与后台 Worker 共用这一处判定才能保持一致；非 active 订阅的更新直接忽略。批量更新在整轮结束后只重启一次，后续经 mixed-port 的下载全程有网。

## 仓库单例与流量数据合并

`SubscriptionRepositoryImpl` 由 Koin `single` 提供，SubscriptionViewModel、HomeViewModel 与 `ProfileWorker` 共用同一实例，看到同一份流量快照。

订阅页与首页流量栏的数据强一致：`resolveProfile` 在 combine 内按 `pending > live provider snapshot > imported 列表` 合并三层，`_liveProvider` 携带 `subscriptionId` 做归属校验。获取结果（创建与更新时间、失败记录、内置链式代理名）始终以已导入版本为准，草稿只覆盖用户设置。三层各有用途：模板订阅列表里 `TrafficInfo` 为空而各 provider 有 header → 用 live；常规单源订阅 providers 为空 → 用列表；File 型两边都没有 → UI 显示 "--"。

[HomeViewModel](../../../../android/app/src/main/kotlin/com/stelliberty/android/viewmodel/HomeViewModel.kt) 是唯一的 runtime producer：

- `refreshProviderTraffic` 取 GET 快照；`updateAllProviders` 逐个 provider PUT 后再 GET（mihomo 的 `subscriptionInfo` 只在 provider 更新时刷新）。
- `aggregateProviderInfo` 对所有 `Total > 0` 的 provider 求和，Expire 取最近的非零值，经 `onLiveProviderInfo` 推回 Repository。`subscriptionInfo` 按 provider 各自解析 header，多源 yaml 必须聚合（`values.firstOrNull()` 取到的是 Map 迭代顺序上的任意一个）。
- 请求开始前取消上一次，并捕获 repository identity、active UUID 与递增的 request ID，每次写 UI 前重验三者；disconnect 或 UUID 改变时 cancel + 清空 + 作废旧 ID；失败只更新错误态，已确认的 live snapshot 保持原样。

**Active 订阅名缓存**：通知栏启动时一次性读取 storage 的 `ACTIVE_PROFILE_NAME` 快照。`commitPending` 末尾调用 `syncActiveNameIfActive(uuid, name)`，编辑 active 订阅后通知栏标题随之更新；辅助函数内部对非 active 与同名情况短路（周期性流量更新时通知动画保持连贯）。

## 后台更新

**ProfileWorker 按 startId 收尾**：每件任务完成时调用 `stopSelfResult(自己的 startId)`；已有更新的 start 投递时它返回 false，由那条请求自己的任务接手。完成与新请求之间因此没有空窗（`onDestroy` 的 `scope.cancel()` 会取消所有尚未 join 的请求）。计数用 `AtomicInteger`（onStartCommand 在主线程，完成回调在 IO 协程）。

**自动更新闹钟是订阅列表的派生态**：唯一调度点 [ProfileUpdateScheduler](../../../../android/app/src/main/kotlin/com/stelliberty/android/service/ProfileUpdateScheduler.kt)，`StellibertyApplication.onCreate` 启动 collector，与 `SubscriptionStore.importedFlow` 对账（只为 `AutoUpdateMode = Interval` 且间隔不低于 15 分钟的非本地订阅布置，起点取 `LastUpdatedAt`（缺失时取 `CreatedAt`）与 `LastErrorAt` 中较晚者）：新增订阅、改间隔立即生效，删除时自动撤销闹钟。开机时 app 进程可能只为接收广播而启动，`ProfileReceiver` → ProfileWorker → `reconcileNow()` 借前台服务的存活窗口对账一次。进程重启后 `armed` 为空，列表里已不存在的孤儿闹钟无法枚举；它至多空跑一次 ProfileWorker（uuid 查不到即返回）且不再续期，因此无需额外持久化。

**失败记录**：`ProfileProcessor` 的更新路径失败（取消除外）时在订阅锁内写入 `LastError` / `LastErrorAt`，成功获取时清掉，订阅卡片据此显示原因。起点必须计入 `LastErrorAt`：失败会改动列表并触发对账，只看 `LastUpdatedAt` 时闹钟会立刻再响，陷入失败循环。

**启动时更新按进程计**：`AutoUpdateMode = Startup` 的非本地订阅由 `MainActivity.onCreate` 调 `SubscriptionViewModel.runStartupUpdates()` 更新。ViewModel 是进程级单例，每个进程只跑一次，Activity 重建、切回前台都不重复；开机广播、磁贴等不建 Activity 的入口不触发，进程随后首次打开界面时再执行。它与全部更新共用逐条执行、整轮结束重启一次的流程。
