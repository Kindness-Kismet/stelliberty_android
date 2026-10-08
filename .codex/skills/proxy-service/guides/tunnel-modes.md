# 隧道三模式

`TunMode { Vpn, RootTun, RootTproxy }`：

- **VPN**：VpnService 创建 TUN fd，mihomo 写 `tun.file-descriptor` + `auto-route=false`，工作目录 `imported/{uuid}/`（app UID）。
- **ROOT TUN**：mihomo 以 root 自建 TUN，`auto-route=true` + `auto-detect-interface=true`，工作目录为独立的 `runtime/{uuid}/`（启动前从 imported/ 复制，停止时删除 provider 缓存以外的内容）；imported/ 始终属于 app UID。
- **ROOT TPROXY**：`tun.enable=false`，`tproxy-port=7895` 入站 + `dns.listen=0.0.0.0:1053`；`RootTproxyApplier` 装 mangle / nat 规则与 fwmark 策略路由，劫持本机与热点流量，常量与 chain 结构见 [root-mode.md](root-mode.md)。
- **分应用代理**：VPN 用 VpnService API；ROOT TUN 用 mihomo `include/exclude-package`（sing-tun 翻译为 uidrange）；ROOT TPROXY 用 iptables `-m owner --uid-owner`（`AppListProvider.resolveUids` 解析包名）。自绕统一按 UID / 包名排除：Netd 用 fwmark 低 16 位编码 netId，自定义 SO_MARK（`routing-mark`）会让路由命中没有默认路由的 legacy_system 表，出站全部 `network unreachable`。
- ROOT 两个子模式共享 StellibertyRootService，Intent 经 `EXTRA_SUBMODE = "tun"/"tproxy"` 区分；attach 前比对 `ROOT_SUBMODE_ACTIVE` 与请求的 submode，不一致时 fresh restart。ROOT 进程在 app 被杀后继续存活，重开 app 靠持久化的 PID / secret 做 **attach-only** 重连。ROOT 不可用时自动回退 VPN。

## Wi-Fi 自动切换

`WifiPolicyMonitorService` 前台监控当前 Wi-Fi 的 SSID（精确匹配，去掉 Android 外层引号，忽略 `<unknown ssid>`），权限齐全时才触发。两种动作：

- **停止服务**：进入匹配 Wi-Fi 且代理运行中时，记 `WIFI_POLICY_PENDING_RESTART` 后停止；离开时仅在 pending 存在时自动启动一次。
- **Direct 模式**：进入时写 `WIFI_POLICY_RUNTIME_MODE=direct`，离开时清除并回到用户的持久 mode，统一经 `ProxyServiceController.restart()` 生效，三模式行为一致。runtime mode 由 `RuntimeOverrideBuilder` 以高于 `override.user.json` 的优先级注入，持久配置保持原样。

Starting 窗口内的切换先排队，Running 后补一次 restart；关闭功能时恢复被策略改动的状态。监控通知与切换通知使用独立 channel。开机或包替换后由 `WifiPolicyBootReceiver`（默认 disabled，随开关动态启用）恢复监控。

## 自身流量绕过隧道

Stelliberty 自身包名始终绕过 TUN / VPN：`ProcessBuilder` 子进程的 HTTP 被代理捕获会永久阻塞。ROOT 三种 AppProxyMode 都把 `packageName` 从 include 剔除或加入 exclude；VPN `AllowSelected` 分支先过滤 self 再 addAllowed，过滤后为空时改用 `addDisallowedApplication(self)`。

## VPN 专属约束

**子进程判活用 waitpid**：mihomo 是 app 经 `process_helper.c` 直接 fork 的子进程，退出后成为僵尸，而僵尸的 `/proc/<pid>` 依然存在，以 `/proc` 判活会让崩溃检测永不触发（UI 停在 Running）。libcore 的 `ProcessManager` 收割线程偶尔会 `waitpid(-1)` 带走僵尸，让这个问题时有时无。`nativeIsAlive` 用 `waitpid(pid, WNOHANG)` 判活兼收割：返回 0 = 存活，返回 pid = 刚收割，返回 -1（ECHILD）= 已被别处收割。配套：

- `waitpid` 包 EINTR 重试（被打断时返回 -1 且 `status` 为 0，`WIFEXITED(0)` 会误报正常退出）；
- 等待带超时（调用点在 `onDestroy` 主线程，mihomo 收到 SIGTERM 后要关 TUN、断开全部连接）；
- SIGTERM 等待超时后升级 SIGKILL。

ROOT 用 `kill -0`（跨进程），每次都要 fork su（Android 10+ 的 `/proc` 是 hidepid），轮询处自行控频。

**TUN 初始化失败的兜底**：mihomo `ReCreateTun` 失败只打日志。① `StellibertyTunService` 清除 O_CLOEXEC 失败视为致命（`closeTunFd` + Error + `stopSelf`）；② `MihomoRunner.waitForReady` 在就绪文件写入且 API 可用后扫描启动日志，匹配 `Start TUN listening error` / `configure tun interface` / `create NetworkUpdateMonitor`。

**MTU 同步**：`VpnService.Builder.setMtu` 与 mihomo `cfg.Tun.MTU` 同值，两侧共用 `RuntimeOverrideBuilder.VPN_TUN_MTU` 常量。sing-tun 在 fd 模式下用 `cfg.Tun.MTU` 设置 gvisor `fdbased.New` 的缓冲，为 0 时所有 read 失败，表现为延迟正常而流量不通。
