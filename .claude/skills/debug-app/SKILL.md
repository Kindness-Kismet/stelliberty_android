---
name: debug-app
description: Use the debug command provider to navigate or exercise Stelliberty on a device. Triggers on phrases like 调试应用, 测试应用, 验证应用, app验证, app测试, 测试app, 验证app, 实机测试, 实机验证, 模拟器测试, 测试某项功能, 验证某项功能, 验证某个页面, 测试某个页面, 验证某个提交, 测试某个提交, 验证修改, 验证修复, 验证改动, 验证 commit, 测试 commit.
user_invocable: true
---

## 使用原则

调试、测试或验证 Stelliberty 时优先用 debug 指令，坐标点击只用来验证控件本身。

- 先用 `debug-open-page.sh` 把应用拉到前台：它先 `am start` 再发 `page.open`。指令靠组合树内的 collector 生效，应用在后台时只会返回 ok。
- 返回 Bundle 的 `ok=true` 才算成功。
- 改单项设置用 `settings.set` 写明确值，当前状态始终确定。
- `proxy.list` / `select` / `test_*` 与全部 `dns.*` 需要 mihomo 在跑，未运行时返回 `ok=false, message=Proxy is not running`，属预期结果。
- VPN 链路在真机上验证（模拟器的 VPN 授权框需要人点）；ROOT 两种模式可在有 su 的模拟器上测。

## 业务逻辑用业务指令验证

两者证明的东西不同：业务指令直接调 repository / processor，结果确定、失败原因明确；模拟点击经真实输入派发，证明的是「控件此刻可点且有反应」，失败时难以区分业务、遮挡还是动画。

| 要验证什么 | 用什么 |
|---|---|
| 功能是否正确（导入、切节点、改设置、DNS） | 业务指令 |
| 控件是否存在、文案、布局 | `debug-control.sh -c list` / `-c get` / 截图 |
| 点击链路是否通（遮挡、禁用） | `debug-control.sh -c click` |
| 暂无业务指令的操作（取消固定节点、编辑已有订阅） | 模拟点击 |

## 指引索引

只读当前任务需要的 guide。

| 任务 | 指引 |
|---|---|
| 设备选择、编译安装、截图、日志、验证流程 | `guides/runtime.md` |
| 页面跳转 | `guides/pages.md` |
| 启动停止、出站模式、选节点、测速 | `guides/proxy.md` |
| 订阅导入、更新、切换、删除 | `guides/subscription.md` |
| 覆写新增、选择、保存、更新与删除 | `guides/overrides.md` |
| 链式代理列出、新增、启停与删除 | `guides/chains.md` |
| 规则覆写列出、新增、启停与删除 | `guides/rules.md` |
| 偏好读写、隧道模式 | `guides/settings.md` |
| 备份导出与恢复 | `guides/backup.md` |
| DNS 查询、清缓存 | `guides/dns.md` |
| 按测试 ID 定位控件、模拟点击 | `guides/control.md` |

## 指令入口

包名 `com.stelliberty.android`（debug 与 release 同包名，装包前留意签名）；Activity `com.stelliberty.android/.MainActivity`；命令 Provider `content://com.stelliberty.android.commands`。

页面切换：

```bash
.claude/skills/debug-app/scripts/debug-open-page.sh -s <device_id> -p <page_id>
```

其余指令用通用脚本，按 `ok` 设置退出码：

```bash
.claude/skills/debug-app/scripts/debug-call.sh -s <device_id> -m <domain.action> [-a <arg>] [-e key:s:value]
```

控件操作与截图：

```bash
.claude/skills/debug-app/scripts/debug-control.sh -s <device_id> -c list
.claude/skills/debug-app/scripts/debug-screenshot.sh -s <device_id> -n <name>
```

底层形式：

```bash
adb shell content call \
  --uri content://com.stelliberty.android.commands \
  --method <domain.action> \
  --arg <target> \
  --extra value:s:<value>
```

Provider 只存在于 debug source set（`android/app/src/debug/`）；`exported=true`，只放行 shell / root / 自身 UID。

## 命令风格

1. 方法名一律 `动词` 或 `动词_宾语`：`proxy.list`、`settings.set_tun_mode`。
2. 主目标经 `--arg` 传，extras 只放修饰参数，`value` extra 等价于 `--arg`。`--extra KEY:TYPE:VALUE` 按冒号分段，含冒号的值（URL、`host:port`）走 `--arg`。

`adb shell` 把参数拼成一行交给设备上的 sh，含空格或竖线的值（节点名 `Example 🔹 香港 | 5`）要按远端规则再引一次。脚本已处理；手写 `adb shell` 时自行加引号。

## 维护

指令一律 `domain.action`，实现放 `android/app/src/debug/kotlin/com/stelliberty/android/debug/` 下对应的 `*DebugCommands.kt`，并登记进 `DebugCommandContracts.kt` 的方法集合，Provider 才能分发。增删改指令时就地更新对应 guide 的表格。

新增可交互控件在 [TestTags.kt](../../../android/app/src/main/kotlin/com/stelliberty/android/ui/util/TestTags.kt) 登记测试 ID，命名「页面.控件」；设置项由 `groupedCardItems` 按 `CardItem` 的 key 自动生成。
