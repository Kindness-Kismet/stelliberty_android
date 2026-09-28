# 客户端所有权与生命周期

## 谁持有 client

`MihomoConnectionManager`（`dataModule` single）订阅 `ProxyServiceBridge.state`：Running 时新建 `MihomoRepositoryImpl`，其他状态置 null，切换前同步 close 旧实例。消费方一律经 `connectionManager.repository: StateFlow<MihomoRepository?>`：HomeViewModel 与 `DynamicNotificationManager` 自行 collect，MainActivity collect 后经 `setRepository` 转发给 Proxy / Log / Provider / Connection / DnsQuery 五个 VM。

manager 是唯一持有 `close()` 责任的一方：按 bridge state 自动 connect / disconnect，原子地 close 旧实例并新建。它只看状态、不比对 endpoint，attach 重连多一次重建（< 50ms），换来无竞态。ViewModel 的 `setRepository` 只传信号。

例外：`SubscriptionProxyResolver` / `RuleLatencyTester` 属于探测场景，可自建短生命周期 client，用 `use{}` 或 try/finally 关闭。订阅 fetch 走 JNI。

## 切换时取消旧协程

mihomo 重启或切订阅时，manager close 旧 client 并 emit 新 repo。消费方先 `loadJob?.cancel()` 再切字段，协程内再用 `if (repository !== repo) return@launch` 兜底：`client.close()` 只让 in-flight 请求抛异常，协程继续执行，旧响应的 onSuccess 会把 `_uiState` 写成旧订阅数据。WS 流无限重连且吞掉非取消异常，`close()` 后只会进入退避循环，cancel 是唯一的终止手段。五个 VM 均按此模式处理，一次性请求同样适用（DNS 查询的 `isQuerying` 也靠它复位）。

## WebSocket 重连

Ktor 的 `for (frame in incoming)` 在 graceful close 时静默退出，`MihomoWebSocket.webSocketFlow` 因此自行实现无限重连 + 指数退避（1s→30s）+ 20s 心跳：

- `CancellationException` 排在通用 catch 之前并 rethrow，重连循环才能停下。
- 末尾保留 `flowOn(Dispatchers.Default)`：消费点都在 Main，反序列化移出主线程。它引入的缓冲要求消费方额外校验 `repository !== repo`。
- `emit` 放在解析的 try 之外：两者包在一起时，下游异常会被当成坏帧吞掉，随后撞上 flow 异常透明性检查，表现为一次虚假的断线重连。
- 消费侧的错误处理留在流内：取消以外的异常都转成重连。
- `connectionState` 由四条流共享，语义是「任意一条连着」，按引用计数发布：计数与发布一起放在 `@Synchronized` 里；握手失败的那次未计数，`finally` 按实际计数递减。它反映整体状态，单条流的存活另行判断。

## Flow.catch 是终结操作

`.catch` 捕获后流即结束，不会重新订阅。长生命周期 UI / 通知 Flow 的瞬态异常（如 `notify()` 偶发的 `RemoteServiceException`）在 `collect` 内用 `runCatching` 处理；`.catch` 只用于确实要终结的失败。
