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
sleep 8                      # 有 provider 缓存时数秒就绪；缺缓存时可达一分钟，期间 state=Starting
debug-call.sh -s <dev> -m state.get
```

## 出站模式

| method | arg | 作用 |
|---|---|---|
| `proxy.set_mode` | `rule` / `global` / `direct` | 切出站模式 |

embed mode 下 `PATCH /configs` 返回 404，改 mode 走 `override.user.json` + 重启。指令内部调用 `restartWhenReady()`：运行中等状态收敛后重启，未运行时只写入。

## 节点与测速

测速与选择指令需要 mihomo 在跑，排期设置与状态读取可在停止状态使用。

| method | 参数 | 作用 |
|---|---|---|
| `proxy.list` | — | 列出所有组：`名字(类型)->当前节点` |
| `proxy.select` | `group`（extra）+ `node`（arg 或 extra） | 在组内选节点 |
| `proxy.test_group` | 组名（arg） | 测该组全部成员，完成后返回 |
| `proxy.test_node` | 节点名（arg） | 测 runtime 或 provider 节点延迟 |
| `proxy.test_all` | — | 去重后测全部代理组成员，完成后返回 |
| `proxy.set_auto_delay` | 分钟数（arg），0 关闭 | 更新当前订阅的定时测速间隔，可在停止状态设置 |
| `proxy.get_test_state` | — | 读取正在测速的节点、内存结果、排期与界面状态，可在停止状态读取 |

```bash
debug-call.sh -s <dev> -m proxy.list
debug-call.sh -s <dev> -m proxy.select -e group:s:PROXY -a "HK-01"
debug-call.sh -s <dev> -m proxy.test_group -a PROXY
```

`proxy.select` 对 URLTest / Fallback 组同样生效（固定选择），并与代理页一样按当前订阅记录选择，内核重启后恢复；恢复自动选择（`unfix`）用模拟点击，见 `guides/control.md`。

选择指令在发请求前捕获订阅 id，完成后同时核对 repository 与活跃订阅，保存和页面刷新在主线程执行；切换过程中返回失败，避免把旧节点选择写入新订阅。

单测与批测共享并发预算，真实请求开始后节点按钮转圈，结果逐个更新；provider 节点使用专属健康检查接口。

`set_auto_delay` 只更新订阅元数据，不下载或重启；周期设置按订阅保存，延迟结果和下一次执行时间只放内存。首次测速等待完整周期，后台每轮完成后重新排期，停止代理或关闭定时会撤销闹钟。
