# 订阅覆写

## 存储与顺序

- `OverrideProfileStore` 管理 `overrides/overrides_list.json` 和 `overrides/{id}.yaml|js`，采用 PC 的 PascalCase 键、枚举编号与 ISO 时间。`OverrideJsonStore` 仍只管理用户设置，两者职责独立。
- `Subscription.orderedOverrideIds` 先取排序偏好中已勾选的项，再补其余 `OverrideIds`，每项只应用一次。选择页保存全部显示顺序，并按该顺序写入已勾选项。
- 修改选择时同步更新已导入订阅和草稿；删除覆写时清理两份列表中的选择与排序引用，避免草稿提交后恢复失效引用。
- 覆写内容与列表的写入共用 `ProfileProcessor.processLock`，锁顺序为覆写仓库锁 → processLock → profileLock。备份在 processLock 内取得一致快照；下载在这些锁外进行。

## 校验与运行

- 保存选择先校验所选订阅；编辑内容、更新与更改格式时，校验当前订阅使用的覆写。校验和提交处于同一次 processLock 持有期间。
- 校验使用独立的变换文件与内容临时文件，经 `StellibertyCoreBridge.validateTransform` 调用同一套 Go 变换和配置解析流程，订阅已有的链式代理与规则覆写一并参与。失败保留正式内容与选择；加密订阅的密钥同时用于配置与 provider。
- 当前订阅的覆写顺序、内容或格式变化，以及删除正在使用的覆写，通过 `restartWhenReady` 生效；订阅更新的重启偏好只约束订阅更新。未运行时保持停止。
- 服务启动由 `ProfileTransformWriter` 重写 `profile.transform.json`，通过 `--transform` 传入；没有覆写、链式代理和规则覆写时清掉旧文件。应用顺序为原订阅 → 逐项覆写 → 链式代理 → 规则覆写 → 运行参数，原订阅文件保持原样。

## 下载与备份

- 远程覆写的更新方式复用订阅代理解析器，请求超时 30 秒；批量更新逐条下载，最后至多重启一次。
- 备份携带覆写列表和内容；合并按 Id 只补缺失项，覆盖替换整套覆写。换入前校验列表、Id 唯一性与内容文件完整性，损坏的包在暂存阶段拒绝。
- 原生覆写实现与测试在 `stelliberty_core/overrides/`，新增源码须在根 `.gitignore` 的目录白名单内。
