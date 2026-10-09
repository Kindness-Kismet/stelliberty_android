# 订阅指令

```bash
.claude/skills/debug-app/scripts/debug-call.sh -s <device_id> -m subscription.<action> [-a <arg>] [-e k:s:v]
```

| method | 参数 | 作用 |
|---|---|---|
| `subscription.add` | `url`（arg 或 extra）、`name`、`auto_update`（`disabled` / `startup` / `interval`，缺省时按 `interval` 是否大于 0 推断）、`interval`（分钟）、`auto_delay`（自动测试延迟间隔，分钟）、`update_proxy`（`direct` / `system` / `core`，缺省 `core`）、`age_key`（extra，均可选） | 导入并完成校验 |
| `subscription.list` | — | 列出全部，`*` 标记 active |
| `subscription.activate` | uuid 前缀或订阅名（arg） | 设为 active |
| `subscription.update` | uuid 前缀或订阅名（arg） | 重新下载并校验 |
| `subscription.update_all` | — | 更新全部 Url 型 |
| `subscription.delete` | uuid 前缀或订阅名（arg） | 删除 |

定位订阅可用完整 uuid、uuid 前缀或订阅名：

```bash
debug-call.sh -s <dev> -m subscription.list
debug-call.sh -s <dev> -m subscription.add -a "https://example.com/sub.yaml" -e name:s:Probe
debug-call.sh -s <dev> -m subscription.activate -a Probe
```

## add 同步阻塞

`add` 走完整管线：`create`（Pending）→ `apply`（fetch + provider prefetch + Parse）→ commit。provider 预取并发 5、每源 60s 超时、无总时限，多源订阅耗时较长，`content call` 会一直等待。用长超时一次性调用并等它结束：中途打断后 processLock 要到 60s 超时才释放。

失败时 `release` 清掉 pending，`imported/` 与运行中的配置保持原样。

## 导入后的 active

与 UI 一致：只有首次导入（列表原本为空）自动激活，其余用 `activate`。导入的订阅默认经内核更新（`UpdateProxyMode = Core`），与添加页默认值一致；`update_proxy` 可改为直连或系统代理。

## 会重启代理的指令

`activate` / `update` / `update_all` / `delete`（删 active 那条时）都会重启整个进程：mihomo 只在启动时读一次 config.yaml，embed mode 又禁用了 `/restart`。`update` / `update_all` 另受 `RESTART_AFTER_PROFILE_UPDATE`（默认开）约束，且只对 active 订阅生效；`update_all` 在整轮结束后重启一次，后续下载全程有网。

判据是 PID 改变。重启异步进行，需要十几秒：

```bash
debug-call.sh -s <dev> -m state.get                       # 记下 pid
debug-call.sh -s <dev> -m subscription.update -a <名字>
sleep 20
debug-call.sh -s <dev> -m state.get                       # pid 应已改变
```

删掉最后一条订阅时改为停止代理。

## 并发

`ProfileProcessor` 的锁是进程级的，上一条 `update` 未完成时下一条返回 `ok=false`。批量更新用 `update_all`，它内部串行。

## 加密订阅

导入 age 加密订阅时用 `-e age_key:s:<key>` 传密钥（age 密钥不含冒号）。给已有订阅改密钥只能在编辑页填写，见 `guides/control.md`。
