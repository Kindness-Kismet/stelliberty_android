---
name: subscription
description: 订阅的导入管线（Pending → Processing → Imported）、协程锁、活跃订阅、自动更新与备份恢复。改 data/repository/ 下的 ProfileProcessor / SubscriptionRepositoryImpl、service/ProfileWorker、data/backup/ 或调 StellibertyCoreBridge 前必须先读。触发词包括 导入订阅, 添加订阅, 更新订阅, 删除订阅, 切换订阅, 订阅管线, ProfileProcessor, 订阅锁, processLock, 深链, clash://, age 加密, 自动更新, 备份, 恢复, WebDAV, 订阅流量, 到期时间, 链式代理, dialer-proxy, 规则覆写, 自定义规则, 规则模板.
user_invocable: true
---

## 核心约束

1. 三阶段沙箱按 CREATE → APPLY（snapshot / fetchAndValid / commit）→ RELEASE 进行。`imported/` 一律「先拷后 rename」：update 路径下它是唯一副本。
2. `ProfileProcessor.processLock` 是 companion 进程级锁：`processing/` 是不带 uuid 的单例目录，前台 VM 与后台 Worker 是不同实例，共用一把锁才能让配置提交到正确的 uuid。
3. `kotlinx.coroutines.sync.Mutex` 不可重入：`markUpdated` / `commitPending` / `queryImported` / `queryPending` 由外层持锁调用，内部直接执行；`create` / `patch` / `release` / `delete` 直接被 VM 调用，自带锁。
4. 更新后重启代理：mihomo 只在启动时读一次 config.yaml，embed mode 下 `/restart` 返回 404。五个入口（订阅页单条 / 全部 / 启动时更新 / 后台任务 / 调试指令）都调用 `restartAfterProfileUpdate`。

## 指引索引

只读当前任务需要的 guide。

| 任务 | 指引 |
|---|---|
| 导入 / 校验 / 取消、锁、HTTP 与自动命名、age 加密、深链 | `guides/pipeline.md` |
| 活跃订阅切换、更新后重启、流量数据合并、后台更新与闹钟 | `guides/lifecycle.md` |
| WebDAV / 本地备份、恢复的两阶段写入与流式处理 | `guides/backup.md` |
| 覆写文件、订阅选择与排序、保存校验和备份一致性 | `guides/overrides.md` |
| 内置与自定义链式代理、候选项上下文、运行时展开规则 | `guides/chains.md` |
| 规则覆写的键、顺序、模板、保存校验与运行时合并 | `guides/rules.md` |

订阅文件与字段契约见仓库根的 [AGENTS.md](../../../AGENTS.md)（订阅数据章节）；代理启停见 `proxy-service` skill；mihomo HTTP / WS 见 `mihomo-api` skill。

## 维护

`Subscription` 新增字段前先确认 PC 已有同名字段：PC 读取时拒绝未知键，多一个键就整份列表读不出来。

改 Go 侧 `fetch.go` 的错误文案时同步改 `ProfileProcessor.translateCoreError`，用户才能看到本地化文案。
