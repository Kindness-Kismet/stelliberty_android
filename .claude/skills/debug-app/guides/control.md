# 控件定位与模拟点击

```bash
.claude/skills/debug-app/scripts/debug-control.sh -s <device_id> -c <action> [-i <test_id>] [-v <value>]
```

业务逻辑优先用业务指令（见 SKILL.md），本页只讲控件操作。

## 六个动作

| action | 参数 | 作用 | 退出码 |
|---|---|---|---|
| `list` | — | 列出当前界面全部测试 ID | 0 |
| `text` | — | 列出当前界面全部可见文字 | 0 |
| `exists` | `-i ID` | 断言控件存在 | 存在 0，不存在 1 |
| `get` | `-i ID` | 读控件文字（含子节点） | 找不到 1 |
| `click` | `-i ID` | 点控件中心 | 找不到 1 |
| `input` | `-i ID -v VALUE` | 点进控件后输入文本 | 找不到 1 |

```bash
debug-control.sh -s <dev> -c list
debug-control.sh -s <dev> -c get -i Home.StatusCard
debug-control.sh -s <dev> -c click -i Nav.Tab.1
debug-control.sh -s <dev> -c exists -i Proxy.MoreButton && echo 存在
```

## 时序

脚本每次执行都重新 `uiautomator dump`，坐标始终对应当前界面。Compose 动画不向无障碍层报 busy，页面切换、Dialog 弹出、Popup 展开后先 `sleep 1` 再操作。

## `get` 与 `text`

testTag 挂在最外层节点，按钮文字在子节点，`get` 返回整棵子树的文字。`text` 列出全界面文字：查文案用 `text`，查控件用 `list`。

## 测试 ID

全部登记在 [TestTags.kt](../../../../android/app/src/main/kotlin/com/stelliberty/android/ui/util/TestTags.kt)，命名「页面.控件」：

| 前缀 | 覆盖 |
|---|---|
| `Home.*` | 启停按钮、状态卡、延迟测试、流量卡、订阅卡 |
| `Proxy.*` | 搜索 / 排序 / 更多菜单、搜索框、模式与 TUN Stack 菜单项；布局开关 `Proxy.GroupLayout` |
| `Proxy.GroupTab.<组名>` | 横向组标签 |
| `Proxy.Group.<组名>` | 代理组行；`.TestButton` 是组测速（兼作右侧延迟标签），`.ToggleButton` 是展开，`.UnfixButton` 是解除固定 |
| `Proxy.Node.<节点名>` | 节点项；`.TestButton` 是节点测速 |
| `Subscription.*` | 新增、全部更新、添加页与编辑页的自动测试延迟输入框（`AutoDelayField`）；`Subscription.Item.<uuid>` 是订阅行 |
| `Overrides.*` | 新增、保存、名称与地址输入；`Item.<id>` 的编辑 / 更新 / 删除按钮，`Select.<id>` 的选择与上下移动 |
| `ChainProxy.*` | 新增、保存、名称输入；`Custom.<id>` 的开关与删除按钮，`Hop.<序号>` 的上下移动与移除 |
| `RuleOverride.*` | 新增、更多、保存；`Row.<序号>` 是规则行（序号从 1 起），`.Toggle` 是开关；`Menu.<序号>` 是更多菜单项；编辑页的内容、位置输入与删除按钮 |
| `Settings.Entry.<key>` | 设置项，由 `groupedCardItems` 按 `CardItem.key` 自动生成，key 与 `settings.dump` 的键名一致 |
| `AppProxy.Mode.<模式名>` | 分应用代理三种模式（`AllowAll` / `AllowSelected` / `DenySelected`） |
| `AppProxy.App.<包名>` | 应用项，点一下切换勾选 |
| `Log.*` | 清空按钮 `Log.ClearButton`、列表 `Log.List` 与空列表连接提示 `Log.Status` |
| `Connection.CloseAllButton` | 关闭全部连接；`Connection.Close.<连接 id>` 是单条 |
| `Nav.Tab.<0-3>` | 底栏四个 Tab；`Nav.BackButton` 是返回 |

组名与节点名直接拼进 ID，含空格、竖线或 emoji 时 `-i` 加引号：

```bash
debug-control.sh -s <dev> -c click -i "Proxy.Node.Example 🔹 日本 | 1"
```

脚本内部会按远端 sh 的规则再引一次；手写 `adb shell` 时同样要处理。

`-c list` 输出的 ID 可直接交给 `-i`：脚本已把 uiautomator 写出的数字引用（`&#128313;`）还原成字面字符。

dump 只含已渲染的节点：长列表先滚到目标位置，或改用业务指令（`proxy.select` 覆盖全部节点）。

菜单在独立窗口中，测试 ID 要在菜单内容里单独启用导出。

## 只有 debug 包有 ID

`App.kt` 在 debug 下开启 `testTagsAsResourceId`，把 testTag 写进无障碍树的 `resource-id`；release 包上 `-c list` 只列出零星系统 ID。

## `input` 的转义

脚本只把空格换成 `%s`；`&`、`|`、`(`、`'` 等由调用方转义，或走剪贴板：

```bash
adb -s <dev> shell am broadcast -a clipper.set -e text "<value>"   # 需装剪贴板工具
```

URL、age 密钥这类标点密集的值优先用业务指令（`subscription.add` 的 `url` / `age_key` extra）。

## 只能模拟点击的操作

- URLTest / Fallback 组的 `unfix`（恢复自动选择）
- 编辑已有订阅的 age 密钥、自定义 User-Agent
- 备份恢复的 SAF 文件选择器（系统 UI，无测试 ID；备份内容本身用 `backup.*` 验证）
- 网络设置页里归 `override.user.json` 的项（mixed-port、DNS）
- 分应用代理的模式与勾选：`settings.set` 可写 `app_proxy_mode` / `app_proxy_packages`，验证界面联动时点 `AppProxy.*`

## 新增控件

在 `TestTags.kt` 登记后挂 `Modifier.testTag(...)`；设置项由 `groupedCardItems` 按 key 自动生成。测试 ID 是调试脚本的契约，改名时在 debug-app skill 里搜出引用一并更新。
