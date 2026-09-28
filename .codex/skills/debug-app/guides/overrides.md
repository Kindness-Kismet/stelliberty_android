# 覆写指令

通用入口为 `.claude/skills/debug-app/scripts/debug-call.sh -s <device_id> -m override.<action>`。

| 方法 | 参数 | 作用 |
|---|---|---|
| `override.add` | 地址（arg）、`name`、`format`（extra，`yaml` / `js`） | 添加远程覆写，通过内核运行端口下载 |
| `override.list` | 无 | 列出覆写，`*` 标记当前订阅使用的项 |
| `override.update` | Id、Id 前缀或名称（arg） | 更新远程覆写 |
| `override.save` | 完整内容（arg）、`uuid`（extra，Id、前缀或名称） | 校验并保存内容，空字符串可保存空文件 |
| `override.select` | 订阅 Id、前缀或名称（arg）、`ids`（extra，覆写 Id 或名称，逗号分隔） | 按传入顺序选择；省略 ids 清空选择 |
| `override.delete` | Id、Id 前缀或名称（arg） | 删除覆写并清理订阅引用 |

影响当前订阅时按页面行为重启，独立于“更新订阅后重启”偏好。校验失败返回 `ok=false`，已保存的内容与选择保持原样。

列表页用 `debug-open-page.sh -p settings.overrides`。控件前缀为 `Overrides.*`；选择行的上下移动按钮分别为 `Overrides.Select.<id>.MoveUpButton` / `MoveDownButton`。
