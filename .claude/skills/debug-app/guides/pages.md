# 页面指令

```bash
.claude/skills/debug-app/scripts/debug-open-page.sh -s <device_id> -p <page_id>
```

脚本先 `am start` 再发指令。`DebugNavBridge.request` 等组合树里的 collector 就位（最多 6s）后才投递，`ok=true` 即页面已切换，冷启动也无需额外 sleep。

## 主页 4 个 Tab

| page_id | 作用 |
|---|---|
| `home` | 首页（状态、流量、系统信息） |
| `proxy` | 代理页（代理组与节点） |
| `subscription` | 订阅页 |
| `settings` | 设置页 |

Tab 间走 `HorizontalPager.animateToPage`，动画约 300ms，截图前留出时间。

## 二级页

设置类页面（`settings.*`）先把 pager 切到设置 Tab 再压栈，返回后落在设置页。界面里按层级进入，返回逐级回到上一页，例如 设置 → Clash 特性 → 系统集成 → VPN 设置。

| page_id | 作用 |
|---|---|
| `subscription.add` | 新增订阅（选择来源） |
| `log` | 日志 |
| `provider` | 外部资源 |
| `dns` | DNS 查询 |
| `connection` | 连接 |
| `settings.theme` | 主题设置 |
| `settings.clash` | Clash 特性（以下五页的入口与核心日志等级） |
| `settings.network` | 网络设置（统一延迟、局域网、IPv6、嗅探器） |
| `settings.port_control` | 端口控制（代理端口、外部控制器） |
| `settings.system_integration` | 系统集成（隧道模式，VPN / ROOT 设置与分应用代理的入口） |
| `settings.dns` | DNS 配置 |
| `settings.performance` | 性能优化（Geodata 模式、进程匹配） |
| `settings.vpn` | VPN 设置 |
| `settings.root` | ROOT 设置 |
| `settings.app_proxy` | 分应用代理 |
| `settings.overrides` | 覆写管理 |
| `settings.file_manager` | 文件管理 |
| `settings.app_behavior` | 应用行为（自动连接、开机重启、Wi-Fi 策略入口、通知、应用日志等级） |
| `settings.wifi_policy` | Wi-Fi 策略 |
| `settings.about` | 关于 |
| `settings.data` | 数据管理（本地与 WebDAV 备份恢复） |
| `settings.age_key` | Age 密钥 |

## 带参数的页面

`SubscriptionAddUrl` / `SubscriptionEdit` / `FileManagerEditor` 需要参数（url、uuid、路径），走深链或业务指令。添加页可用深链预填：

```bash
adb -s <device_id> shell 'am start -a android.intent.action.VIEW \
  -d "clash://install-config?url=https%3A%2F%2Fexample.com%2Fsub.yaml&name=Probe"'
```

深链走 popUntil Main → 切订阅 Tab → 压预填的添加页，停在等用户确认的状态。直接完成导入用 `subscription.add`（见 `guides/subscription.md`）。
