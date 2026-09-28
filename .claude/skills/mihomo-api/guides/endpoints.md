# 接口语义

## embed mode 下的配置接口

CMFA embed mode 下 `PATCH/PUT /configs`、`POST /restart`、`POST /configs/geo`、`PUT/PATCH /rules`、`POST /upgrade` 均返回 404。配置修改走 `OverrideJsonStore.update{}` + `serviceController.restart()`，UI 用 `RestartRequiredHint` 提示。

## provider 节点的命名空间

`GET /proxies` 与 `findProxyByName` 只覆盖 runtime proxies（`proxies:` 段 + 代理组），proxy-provider 节点在其中查不到。因此：

- 节点详情合并 `/providers/proxies` 里各 provider 的 `proxies`（runtime 优先，`ProxyViewModel` 维护 `nodeProviderMap`）；
- provider 节点单测走 `GET /providers/proxies/{provider}/{node}/healthcheck`；
- 组测速与组选择照常使用。

只有 provider 型（模板）订阅涉及。

## 首页延迟测试走规则

首页延迟测试经本机 mixed-port 发真实 HTTP 请求（[RuleLatencyTester](../../../../android/app/src/main/kotlin/com/stelliberty/android/data/api/RuleLatencyTester.kt)），出口由规则引擎决定，结果反映该域名实际命中的分流。Stelliberty 自身流量始终绕过 TUN，mixed-port 是自身请求经过 mihomo 的唯一入口。`/proxies/{name}/delay` 直接拨测指定组的当前节点、绕过规则引擎，且要求传入 proxy 名，适合代理页的节点 / 组测速。

实现约束：
1. 每次测量新建 client，每次都完整握手，结果可比；
2. `followRedirects = false`，耗时只含首个响应；
3. 解析不到 mixed-port 时退回 `GLOBAL` 组拨测，置 `latencyViaRules = false`，UI 标注「未走规则」。

## 自动测试延迟

[AutoDelayTester](../../../../android/app/src/main/kotlin/com/stelliberty/android/data/api/AutoDelayTester.kt) 在应用启动时开始工作，与界面无关：连接中的 client、当前订阅与其 `AutoTestDelayIntervalMinutes` 任一变化就重新计时，首轮在一个间隔之后，与 PC 的排期一致。

1. 测全部代理组的成员（去重），经 `MihomoRepository.testDelays` 逐个节点测，与组测速同一路径，不解除固定选择；PC 测完会解除。
2. 每轮结束发出所用的 client，`ProxyViewModel` 只在它仍是自己当前的 client 时刷新，避免 Activity 销毁后拿旧实例请求。

## 连接速率自行差分

`/connections`（WS，1Hz 全量列表）每条只给累计的 `upload` / `download`。[SpeedDetailSheet](../../../../android/app/src/main/kotlin/com/stelliberty/android/ui/screen/home/SpeedDetailSheet.kt) + `updateConnectionRates` 按 `id` 对相邻两次快照差分，再除以实际间隔。

1. 首轮只记基准，第二轮起才输出速率。
2. `topConnectionRates` 用 null 表示「首轮未完成」、空列表表示「无活跃连接」，UI 分别显示 loading 与空态。
3. 订阅只在详情打开期间存续（`DisposableEffect` start/stop），`disconnectStreams` 里同样 stop（旧 client 关闭后基准失效）。

`metadata.process` 依赖 `find-process-mode`，默认注入为 `off`，因此只能按连接统计，按应用聚合没有数据。

连接页的 `/connections` 订阅同样只在本页存续：`ConnectionViewModel` 是 Koin `single`，start/stop 由 `ConnectionScreen` 的 `DisposableEffect` 驱动，`setRepository` 只在 `observing` 时接上。
