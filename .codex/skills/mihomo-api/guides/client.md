# 客户端所有权与生命周期

## 谁持有 client

`MihomoConnectionManager`（`dataModule` single）订阅 `ProxyServiceBridge.state`：Running 时新建 `MihomoRepositoryImpl`，其他状态置 null，切换前同步 close 旧实例。消费方一律经 `connectionManager.repository: StateFlow<MihomoRepository?>`：HomeViewModel 与 `DynamicNotificationManager` 自行 collect，MainActivity collect 后经 `setRepository` 转发给 Proxy / Provider / Connection / DnsQuery 四个 VM。

manager 是唯一持有 `close()` 责任的一方：按 bridge state 自动 connect / disconnect，原子地 close 旧实例并新建。按服务身份替换，比较时忽略 `configurationRevision` 与 `isReloading`；同一内核重载保留 client 和 WS，页面按修订号刷新配置、节点与 provider。ViewModel 的 `setRepository` 只传信号。

首页流量和内存 WS 只在 Activity 可见时收集，后台取消订阅，回前台重新连接；接收帧时核对 repository 身份。通知的流量 WS 独立于页面，仅在动态通知启用、应用和渠道允许通知、本次会话的通知未被划掉时收集。

例外：`SubscriptionProxyResolver` / `RuleLatencyTester` 属于探测场景，可自建短生命周期 client，用 `use{}` 或 try/finally 关闭。订阅 fetch 走 JNI。

## 切换时取消旧协程

mihomo 重启或切订阅时，manager close 旧 client 并 emit 新 repo。消费方先 `loadJob?.cancel()` 再切字段，协程内再用 `if (repository !== repo) return@launch` 兜底：`client.close()` 只让 in-flight 请求抛异常，协程继续执行，旧响应的 onSuccess 会把 `_uiState` 写成旧订阅数据。WS 流无限重连且吞掉非取消异常，`close()` 后只会进入退避循环，cancel 是唯一的终止手段。四个 VM 均按此模式处理，一次性请求同样适用（DNS 查询的 `isQuerying` 也靠它复位）。

`ProxyViewModel` 的 session 绑定 repository、订阅 id 与子作用域；切换时取消整个子作用域，回填和保存前复核 session 与活跃订阅。选择、解除固定、刷新与恢复共用一把锁，内核写入和界面发布保持同序；恢复完成后才置完成标记，中断后下次刷新接续。测速请求独立运行，回填仍经过刷新锁。

本地核心的 REST 和 WebSocket 客户端显式禁用系统代理，避免 PAC / Wi-Fi 代理把回环请求转发到远端。`SubscriptionProxyResolver` 查询实际运行端口，探测限时 3 秒并保留协程取消。

## WebSocket 重连

Ktor 的 `for (frame in incoming)` 在 graceful close 时静默退出，`MihomoWebSocket.webSocketFlow` 因此自行实现无限重连 + 指数退避（1s→30s）。内核推送接口只写帧、不处理客户端的 ping / close；OkHttp WebSocket factory 在关闭时直接 cancel 底层连接，不发送需要服务端响应的心跳，避免取消后继续收帧等待超时。

- `CancellationException` 排在通用 catch 之前并 rethrow，重连循环才能停下。
- 末尾使用 `flowOn(Dispatchers.IO)`：Ktor 的 nonce 初始化会阻塞等待后台协程，并发握手放在 Default 会耗尽 CPU worker；解码仍在后台，缓冲要求消费方额外校验 `repository !== repo`。
- `emit` 放在解析的 try 之外：两者包在一起时，下游异常会被当成坏帧吞掉，随后撞上 flow 异常透明性检查，表现为一次虚假的断线重连。
- 消费侧的错误处理留在流内：取消以外的异常都转成重连。
- `connectionState` 由四条流共享，语义是「任意一条连着」，按引用计数发布：计数与发布一起放在 `@Synchronized` 里；握手失败的那次未计数，`finally` 按实际计数递减。
- 日志通道用 `Flow<LogEvent>` 同时发布握手成功、断线与日志；页面连接状态只消费该通道的事件，避免被流量等通道的连接状态干扰。
- `MihomoConnectionManager` 持有 `CoreLogCollector`，运行期间先经 `GET /configs` 读出实际生效的 `log-level` 再订阅 `/logs`：内核推送日志不看 log-level，只按订阅参数过滤；切换 repository 时先取消旧任务，接收帧时在同一把锁内复核实例并写入日志模块。
- 日志页通过 `LifecycleStartEffect` 控制每 120ms 一次的快照发布；采集与页面生命周期分离，停止代理保留历史。应用与核心日志分别保存，类型切换和等级筛选只影响查看，导出所选类型的整份日志文件，清空只作用于所选类型。

## Flow.catch 是终结操作

`.catch` 捕获后流即结束，不会重新订阅。长生命周期 UI / 通知 Flow 的瞬态异常（如 `notify()` 偶发的 `RemoteServiceException`）在 `collect` 内用 `runCatching` 处理；`.catch` 只用于确实要终结的失败。
