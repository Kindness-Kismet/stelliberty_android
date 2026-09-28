# 链式代理

## 数据

- 订阅字段 `BuiltinChainProxyNames`、`DisabledBuiltinChainProxyNames`、`CustomChainProxies` 与 PC 同名同格式；跳点 `Kind` 写序号（0 节点 / 1 代理组），自定义链 Id 为 32 位十六进制。
- `BuiltinChainProxyNames` 在每次获取成功后按原订阅里带 `dialer-proxy` 的节点重写，供编辑页计数；链式代理页的内置列表以套完覆写后的上下文为准。
- 保存时同步更新已导入订阅和草稿，与覆写选择一致。

## 页面与保存

- 上下文经 `StellibertyCoreBridge.chainProxyContext` 读取：只套覆写、不套链，候选项为没有 `dialer-proxy` 的节点加全部代理组。读取只用独立锁，不占 processLock。
- 列表页与编辑页共用 `ChainProxyViewModel` 里的草稿，路由的 session 区分每次进入列表页；点保存才写入订阅，直接返回即放弃。
- 保存在 processLock 内先用完整变换校验再写入，校验失败保留原值。检测到 `dialer-proxy` 循环只提示、不阻止保存。当前订阅保存后经 `restartWhenReady` 生效。

## 运行时规则（Go `overrides/chains.go`）

- 顺序：原订阅 → 覆写 → 链式代理 → 规则覆写 → 运行参数。
- 禁用的内置链从 `proxies` 与各组成员中移除；组被清空且成员不来自 `use` / `include-all*` 时补 `empty-fallback`（默认 `COMPATIBLE`）。
- 上游不可达的内置链按传递关系一并移除，只在本次运行生效、不写回订阅；PC 会把它们写入禁用列表。
- 自定义链至少两跳，代理组只能做首跳，显示名不能被占用，所属组必须存在；不满足的链在运行时跳过。中间跳命名为 `__stelliberty_chain_{id}_{序号}`，冲突时加后缀，末跳用显示名并追加到所属组。
