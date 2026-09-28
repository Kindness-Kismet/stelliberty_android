# 设置指令

```bash
.claude/skills/debug-app/scripts/debug-call.sh -s <device_id> -m settings.<action> [-a <arg>] [-e k:s:v]
```

| method | 参数 | 作用 |
|---|---|---|
| `settings.get` | key（arg） | 读一个偏好 |
| `settings.set` | key（arg 或 `name` extra）+ `value` / `enabled`（extra） | 写一个偏好 |
| `settings.dump` | 过滤子串（arg，可选） | 列出全部偏好，可按子串过滤 |
| `settings.set_tun_mode` | `vpn` / `root_tun` / `root_tproxy` | 切隧道模式 |

```bash
debug-call.sh -s <dev> -m settings.dump -a wifi
debug-call.sh -s <dev> -m settings.set -a auto_connect_on_launch -e enabled:b:true
debug-call.sh -s <dev> -m settings.get -a auto_connect_on_launch
debug-call.sh -s <dev> -m settings.set_tun_mode -a root_tproxy
```

布尔值用 `-e enabled:b:true`，字符串用 `-e value:s:xxx`；底层均存为字符串，`settings.get` 返回字符串形式。

## 范围

`settings.*` 读写 `PlatformStorage`（SharedPreferences），键名见 `android/app/src/main/kotlin/com/stelliberty/android/platform/PlatformStorage.kt` 的 `StorageKeys`，也可直接 `settings.dump` 查看。

mixed-port、DNS、TUN 栈等 mihomo 配置归 `override.user.json`（`OverrideJsonStore`），在 UI 的网络设置页修改；出站模式例外，用 `proxy.set_mode`。

## 切隧道模式

`settings.set_tun_mode` 只写偏好；代理在跑时再 `proxy.restart` 才会切换，返回消息附带当前 `proxy state`。模拟器缺 su 时，ROOT 两种模式启动会回退 VPN 或报错。

## 写入校验

`settings.set` 不校验键名与取值，写入前对照 `StorageKeys` 核对。

集合型键（`app_proxy_packages`、`wifi_policy_ssids`）登记在 `SET_TYPED_KEYS`：`set` 传逗号分隔，`get` 按逗号返回，内部走 `putStringSet` / `getStringSet`：

```bash
debug-call.sh -s <dev> -m settings.set -a wifi_policy_ssids -e value:s:"HomeWiFi,Office"
debug-call.sh -s <dev> -m settings.get -a wifi_policy_ssids   # → Office,HomeWiFi
```

底层是 Set，返回顺序随机，断言时按集合比较。

新增集合型偏好时同步登记进 `SettingsDebugCommands.kt` 的 `SET_TYPED_KEYS`：未登记的键 `set` 会写成字符串，`get` 因类型不符静默返回空串（`PlatformStorage.getString` 吞下 `ClassCastException` 以防崩溃循环）。
