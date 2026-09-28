# 规则覆写

## 数据

- `RuleOverrideStore` 管理 `rules/rule_overrides.json`，格式同 PC：`Items` 按订阅各一项（`CustomRules` / `DisabledBuiltinRuleKeys` / `RuleOrder`），`Templates` 全局共用。自定义规则 Id 为 `custom-{32 位十六进制}`，模板 Id 为 `template-{32 位十六进制}`。
- 规则键同 PC：类型、内容、目标、选项四段折叠空白并转大写，以 `\u001f` 相连；查重只看类型与内容两段。顺序 Id 为 `builtin:<键>` / `custom:<Id>`。Kotlin `RuleKeys` 与 Go `RuleKey` 必须同一算法，否则禁用与排序会对不上运行时的规则。
- 某订阅的覆写为空时删除该项；删除订阅时一并清理。规则覆写不进备份包，与 PC 一致。

## 页面与保存

- 基线经 `StellibertyCoreBridge.ruleContext` 读取：套覆写与链式代理、不套规则覆写，订阅规则的键由 Go 算出，与运行时一致。读取只用独立锁，不占 processLock。
- 列表页与编辑页共用 `RuleOverrideViewModel` 的草稿，路由的 session 区分每次进入列表页；点保存才写入，直接返回即放弃。
- 只有用户调整过位置才保存 `RuleOrder`，并保存展开后的完整顺序；没有顺序时订阅新增的规则保持原位，自定义规则插在首个 MATCH 前。
- 保存前查重：自定义规则之间、自定义规则与启用的订阅规则之间，类型与内容都不能重复。随后在 processLock 内用完整变换校验再写入，失败保留原值；当前订阅保存后经 `restartWhenReady` 生效。
- 应用模板按键合并，已有的规则不重复添加，新加入的规则换新 Id；同名模板（不区分大小写）覆盖原内容。

## 运行时规则（Go `overrides/rules.go`）

- 顺序：原订阅 → 覆写 → 链式代理 → 规则覆写 → 运行参数。
- `rules` 必须是字符串序列，否则报错；没有自定义规则、禁用项和顺序时原样返回。
- 禁用的订阅规则按键移除；启用的自定义规则中，出站目标不在内置动作、节点与代理组里的在运行时跳过，不写回禁用状态。
- 有顺序时同一 Id 只取第一条，顺序里没有的订阅规则接在末尾，未排序的自定义规则插到首个 MATCH 前。
