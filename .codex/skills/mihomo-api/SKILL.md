---
name: mihomo-api
description: mihomo 的 REST / WebSocket 客户端所有权、重连语义与接口可用性边界。改 data/api/ 下的 MihomoApiClient / MihomoWebSocket / MihomoConnectionManager / RuleLatencyTester，或在 ViewModel 里接 repository 前必须先读。触发词包括 mihomo 接口, REST, WebSocket, WS, 重连, Ktor, HttpClient, connectionManager, setRepository, 流量统计, 连接列表, 测速, 延迟, proxies, providers, configs, 代理组接口.
user_invocable: true
---

## 核心约束

1. 消费方统一从 `connectionManager.repository` 订阅 client：`MihomoConnectionManager` 是唯一负责 `close()` 的一方，切订阅时旧实例随之关闭。
2. `setRepository` 先 cancel 旧拉取协程：`client.close()` 只让 in-flight 请求抛异常，协程本身会跑完并写入旧订阅的数据。
3. embed mode 下配置类接口（`PATCH/PUT /configs`、`POST /restart`、`PUT/PATCH /rules` 等）返回 404；配置修改走 `OverrideJsonStore.update{}` + 重启。
4. 长生命周期流的错误处理留在流内重连循环：取消以外的异常都在流内转成重连，外挂的 `.catch` 执行不到。

## 指引索引

| 任务 | 指引 |
|---|---|
| client 归属、切换时机、WS 无限重连与心跳、connectionState 计数 | `guides/client.md` |
| embed mode 下的 404 接口、provider 节点命名空间、测速路径、速率差分 | `guides/endpoints.md` |

配置修改见 `proxy-service` skill；订阅导入走 JNI，见 `subscription` skill。

## 维护

新增接口前确认它在 embed mode 下可用：CMFA embed 关闭了一整类配置接口，调用方只会看到 404。

新增长生命周期流时，消费方在 cancel 之外再校验 `repository === repo`：`flowOn` 的缓冲会让旧实例的帧在切换后到达。
