# 导入管线

## JNI in-process

fetch + provider prefetch + Parse 三步经 `StellibertyCoreBridge.fetchAndValid` 在进程内完成，与 runtime 共用 GeoIP，并受 processLock 串行保护。`StellibertyApplication.onCreate` 先 `StellibertyCoreBridge.init(homeDir, userAgent)` 把 `constant.SetHomeDir` 指向共享 GeoIP 目录 `files/mihomo/geodata/`，再起后台线程解压地理数据；`fetchAndValid` 与 `validateTransform` 进入 native 前先 `ProfileFileOps.awaitGeodata()`，否则 Parse 读到缺失的文件会让 mihomo 往同一目录现场下载。`fetchAndValid` 内部分配 token，每 150ms 轮询一次进度。

## 取消语义

外层 `runProcess` 可取消；commit 阶段包 `withContext(NonCancellable)`，文件 swap 与列表更新整体原子；catch 中的 `cleanupProcessing` 同样 NonCancellable。

阻塞的 JNI 调用不响应协程取消，`withContext` 要等它返回才抛 `CancellationException`，那时 Go 侧 defer 已清空 `cancelRegistry`，`nativeCancel` 失效，processLock 会被占到 60s 超时。因此 `fetchAndValid` 在阻塞调用之前挂一条停在 `awaitCancellation()` 的 watcher 协程，取消瞬间即调 `nativeCancel`；`nativeDone` 标记避免正常返回后重复调用，`finally` 里 cancel 掉 watcher，`coroutineScope` 才能返回。watcher 必须先于阻塞调用启动：`invokeOnCompletion` 要等子协程收敛后才触发，时机太晚。`cancelCurrentUpdate` 先同步 `clearProgress()` 让 Dialog 立即消失，再 cancel。

## 协程锁

`kotlinx.coroutines.sync.Mutex` 不可重入。`markUpdated` / `commitPending` / `queryImported` / `queryPending` 由 `ProfileProcessor` 在 `withProfileLock{}` 内调用，内部直接执行；`create` / `patch` / `release` / `delete` 直接被 ViewModel 调用，自带锁。锁顺序 processLock → profileLock，全仓只在 commit 一处嵌套。

`processing/` 是进程内的单例沙箱（路径不带 uuid）：`prepareProcessing` 每次清空后重填，`commitProcessingToImported(uuid)` 再换入 imported/。`ProfileProcessor.processLock` 因此是 companion 进程级 Mutex。`ProfileProcessor` 是 Koin `factory`，前台 `SubscriptionViewModel.processor` 与后台 `ProfileWorker`（每个 `ACTION_UPDATE_PROFILE` 取一个）是不同实例，实例级锁会让两个并发 update 交错清空 `processing/`，把 B 的配置提交进 `imported/A/`。

启动清理走 `ProfileProcessor.cleanupResidual()`（持同一把锁）：先 `cleanupProcessing`（它会把 `commit.old.{uuid}` 还原回 imported/），再按订阅列表与草稿列表现存的 Id 反扫 `imported/`、`pending/`，删除孤儿目录（删除订阅分「先删列表条目 → 再删目录」两步，中途进程死亡会留下孤儿）。列表文件解析失败时 `knownUuidsOrNull()` 返回 null，跳过这一步，否则会删光全部订阅。

## HTTP 与自动命名

- 订阅 HTTP 走 mihomo `component/http.HttpRequest`（进程内 cgo），超时 60s。它的 Transport 不读代理环境变量，经 mixed-port 时传入 HTTP CONNECT 拨号器，订阅与 provider 预取共用；Parse 校验时内核为预取失败的 provider 与缺失的 GeoIP 现场下载，仍直连。UA 默认 `ClashMetaForAndroid/{version}`；用户可在 Add / Edit 页填自定义 UA，存入 `Subscription.userAgent`，经 PendingSnapshot 传到 Go `runFetchAndValid`：`effectiveUA = trim(userAgent) ?: currentUserAgent()`。非 2xx 或空 body 返回 `StellibertyCoreError`。订阅内容原样交给 mihomo 解析，app 侧不做 base64 / V2Ray 转换。
- **名字留空时自动命名**：Url 型允许名字留空（`enforceFieldValid` 放行）。fetch 响应的 `Content-Disposition` filename（Go 侧 `mime.ParseMediaType` 解析 RFC 5987，去掉 .yaml / .yml 后缀）经 `FetchResult.FileName` 回传；`commitPending(fallbackName)` 只在 commit 时 `pending.name` 仍为空时采用。用户输入（含深链 `name` 参数）优先，兜底链为 disposition > URL host > 调用方注入的默认名。更新订阅（isUpdate）保留原名。

## provider 缓存路径

http provider 的缓存一律放在工作目录的 `providers/` 下（`provider_paths.go`）：配置写了 `path` 时以 `/` 为根清理后保留其相对结构，`../` 与绝对路径都收进该目录；没写时用 `{proxies|rules}/<URL 哈希>`。导入预取、`validateTransform` 与运行时共用这一规则，运行时直接读到预取的文件，就绪前不必再下载。cmfa 构建跳过了 mihomo 的安全路径检查，这一步也把 ROOT 内核的写入限制在工作目录内。

运行时只能把配置文本交给内核：`runtime.go` 在套用 `--transform` 之后调用 `patchProviderPathsYAML`，按解析后的值（合并键、锚点已展开）算路径，再写回 YAML 节点。合并键带来的 provider 补成显式条目；别名指向的节点先复制再改，带锚点的原节点就地改。

## age 加密订阅

per-profile `ageSecretKey` 经 `PendingSnapshot` 传到 `fetchAndValid`。磁盘上保持加密，运行时解密：

- 导入校验：Kotlin 侧 fetch 前 `nativeSetAgeSecretKey`、fetch 后清空（processLock 保证串行），Go 侧在内存中解密校验，config.yaml 与 provider 文件按加密形态写入。
- 运行：Service 从 `SubscriptionStore` 读密钥，经 `MihomoRunner.start(ageSecretKey)` 加 `--age-secret-key`，`runtime.go` 在 `hub.Parse` 前调用 `age.SetGlobalSecretKeys`；ROOT attach 路径的进程已带密钥。
- 加密订阅对 app 不透明：`ConfigGenerator.readSubscriptionSecret` / `readSubscriptionMixedPort` 的行扫描读不到内容，使用默认值。
- 备份导出时解密为明文（与 PC 一致），从备份恢复后到下次更新前磁盘上是明文，见 `guides/backup.md`。
- 密钥在 设置 →「Age 密钥」中生成，调用 `stellibertyGenAgeKeyPair` / `stellibertyGenAgeHybridKeyPair`（X25519 / mlkem768-x25519）。

## 深链一键导入

`clash://install-config?url=&name=&update-interval=`（兼容 `clashmeta://`）由 ExternalImportActivity（透明 + noHistory + excludeFromRecents）承接：校验 url 为 http(s) 后带随机 nonce 转发 MainActivity，经 `deepLinkImport` 驱动 popUntil Main → pager 切到订阅 Tab → push 预填的 `Route.SubscriptionAddUrl`，停在等用户确认的状态，确认后走常规 Pending → APPLY 管线。两个防御点：

1. 用 nonce 去重：进程死亡恢复会重放旧 intent（跳过），任务存活而进程被杀时新深链可能随恢复态送达（接受），`savedInstanceState` 判空区分不了这两种情况。
2. 跳板是 exported 的，`intent.data` 先 `takeIf { isHierarchical }` 再取 query（非层级 URI 上 `getQueryParameter` 会抛异常）。

## imported/ 的换入顺序

imported/ 一律先拷后 rename：拷进 `commit.new/`，再两次 rename 换入，删除全部排在拷贝之后，失败时 `imported/{uuid}/` 保持完整（update 路径下它是唯一副本）。rename 只需父目录可写，与 root:root 残留无关。两次 rename 之间的窗口由 `commit.old.{uuid}` 兜底，启动时 `cleanupProcessing` 据此还原。
