# DNS 指令

```bash
.claude/skills/debug-app/scripts/debug-call.sh -s <device_id> -m dns.<action> [-a <arg>] [-e k:s:v]
```

| method | 参数 | 作用 |
|---|---|---|
| `dns.query` | 域名（arg 或 `value` extra）+ `type`（extra，默认 `A`） | 查一条 DNS 记录 |
| `dns.flush_cache` | 无 | 清 DNS 缓存 |
| `dns.flush_fakeip` | 无 | 清 fake-ip 池 |

```bash
debug-call.sh -s <dev> -m dns.query -a www.baidu.com
debug-call.sh -s <dev> -m dns.query -a github.com -e type:s:AAAA
debug-call.sh -s <dev> -m dns.flush_cache
```

`type` 取值 `A` / `AAAA` / `CNAME` / `MX` / `TXT` / `NS`，与 UI 六个按钮同源（`domain/model/DnsQuery.kt` 的 `DnsQueryTypes`），其他值在本地直接返回 `ok=false`。

三条指令都走 mihomo 的 `/dns/query`，需要代理在跑。

## 结果

`message` 为 `status=<rcode>, <n> answers`，`data` 为分号连接的 `<类型> <值> ttl=<秒>`。`status=0` 且 0 条应答属正常（域名存在但没有该类型记录）；`status` 非 0 按 DNS RCODE 读（3 = NXDOMAIN）。

## 与 DNS 页的关系

指令直连 repository、绕过 `DnsQueryViewModel`，结果只在返回的 Bundle 里；应用未打开该页、甚至 MainActivity 未启动时也能用。验证页面渲染时在输入框里手动输入。

这一页用业务指令验证：键盘弹出会整体上移类型与查询按钮，坐标随之失效。输入框与类型按钮尚未登记测试 ID，需要时先在 `TestTags.kt` 补上。
