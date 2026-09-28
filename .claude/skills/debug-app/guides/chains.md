# 链式代理指令

通用入口为 `.claude/skills/debug-app/scripts/debug-call.sh -s <device_id> -m chain.<action> -a <订阅>`，订阅可写 Id、Id 前缀或名称。

| 方法 | 参数 | 作用 |
|---|---|---|
| `chain.list` | 无 | 列出内置链（`-` 前缀表示已禁用）、自定义链，以及代理组与候选项数量 |
| `chain.add` | `name`、`group`、`hops`（extra，逗号分隔，首跳写成 `@组名` 表示代理组） | 校验并新增一条自定义链 |
| `chain.toggle` | `name`（extra） | 先按自定义链的名称或 Id 前缀匹配，匹配不到按内置链名切换 |
| `chain.delete` | `name`（extra，自定义链名称或 Id 前缀） | 删除自定义链 |

```bash
debug-call.sh -s <dev> -m chain.add -a Example -e name:s:ExampleChain -e group:s:Proxy -e hops:s:HK,JP
```

写入类指令返回 `cycle=true|false`；检测到循环照常保存，与页面一致。当前订阅保存后按页面行为重启，运行时结果用 mihomo `/proxies` 核对：中间跳名为 `__stelliberty_chain_*`，末跳用显示名并出现在所属组的 `all` 里。

页面从订阅编辑页的 `Settings.Entry.subscriptionChainProxies` 进入，控件前缀为 `ChainProxy.*`。编辑页的候选项为 `Settings.Entry.chainCandidate.<Kind 序号>:<名称>`，名称输入框点完要先收起键盘再点候选项。
