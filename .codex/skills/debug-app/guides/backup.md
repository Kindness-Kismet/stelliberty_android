# 备份指令

```bash
.claude/skills/debug-app/scripts/debug-call.sh -s <device_id> -m backup.<action> [-a <文件名>]
```

| method | 参数 | 作用 |
|---|---|---|
| `backup.export` | 文件名（arg，可选，默认 `stelliberty-yyyyMMdd-HHmmss.stelliberty`） | 导出备份 |
| `backup.restore` | 文件名（arg）、`mode`（extra，`overwrite` / `merge`，默认 `overwrite`） | 从备份恢复，要求代理已停止 |

文件都在应用外部文件目录的 `backups/` 下，只取文件名部分，用 adb 推拉：

```bash
debug-call.sh -s <dev> -m backup.export -a example.stelliberty
adb -s <dev> pull /sdcard/Android/data/com.stelliberty.android/files/backups/example.stelliberty
adb -s <dev> push example.stelliberty /sdcard/Android/data/com.stelliberty.android/files/backups/
debug-call.sh -s <dev> -m proxy.stop
debug-call.sh -s <dev> -m backup.restore -a example.stelliberty
debug-call.sh -s <dev> -m backup.restore -a example.stelliberty -e mode:s:merge
```

`restore` 与 UI 恢复走同一管线，PC 生成的备份也能直接恢复。恢复只换磁盘文件，界面与各 ViewModel 仍是旧状态：先 `am force-stop` 再用 `debug-open-page.sh` 拉起，之后再检查界面或启动代理。

失败时 `message` 是 `BackupException` 的英文原因（版本不支持、归档无可恢复数据等），磁盘数据保持原样。
