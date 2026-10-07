# 启动、停止与状态

## 启动校验单点

所有「启动代理」路径经 [ProxyServiceController.start / restart](../../../../android/app/src/main/kotlin/com/stelliberty/android/platform/ProxyServiceController.kt)，两个 Service 因此只暴露 action 常量、没有 `start` / `stop` 静态入口。`resolveStartSubscriptionId()` 校验 active 订阅与 `imported/{uuid}/config.yaml` 已写入，失败时一次完成 toast + `updateState(Error)` + 清除 `SERVICE_WAS_RUNNING` + Running 时发 STOP。

- Intent 用类对象与 Service 自己的 action / extra 常量构造，重命名时编译器能一并检查（字面量写错会让 attach-only 静默变成全新启动）。
- 新增入口（Wear / shortcut / 自动化）一律经 controller；Service 内的 `ProfileFileOps.hasValidConfig` 兜住 ADB 与第三方 Intent。
- Tile / 通知等没有 Activity 上下文的入口在 VPN 模式下经 [VpnPermissionActivity](../../../../android/app/src/main/kotlin/com/stelliberty/android/service/VpnPermissionActivity.kt) 弹授权（`VpnService.prepare()` 需要 Activity context）。

## 幂等串行

`StellibertyRootService` / `StellibertyTunService` 用 `startJob`（`@Volatile`，主线程与 IO 协程共用）守门：已有启动在进行时，新的 `ACTION_START` 直接忽略。`stopProxy` / `restartProxy` / `onRevoke` 先 `startJob?.cancelAndJoin()` 再继续，才能越过幂等检查。

`ACTION_START` 会在数百毫秒内到达两次：「打开应用自动连接」发一次，[BootReceiver](../../../../android/app/src/main/kotlin/com/stelliberty/android/service/BootReceiver.kt) 收到补发的 BOOT_COMPLETED 再发一次（HyperOS 等 ROM 等进程起来后才补投广播；BootReceiver 另建 controller，与 `launchAutoConnectConsumed` 各自独立）。两条启动协程并发跑 iptables 会互抢 `/system/etc/xtables.lock`，ROOT TPROXY 下启动卡死。BootReceiver 只在 `state == Running` 时跳过（Starting 仍发送，下述抢占才能生效），去重统一由 Service 负责。

**唯一例外：fresh START 抢占进行中的 attach-only**（cancel 后在新协程里 `join()` 等它收敛）。attach-only 失败时保持停止，fresh 请求表达「必须跑起来」；`AUTO_CONNECT_ON_LAUNCH` 关、开机自启开时，`reattachRoot` 可能先占住 startJob，抢占保证开机自启生效。attach-only 从入口到 `stopSelf()` 没有 suspend 点，`cancel()` 打断不了它，因此该分支显式检查 `isActive` 并主动让位，接替的协程得以继续。

## 停止态与模式回报

**停止态一律走 `ProxyServiceBridge.markStopped(tunMode)` / `markStoppedUnlessError(tunMode)`**，终态带着刚才运行的模式（storage 存的是「用户当前选择」）。onDestroy 保留 Error：失败路径是 `updateState(Error) + stopSelf()`，随后进入 onDestroy，`markStoppedUnlessError`（CAS）让 errorMessage 保留下来，两个 Service 共用这一实现。`HomeViewModel` 的 Error 分支弹 toast（首页不渲染 errorMessage，Error 与 Stopped 外观一致），同一条只弹一次，回到 Stopped 时清除标记；发布方已经 toast 过的（如 `resolveStartSubscriptionId`，它覆盖 Tile / 通知这类无 HomeViewModel 的入口）置 `errorNotified = true`，UI 据此跳过。

**「用户选的模式」也写入桥**：`markStopped` 覆盖「运行后停止」；冷启动尚未启动过代理、或用户在停止态改模式时，走 **`ProxyServiceBridge.setSelectedTunMode(mode)`**。调用点：`StellibertyApplication.onCreate` 在 startKoin 之后立即填一次（唯一既能拿到 storage 又早于全部读取方的位置）；系统集成页选择器、`settings.set_tun_mode`、MainActivity 的 ROOT 不可用回退各同步一次。它只改 `tunMode` 一个字段（Error 与 errorMessage 保持原样），且只在非运行态生效（运行中的 `tunMode` 表示正在跑的模式）。`HomeViewModel` 的 Stopped / Error 分支整体重建 `HomeUiState` 以清空运行期数据，要保留的字段（含 `tunMode`）逐个显式带上。

## 打开应用时自动连接

`AUTO_CONNECT_ON_LAUNCH`（设置 →「自动化」，默认关）与开机自启是两个独立开关：BootReceiver 只在 `SERVICE_WAS_RUNNING=true` 时恢复，自动连接与上次状态无关。实现挂在 `verifyAndSyncState` 内：两者回答的都是「app 打开时代理该不该跑」，放在一处才能与 ROOT attach 路径串行（`start()` 是异步的，attach intent 发出后 bridge 仍是 Stopped）。进程外的 BootReceiver 重复投递由 Service 的幂等串行兜底。

1. 每进程只消费一次（controller 是 Koin single）：冷启动触发，回前台保持现状。
2. 静默校验走 `startableSubscriptionId()`（无副作用版），没有可用订阅时直接跳过。
3. VPN 缺授权时调 `requestVpnPermission()`，授权回调接续启动。ROOT 分支走 `start()`：同样先三重校验 attach 复用，attach 失败时允许全新启动，这正是该开关的意图。

## startForeground 防御

Tun / Root / ProfileWorker 的 onCreate 均 `try { startForeground() } catch (Exception)`，覆盖 API 31+ 的 `ForegroundServiceStartNotAllowedException` 与 API 34+ 的 FGS type 异常。失败路径：Tun / Root 上报 Error + `stopSelf()`；ProfileWorker 置标记后 `stopSelf()`，之后到达的 start 一律拒收。前台服务审核要求保持前台服务形态。

## 配置变了就重启

决策单点是 [ProxyServiceController.restartWhenReady](../../../../android/app/src/main/kotlin/com/stelliberty/android/platform/ProxyServiceController.kt)，读 ProxyServiceBridge：`uiState.isRunning` 在 Starting 窗口（约 10s）内仍为 false，以它为准会漏掉重启。Starting / Stopping 过渡态挂起等待收敛：落到 Running 补一次重启，落到 Stopped / Error 放弃（尊重用户中途的手动停止）。两个调用方：

- `onActiveSubscriptionChanged()`：切换或删除 active；删光最后一条时改为 stop + `cancelPendingRestart()`。
- `restartAfterProfileUpdate(uuid)`：见 `subscription` skill 的 `guides/lifecycle.md`。

重启是整进程重来：VPN 杀进程并重建 TUN fd；ROOT 另清 `runtime/{uuid}/` 沙箱与持久化 PID，强制全新启动读取新配置。

## 日志尾读

mihomo.log 在 debug 级别可达数十 MB，一律尾读：[readLastLines](../../../../android/app/src/main/kotlin/com/stelliberty/android/service/LogTail.kt) 从尾部回读固定字节窗口，窗口起点未到文件开头时丢弃首行（它可能切在半行或半个 UTF-8 字符上）。ROOT 路径用 `su tail -n`。

## 启动就绪与停止等待

- `MihomoRunner` 启动与 ROOT 重连共用 `MihomoApiProbe`：携带 secret 请求 `/stelliberty/runtime`，只接受 200 与目标 PID，禁止跟随重定向。native 在 TUN 与 provider 初始化结束前返回 503，完成后才返回当前进程号；相同 secret 的其他内核也不能冒充就绪。
- 就绪检查间隔 100ms，单次连接与读取超时 500ms，启动总时限 10s；ROOT 判活间隔 2s（每次都要启动 su）。日志等级与滚动不影响就绪判据。
- ROOT 停止时，发信号、判活与网卡清理在同一次 su 中完成，轮询 100ms，正常退出立即返回；SIGTERM 最多等 3s，SIGKILL 最多等 2s。确认进程退出后再完成服务清理并上报停止。
- ROOT 清理失败时阻止继续启动；停止或重启失败时保留 PID 与持久化状态，报告 Error，供后续重试。
