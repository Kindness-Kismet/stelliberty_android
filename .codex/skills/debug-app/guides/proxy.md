# 代理指令

```bash
.claude/skills/debug-app/scripts/debug-call.sh -s <device_id> -m proxy.<action> [-a <arg>] [-e k:s:v]
```

## 生命周期

| method | 参数 | 作用 |
|---|---|---|
| `proxy.start` | `uuid`（可选，extra） | 启动；缺省用当前 active 订阅 |
| `proxy.stop` | — | 停止 |
| `proxy.restart` | `uuid`（可选，extra） | 重启（整进程重来） |

三者都经 `ProxyServiceController`，active 订阅与 `config.yaml` 的校验在那里完成，校验失败时 toast 并置 Error。指令的 `ok` 只表示请求已发出，结果看 `state.get`：

```bash
debug-call.sh -s <dev> -m proxy.start
sleep 8                      # 启动窗口可达 10s，期间 state=Starting
debug-call.sh -s <dev> -m state.get
```

## 出站模式

| method | arg | 作用 |
|---|---|---|
| `proxy.set_mode` | `rule` / `global` / `direct` | 切出站模式 |

embed mode 下 `PATCH /configs` 返回 404，改 mode 走 `override.user.json` + 重启。指令内部调用 `restartWhenReady()`：运行中等状态收敛后重启，未运行时只写入。

## 节点与测速

以下指令需要 mihomo 在跑。

| method | 参数 | 作用 |
|---|---|---|
| `proxy.list` | — | 列出所有组：`名字(类型)->当前节点` |
| `proxy.select` | `group`（extra）+ `node`（arg 或 extra） | 在组内选节点 |
| `proxy.test_group` | 组名（arg） | 测组延迟 |
| `proxy.test_node` | 节点名（arg） | 测节点延迟 |

```bash
debug-call.sh -s <dev> -m proxy.list
debug-call.sh -s <dev> -m proxy.select -e group:s:PROXY -a "HK-01"
debug-call.sh -s <dev> -m proxy.test_group -a PROXY
```

`proxy.select` 对 URLTest / Fallback 组同样生效（固定选择），并与代理页一样按当前订阅记录选择，内核重启后恢复；恢复自动选择（`unfix`）用模拟点击，见 `guides/control.md`。

provider 节点在代理页上测速：`test_node` 走 `/proxies/{name}/delay`，proxy-provider 的节点在这个命名空间之外，会返回 404。
