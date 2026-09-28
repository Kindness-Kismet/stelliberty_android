# ROOT 模式约束

适用于 ROOT 路径：`StellibertyRootService` / `RootHelper` / `RootTetherHijacker` / `RootTproxyApplier` / `MihomoRunner` 的 root 分支。改这些文件、写 `su -c` 命令、动 iptables 或 ip rule 之前先读本文件；三模式共有的启动链与状态回报见 [lifecycle.md](lifecycle.md)，override 注入见 [override.md](override.md)。

## iptables 锁

所有 iptables / ip6tables 调用带 **`-w <秒>`**（裸 `-w` 是无限等待，撞锁会让启动链永久挂起）。秒数已并进工具令牌（`IPT4` / `IPT6` / `IPT_BINS`），新增命令一律用令牌拼接；`runWithOutput` 自动注入，只识别带数字的 `-w`，超时放宽到 `IPT_WAIT_SECONDS + 5`，等锁的时间才不会被算成 su 错误。

抢不到 xtables.lock 的退出码是 **4**（resource problem），与「内核没有该 target」相同。`probeTproxySupport()` 因此按「等锁 → 撞锁退避重试 → 重试耗尽仍是锁问题时**返回 true**」处理，交给真正的 apply 验证：误判「支持」只会让 apply 失败并留下日志，误判「不支持」会中止 ROOT TPROXY 启动。

## IPv6 门控

`RootTproxyApplier.apply(ipv6Enabled)` 由 `VPN_ALLOW_IPV6` 决定（与 VPN / ROOT TUN 同一开关）。默认 false 时跳过全部 ip6tables / `ip -6` 注入，IPv6 走内核原生路由；teardown 始终清理 v4 + v6，切换后干净无残留。原因：mihomo 默认 `ipv6: false`，TPROXY 拦下 IPv6 连接后拨号报 "ip version error"，App 随即重试，形成每秒数百连接的失败循环。VPN / ROOT TUN 由 `inet6-address` 控制是否注册 v6 默认路由，自带这层过滤。

## runtime/ 沙箱

ROOT mihomo 的工作目录是独立的 `runtime/{uuid}/`（从 imported/ 复制），imported/ 始终属于 app UID。启停钩子：`startProxy` 全新启动前 `prepareRootRuntime`；stop / restart / 进程监控三条死亡路径在 `clearPersistedState` 之前 `cleanupRootRuntime`；attach 分支沿用现有目录。

## su 转义

进入 `su -c` 的外部值一律经 `escapeShellSingleQuoted`（双引号挡不住 `$(...)`）。`--secret` 来自远端 config.yaml 行扫描，`--age-secret-key` 由用户手填，device name 只做 trim，转义是唯一防线。启动日志只打印 flag 名，密钥留在 logcat 之外。

## 孤儿进程清理

`RootHelper.cleanupOrphanedMihomo(tunDevice)` 在单次 su shell 里完成 pkill + `ip link delete <tunDevice>`（避免 sing-tun EEXIST）。VPN 启动在 `hadRootPid || HAS_ROOT` 时触发，清除 ROOT 持久化 key，并兜底执行 `cleanupAllRootRuntime`。

## attach 重连

`attachToExisting` 三重校验：`kill -0` 存活 + `/proc/$pid/cmdline` 含 libmihomo.so + stored secret 经 `/configs` Bearer 鉴权返回 2xx。订阅一致性由 `startProxy` 在 attach 前比对持久化的与请求的 subscriptionId，不一致时 cleanup + 全新启动。

app 重新打开（`onResume` → `verifyAndSyncState`）时，ROOT 模式走 `reattachRoot()`，发送带 `EXTRA_ATTACH_ONLY=true` 的 START intent；`startProxy(attachOnly=true)` 在 attach 失败时保持停止（清状态、置 Stopped、`stopSelf`），重启后自动拉起代理只由开机自启负责。`SERVICE_WAS_RUNNING` / `ROOT_MIHOMO_PID` 存在 SharedPreferences 里、跨重启保留，而重启会杀死 root mihomo，PID 随之过期。两层防护：

1. `verifyAndSyncState` 经 [BootSession](../../../../android/app/src/main/kotlin/com/stelliberty/android/platform/BootSession.kt) 预门控：重启过即进程已死，清除标志并保持停止；
2. attach-only 兜住预门控漏判的边界。

**判据是 `Settings.Global.BOOT_COUNT`**（公开 API、免权限、每次开机递增）：精确、没有时间窗口、与时钟调整无关。基于 `elapsedRealtime` 的比较存在漏判窗口，`currentTimeMillis - elapsedRealtime` 推算的开机时刻会随 NTP 校时跳变。宁可漏判：漏判只多一次 attach 尝试，由三重校验挡下过期 PID；误判会清掉有效 PID，让活着的 mihomo 成为孤儿。取不到 BOOT_COUNT 时按「未重启」处理。`ROOT_BOOT_COUNT` 是运行时态，登记在 `BackupManager.EXCLUDED_PREF_KEYS` 里。

开机自启例外：BootReceiver 走 `start()`（全新启动），与 `reattachRoot()` 撞上时按 [lifecycle.md](lifecycle.md) 的「fresh START 抢占进行中的 attach-only」处理（fresh 优先）。「attach 失败保持停止」只约束 attach-only 请求自身。

## 热点处置

sing-tun `auto_route` 的 catch-all ip rule（priority 9002）不区分本机与转发流量，热点客户端的包（iif=wlan2 / ap0）也会被导进 TUN，而 mihomo 处理非本机源 IP 不稳定。`RootTetherHijacker` 在 sing-tun 之前插入两种处置：

- **BYPASS（默认）**：`ip rule priority 8000/8002` 去程与回程均 action=`goto 9010`（sing-tun 的 nop marker）。去程越过 catch-all 后命中 Android 原生 iif forward rule，走 wlan0 / rmnet；回程命中 local_network / main 里的连接路由。
- **PROXY**：内核态 TPROXY，绕开 sing-tun 的用户态 TCP 栈。`mangle PREROUTING -i <tether> -j stelliberty_tether` 把 TCP + UDP 导向 mihomo 的 `--on-port 7895 --tproxy-mark 0x01000000/0x01000000`（IP_TRANSPARENT socket）；`ip rule fwmark ... lookup 2024 priority 7999` + `ip route add local default dev lo table 2024` 让带 mark 的包在 PREROUTING 被判定为本机投递。**这条 local default 是关键**，带 mark 的包靠它找到接收 socket。常量（bit 24 / table 2024 / priority 7999）与 box_for_magisk 一类方案一致，避开 Netd 使用的低 16 位 mark。chain 顺序：① `--ctstate INVALID -j DROP`；② `IptablesIntranet` v4 / v6 CIDR `-j RETURN`（DNS 除外，交给 mihomo 处理 fake-ip）；③ `-m socket -j stelliberty_tether_divert`（已建立的流只打 fwmark + ACCEPT）；④ 新连接 `-j TPROXY`。
- **PROXY 降级**：`xt_TPROXY` 不可用时改用去程 + 回程对称的 `lookup 2022`（双向进 sing-tun，性能次于 TPROXY）。`probeTproxySupport()` 在运行时探测，结果驱动 `buildAndWriteForRun(tproxyForTether = ...)` 决定是否写 `tproxy-port`；`ROOT_TPROXY_KERNEL_CAPABLE` 保存探测结果，RootSettingsScreen 据此显示降级告警卡片。
- **attach 路径**：tproxy-port 是否监听在 mihomo 启动时就已确定，app 被杀期间若用户改了 tether mode，规则会与实际错位。`ROOT_TETHER_MODE_ACTIVE` 保存启动成功时的快照，attach 前比对，不一致时走 fresh restart。attach 后按需 re-apply：先用 `anyRulesPresent()` 探测锚点（依次按 xt_comment 前缀 `stelliberty:tether:` / `stelliberty:tproxy:`、chain 名、priority），存在则跳过，缺失才 re-apply，覆盖系统重启或与 box_for_magisk 共存时规则被清的情况。`ROOT_ATTACH_FORCE_REAPPLY=true` 强制重建（诊断开关）。
- **接口识别（手填）**：用户在 RootSettingsScreen 填 CSV（`ROOT_TETHER_IFACES`，默认 `wlan1,wlan2`），编辑对话框的「检测当前接口」列出候选（`NetworkInterface` 中 UP、有 site-local IPv4、不在蜂窝 / 隧道黑名单的接口，默认排除 `wlan0`）。采用手填：`TETHER_STATE_CHANGED` 的 extras 在 Android 11+ 属于 @hide，正则白名单也覆盖不全 OEM 命名。
- **回程规则按 InterfaceAddress 计算 CIDR**：Android main 表没有 default route，回程规则由 `NetworkInterface.getByName` 读 InterfaceAddress + prefix 算出网段。接口未就绪（热点后开）时 WARN 并跳过，重启代理后重新 apply。`ip rule add iif <name>` 可按名预注册、接口出现即生效；`ip rule add to <subnet>` 需要 apply 时已有地址。生命周期：startProxy（含 attach）后 apply；stop / restart / 死亡三条路径 teardown（NonCancellable），两类规则都执行，任意前置状态都能清干净，末尾 `verifyClean()` 扫描锚点，有残留再重试一轮。本机流量的全 TPROXY 牵涉 AppProxy、DNS、fake-ip、IPv6 DNS 防泄漏整条链，属于独立重构，本模式只覆盖热点。
- **root 批量操作走单次 su**：Magisk 下每次 `su` 约 30~100ms。`teardown`（ip rule 删到失败为止 + `-S` 转 `-D`）、`verifyClean`、`dumpState`、apply 的约 60 条命令都合并成一次调用；有数据依赖的两步操作也在 shell 里完成（`iptables -S PREROUTING | grep | sed 's/^-A/-D/' | while read`）。多条查询用 `echo '--- <key>'` 分段，在 Kotlin 侧切回。`MihomoRunner.waitForReady` 的 ROOT 判活间隔固定 2s，与就绪文件的 100ms 轮询分开（判活要 fork su）。

## 通知

ROOT 模式使用静态通知：`DynamicNotificationManager.startOrFallbackStatic` 在 `tunMode != Vpn` 时走静态分支，忽略 `DYNAMIC_NOTIFICATION` 偏好；设置开关在 ROOT 模式下 `enabled=false` 并附说明。VPN 靠 `BIND_VPN_SERVICE` 让进程处于 `BOUND_FOREGROUND_SERVICE`，CPU 保持活跃；ROOT 没有系统 binding，Activity 进后台后设备进入 idle，1Hz 的 `/traffic` WS 帧被合并、`notify()` 被批处理，动态通知随之冻结。`PARTIAL_WAKE_LOCK` 会被系统置为 DISABLED，且只阻止 SoC suspend、挡不住 CPU idle；唯一根治是引导用户开启 `IGNORE_BATTERY_OPTIMIZATIONS`，这里按平台限制处理。ROOT TUN 下 mihomo 持续 `read(tun_fd)` 让设备保持活跃，属于偶然现象。
