# 规则覆写指令

通用入口为 `.claude/skills/debug-app/scripts/debug-call.sh -s <device_id> -m rule.<action> -a <订阅>`，订阅可写 Id、Id 前缀或名称。

| 方法 | 参数 | 作用 |
|---|---|---|
| `rule.list` | 无 | 列出自定义规则（`-` 前缀表示已禁用，方括号内为 Id 前 8 位）、禁用的订阅规则、保存的顺序条数，以及订阅规则、代理组与出站目标数量 |
| `rule.add` | `type`、`payload`、`proxy`、`options`（extra，`payload` 对 MATCH 可省略） | 校验并新增一条自定义规则，插在首个 MATCH 前 |
| `rule.toggle` | `name`（extra） | 先按自定义规则 Id 前缀匹配，匹配不到按「类型,内容,目标[,选项]」原文找订阅规则 |
| `rule.delete` | `name`（extra，自定义规则 Id 前缀） | 删除自定义规则 |

```bash
debug-call.sh -s <dev> -m rule.add -a Example -e type:s:DOMAIN-SUFFIX -e payload:s:example.com -e proxy:s:DIRECT
debug-call.sh -s <dev> -m rule.toggle -a Example -e "name:s:DOMAIN-KEYWORD,example,REJECT"
```

指令不做页面上的查重，只走保存前的运行时校验。当前订阅保存后按页面行为重启，运行时结果用 mihomo `/rules` 核对。

页面从订阅编辑页的 `Settings.Entry.subscriptionRuleOverrides` 进入，控件前缀为 `RuleOverride.*`；编辑页的下拉框为 `Settings.Entry.ruleType` / `Settings.Entry.ruleTarget`。模板选择对话框的行没有测试 ID，按文字定位。搜索框展开动画结束前输入的文字不会刷新结果，展开后再输入。
