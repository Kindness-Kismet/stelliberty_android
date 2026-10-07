# 运行、编译与截图

## 设备选择

开始前用 `adb devices -l` 列出设备；用户指定时直接用。只有一台设备也要确认它是目标设备。

## ABI

x86_64 模拟器只运行 x86_64 包：`abilist` 里的 `arm64-v8a` 来自 ndk-translation 转译层，cgo 产物 libmihomo.so 会在上面 SIGILL（进程启动即消失，crash 日志含 `berberis::UndefinedInsnThunk`）。

```bash
adb -s <device_id> shell getprop ro.product.cpu.abilist
```

按设备 ABI 传 `--abi`，默认 `arm64-v8a`。

## 编译与安装

首次编译或资源缺失时先运行 `python scripts/prebuild.py`。

```bash
python scripts/build.py compile --dev --abi x86_64   # 改 Kotlin 后的编译检查
python scripts/build.py --dev --abi x86_64           # 打 APK
adb -s <device_id> install -r build/apk/<apk>
```

## 唤醒与锁屏

```bash
adb -s <device_id> shell input keyevent KEYCODE_WAKEUP
adb -s <device_id> shell wm dismiss-keyguard
```

截图黑屏时先重新唤醒解锁。真机测完锁屏。

## 截图

```bash
.claude/skills/debug-app/scripts/debug-screenshot.sh -s <device_id> -n <name>
```

产物写到 `build/tmp/screenshots/`（已忽略），文件名带时间戳，便于前后对比。脚本先查 display id 再 `screencap -d`，空图会删除并报错。

确认控件文字或存在性用 `debug-control.sh -c get` / `-c text`，解析无障碍树比看图可靠。

## 日志

```bash
adb -s <device_id> logcat -c                                     # 操作前先清空
adb -s <device_id> logcat -d -b crash -t 50                      # 崩溃
adb -s <device_id> logcat -d --pid=$(adb -s <device_id> shell pidof com.stelliberty.android)
```

`AppLogger` 统一应用日志入口，`LogFormatter` 统一本地时间、等级与标签格式。`DiagnosticLogStore` 分别保存 `files/stelliberty.log`（应用）和 `files/core.log`（核心），每份上限 5 MB，超限保留后半段完整行；界面只保留最近 500 条（启动时从文件尾部恢复），导出直接读取整份文件。写入前经 `LogRedactor` 脱敏：http(s) / ws(s) 与 DNS 上游（tcp / udp / tls / quic）地址只留协议与主机，其他协议的链接整体遮掉，导出时逐行再过一遍。内核在 API 就绪前的启动日志由 `MihomoRunner` 从 mihomo.log 补进核心日志。写入等级分开控制：应用日志看设置主页的「应用日志」（`app_log_level`，默认 `info`，`silent` 为关闭，经 `settings.set` 写入时下次启动生效），核心日志看网络设置的「核心日志」（覆写的 `log-level`）。应用日志同时写入 logcat，进程被杀后查这个文件：

```bash
adb -s <device_id> shell run-as com.stelliberty.android tail -n 200 files/stelliberty.log
```

mihomo 日志在应用私有目录，debug 包可用 `run-as` 读取：

```bash
adb -s <device_id> shell run-as com.stelliberty.android ls files/mihomo/
adb -s <device_id> shell run-as com.stelliberty.android tail -n 100 files/mihomo/mihomo.log
```

`mihomo.log` 在 debug 级别可达几十 MB，一律用 `tail`。

## 验证流程

1. `adb devices -l` 选定设备，确认 ABI。
2. 唤醒并解除无密码锁屏。
3. 有代码改动时先 `python scripts/build.py compile --dev`，再 `python scripts/build.py --dev` 打 APK 并安装。
4. 用 `debug-open-page.sh` 拉到前台并切到目标页。
5. 清空 logcat，用 debug 指令触发功能。
6. 依次判定：① `ok=true`；② 进程存活（`pidof`）；③ crash 日志为空；④ `state.get` 符合预期；⑤ 改了 UI 时用 `debug-control.sh -c list` 确认控件，再截图看排版。
7. 真机测完锁屏。

## 常见情况

- `page.open` 返回 ok 但页面没变：应用在后台，改用 `debug-open-page.sh`。
- 装包报签名冲突：先 `adb uninstall com.stelliberty.android`。
- 行为没变：确认 APK 已重新打包并安装。
- 代理指令报 not running：预期返回；模拟器的 VPN 授权框要人点。
- 授予 su 后应用仍报无 root：`has_root` 在 MainActivity 冷启动时缓存，授权后重启应用。
- root 状态以应用自身读到的为准：`adb shell` 是 `shell` uid，`run-as` 是 `runas_app` 域，安全上下文都与应用不同。
