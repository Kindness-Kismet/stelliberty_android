# 备份与恢复

WebDAV / 本地备份恢复（设置 →「数据管理」）。包结构与 PC `FileDataBackupService` 一致，两端的包可互相恢复。见 [BackupManager](../../../../android/app/src/main/kotlin/com/stelliberty/android/data/backup/BackupManager.kt) / [PortableSettings](../../../../android/app/src/main/kotlin/com/stelliberty/android/data/backup/PortableSettings.kt) / [WebDavClient](../../../../android/app/src/main/kotlin/com/stelliberty/android/data/backup/WebDavClient.kt)。

| 包内路径 | 内容 |
|---|---|
| `settings.json` | 可迁移设置，PC 键名，映射由 `PortableSettings` 维护 |
| `subscriptions/subscriptions_list.json` | 已导入订阅，与 PC 同格式 |
| `subscriptions/{id}.yaml` | 订阅配置明文 |
| `overrides/overrides_list.json`、`overrides/{id}.yaml|js` | 覆写列表与内容，PC 格式 |
| `android/backup.json` | 偏好与开机自启，`version = 3`；有它才算 Android 的包 |
| `android/override.user.json`、`android/subscriptions/`、`android/proxies/` | 覆写、草稿列表、当前订阅、节点选择 |
| `android/imported/**`（不含 `{id}/config.yaml`）、`android/pending/**` | provider 缓存与草稿目录 |

- **PC 兼容**：PC 恢复只收白名单条目，`android/` 整体跳过；PC 读取列表时拒绝未知键，根目录列表原样取自 SubscriptionStore。文件名用 `BackupManager.newBackupFileName()`（`stelliberty-yyyyMMdd-HHmmss.stelliberty`）；SAF 按 `application/octet-stream` 创建，按 zip 类型创建时文档提供方会再补一个 `.zip`。
- **明文导出**：PC 只保存解密后的订阅。带 age 密钥的订阅导出时经 `StellibertyCoreBridge.decryptFile` 解到 cacheDir 再写入，解密失败则整次导出失败。恢复后本地是明文，密钥保留：mihomo 对没有 age 头的内容原样放行，下次更新重新取密文。
- **两种恢复模式**（`RestoreMode`，UI 先选模式再恢复）：覆盖以备份为准，快照里没有的偏好删除回到默认，排除名单里的键保留；合并只加入本机没有的订阅与覆写 Id，连同 `imported/{id}/` 与节点选择，本机设置、已有覆写、草稿与当前订阅不动。合并先移入目录再写列表，中途失败时多出的目录不在列表里，由启动清理按孤儿删除。PC 的合并按文件补缺，本地已有列表时加不进新订阅，两端语义不同。
- **覆盖恢复 PC 的包**（没有 `android/backup.json`）：`subscriptions/{id}.yaml` 放到 `imported/{id}/config.yaml`，provider 在下次更新时预取；`settings.json` 经 `PortableSettings.import` 合进本机覆写与偏好，未映射的本机设置保留；草稿与节点选择随覆盖清空。两种包恢复后当前订阅失效时都改选列表第一条。
- **拒绝的包**：`android/backup.json` 版本不等于 3；三类数据都没有。版本校验排在解包之后（单遍读取归档才能看到 backup.json），此时只写过 staging。
- **WebDAV 与 PC 共用远端目录** `stelliberty-backups`：每次按新文件名上传，上传后按 `getlastmodified` 只留最近 5 份（PC 上传的也计入，与 PC 默认保留数一致）；恢复取修改时间最新的一份，排序规则与 PC 相同。目录不存在时 PROPFIND 回 404，按没有备份处理，恢复不建目录。
- **锁**：备份与恢复都持 `ProfileProcessor.withProcessLock`，导出时取存储快照、恢复时换入与 `reload()` 再包一层 `withProfileLock`（create / patch / delete 只持 profileLock）。导出用存储的内存值而不是读盘：当前订阅、节点选择与覆写都是异步写盘，文件可能落后一步。恢复换入后立即 `reload()` 订阅、节点选择、用户设置与覆写文件存储，内存里的旧值不能再写回磁盘。恢复前要求代理已停止。
- **重定向手动跟随**：Ktor 默认只对 GET / HEAD 跟随 3xx，而服务器常把无尾斜杠的目录 301 到带斜杠版本。集合 URL 一律带尾斜杠，`davRequest` 手动跟随 301 / 302 / 307 / 308 并保持方法与 body，**仅限同主机**（Basic 凭据跨主机会泄露密码）。
- **恢复两阶段写入**：先全部写进 `.restore/`（zip-slip 校验的基准目录），rename 换入时才触碰正式目录，失败时从 `.restore-old/` 回滚。覆写列表、Id 与内容在换入前验证；包内路径经 `stagingPathOf` 白名单映射，`dns.json` 等不认识的条目跳过。
- **全程流式**：zip 直接写入目标流，恢复时边读边写 staging，内存里只有 `settings.json` 与 `android/backup.json`（KB 级）；provider 缓存可让备份达到几十 MB。SAF 侧只传 `Uri`，读写在 data 层的 IO 上完成；WebDAV 经 cacheDir 中转文件，以便带上 Content-Length。
- **排除项**：geodata 的符号链接与实体文件（名单 + isSymbolicLink 双重判断，防止 readBytes 沿链接把几十 MB 实体打进 zip）；prefs 黑名单排除设备 / 运行时态与 WebDAV 凭据本身。
- **恢复后强制重启进程**（内存热状态不随磁盘刷新）。开机自启是 PackageManager 组件状态而非 pref，走快照里的独立字段；Wi-Fi 策略的组件状态与监控服务由重启后的 MainActivity 按 `WIFI_POLICY_ENABLED` 幂等恢复。
- **本地备份**复用同一 zip 与恢复管线：SAF `CreateDocument`（`"wt"` 截断写入，清掉旧文档尾部）+ `OpenDocument`（接受任意 MIME，经网盘流转后常变成 octet-stream）。
