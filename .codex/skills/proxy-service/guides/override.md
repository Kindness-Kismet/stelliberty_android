# Override 注入

用户设置走 `--override-json` CLI flag + JSON 文件，Kotlin 侧只产出 JSON。用户设置经 `OverrideJsonStore.update{}` 写入 `override.user.json`；启动时 `RuntimeOverrideBuilder` 叠加 TUN fd / AppProxy / rootMode 生成 `override.run.json`。`secret` / `external-controller` 走 `--secret` / `--ext-ctl`。`ConfigurationOverride` 全树 `val`，改动一律 `copy()`：store 直接把实例作为 StateFlow 的值发布，就地修改会被相等去重吞掉。

订阅的覆写、链式代理与规则覆写合成 `profile.transform.json`，经 `--transform` 传入，由 `ProfileTransformWriter.writeRuntime` 在每次启动时按当前订阅重写。Go 在解析订阅前依次应用覆写、链式代理、规则覆写，运行参数最后注入；无需变换时清掉旧文件。修改当前订阅使用的覆写、链式代理或规则覆写经 `restartWhenReady` 生效，详细约束见 subscription 的 `guides/overrides.md`、`guides/chains.md` 与 `guides/rules.md`。

## 默认注入（用户未显式设置时）

- `tcp-concurrent=true`、`find-process-mode=off`（分应用已由 sing-tun / VpnService / iptables uid-owner 处理）。
- ROOT TUN 默认 `tun.mtu=9000 + gso=true + gso-max-size=65535`（大包聚合减少 read syscall），由 `ROOT_TUN_JUMBO_MTU`（默认 true）控制，关闭时回到 1500 / false。VPN 的 MTU 由 `VpnService.Builder` 管理。
- mixed-port 优先级：① 用户 override 显式设置 → 用户值；② 订阅 yaml 自带（`ConfigGenerator.readSubscriptionMixedPort` 行扫描）→ 保留订阅值；③ 存在经内核更新的订阅（`UpdateProxyMode` 非 Direct）或还没有订阅 → 注入 7890；④ 其余情况留空。

## 按模式硬编码覆盖

三模式共用 `profile.store-selected=false` / `store-fake-ip=true`。

- **VPN**：`tun.enable=true` + `file-descriptor` + `dns-hijack=[0.0.0.0:53]` + `auto-route=false`，透传 stack / device。
- **ROOT TUN**：`auto-route=true` + `auto-detect-interface=true` + `iproute2-table-index=2022` + `iproute2-rule-index=9000` + dns-hijack + `include/exclude-package` + **`route-exclude-address`**（私网 + 组播 + 保留段，复用 `IptablesIntranet.V4`，开启 IPv6 时叠加 `.V6`）。sing-tun `auto_route` 在该项为空时铺满 `0.0.0.0/0`，把 LAN 单播与 224/4 组播一起导进 TUN，同网段设备发现与 P2P 直连（妙享投屏）随之失效；VPN 靠 `bypass_private_route`、ROOT TPROXY 靠 iptables RETURN 处理同一问题。
- **ROOT TPROXY**：`tun.enable=false` + `tproxy-port=7895` + `dns.listen=0.0.0.0:1053`；分应用走 iptables uid-owner，`routing-mark` 与 `include/exclude-package` 留空（`routing-mark` 与 Netd 冲突）。

## secret

优先级：用户设置 > 订阅 `config.yaml` 顶层 `secret:`（`readSubscriptionSecret` 行扫描）> 随机 UUID 前 16 字节；ROOT attach 分支使用 storage 持久化的 `existingSecret`。实现单点是 `ConfigGenerator.resolveSecret`，两个 Service 共用。
