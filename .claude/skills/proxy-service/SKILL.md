---
name: proxy-service
description: 代理服务的启动停止、隧道三模式（VPN / ROOT TUN / ROOT TPROXY）、override 注入与 ROOT 约束。改 service/ 与 platform/ 下的 Service、ProxyServiceController、RuntimeOverrideBuilder、ConfigGenerator、iptables 相关代码前必须先读。触发词包括 启动代理, 停止代理, 重启代理, 改服务, 前台服务, 隧道模式, VPN 模式, ROOT 模式, TPROXY, TUN, iptables, su, 分应用代理, override, 配置注入, mixed-port, 开机自启, 自动连接, Wi-Fi 策略, VpnService, 状态机, 崩溃检测.
user_invocable: true
---

## 核心约束

1. 所有「启动代理」路径经 `ProxyServiceController.start / restart`：active 订阅与 config.yaml 的校验在这里完成，两个 Service 因此只接受 controller 构造的 Intent。
2. Service 启动幂等串行：`ACTION_START` 会在数百毫秒内到达两次（自动连接 + BootReceiver 补发），两条协程并发跑 iptables 会互抢锁，ROOT TPROXY 下启动卡死。
3. 停止态一律走 `markStopped(tunMode)` / `markStoppedUnlessError(tunMode)`，终态带着正确的模式；「用户当前选的模式」走 `setSelectedTunMode`。
4. 配置变更通过重启整进程生效，决策单点是 `restartWhenReady`：它读 `ProxyServiceBridge`（Starting 窗口内 UI 的 `isRunning` 仍为 false）。

## 指引索引

只读当前任务需要的 guide。

| 任务 | 指引 |
|---|---|
| 启动 / 停止 / 重启、状态回报、前台服务、自动连接、日志尾读 | `guides/lifecycle.md` |
| 三种隧道模式的机制、分应用代理、Wi-Fi 策略、VPN 专属约束 | `guides/tunnel-modes.md` |
| `override.user.json` / `override.run.json`、默认注入、secret | `guides/override.md` |
| iptables 规则集与锁、`runtime/` 沙箱、`su` 转义、attach 三重校验 | `guides/root-mode.md` |

订阅侧（导入管线、更新、锁）见 `subscription` skill；mihomo HTTP / WS 客户端见 `mihomo-api` skill；JNI 与 .so 构建见 `native-build` skill；其余见仓库根的 [AGENTS.md](../../../AGENTS.md)。

## 维护

新增启动入口（Wear / shortcut / 自动化）一律经 controller。新增 CLI flag 同步注册到 `stelliberty_core/runtime.go` 的 flag 集合。
