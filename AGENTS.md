# Stelliberty

miuix + mihomo 的 Android 代理客户端。单模块 `:app`（`com.android.application`，AGP 9 内置 Kotlin，源码全在 `src/main`）+ `:baselineprofile`（`com.android.test`，只产 Baseline Profile 不进 APK）。UI 用 AndroidX Compose，miuix 走其 `-android` 发布件。

本文件是 agent 指南的入口，只讲**项目结构**与**跨子系统都成立的规范**——每次会话都会全量加载，
所以放不下的东西不该放这里。子系统各自的约束在 skill 里按需读，见末尾分派表。

## 工作规程

- 每次改动至少跑 `git diff --check` + 与变更匹配的编译验证。
- 首次编译或资源缺失时运行 `python scripts/prebuild.py`，恢复未跟踪的 Gradle 启动器、便携 JDK / Go 与 GeoIP。
- 编译一律走 `python scripts/build.py`。脚本默认编译 release，`--dev` 编译 debug；助手未指定构建类型时始终加 `--dev`。
- 改 Kotlin：`python scripts/build.py compile --dev`。验证 native / 打包：`python scripts/build.py --dev`。架构 `--abi`，默认 `arm64-v8a`。
- 单元测试在 `android/app/src/test/`，改到被测代码时跑 `python scripts/build.py gradle :app:testDebugUnitTest`。
- 新增 composable 后临时加 `composeCompiler { reportsDestination.set(layout.buildDirectory.dir("compose_reports")) }`，再 `python scripts/build.py gradle :app:compileDebugKotlin --rerun-tasks` 跑报告，确认 restartable 全部 skippable、0 unstable 参数（当前 132 个），验完删掉临时配置。
- `third_party/mihomo` 是 submodule、`third_party/scripta` 是 includeBuild 复合构建，改前先确认确需触及。
- 保留用户已有的未提交改动；不用破坏性 reset/checkout；不修改或输出 `local.properties`。
- 完成后先报告变更与验证结果。**未经用户明确授权，不执行 `git add`/`commit`，不创建或修改远程 PR**；用户要求更新版本号即包含按 `version-bump` 技能完成本次版本的本地提交，明确要求不提交时除外；要求撰写文案不代表授权执行这些操作。
- **禁止擅自 `git push`**：先说明全部待推送提交、验证结果、远端和目标分支，再取得明确确认。提交或创建 PR 的授权不包含推送，也不能借 `gh pr create` 隐式推送；会话中已确认且范围、目标未变的推送无需重复询问。
- **Commit 主题与正文、PR 标题与正文一律使用英文**。撰写或执行提交先阅读 `commit`，准备或创建、更新 PR 先阅读 `pr`；格式、范围与验证规则由对应技能维护。
- Git 使用目录与后缀白名单。新增编译输入须核对 `.gitignore`；可下载产物不跟踪，Baseline Profile 必须保留。

## 技术栈

Kotlin（AGP 9 内置，不加独立 kotlin 插件）。UI：Compose（经 miuix `-android` 件传递）+ miuix（含自带 NavDisplay）+ androidx navigationevent（预测性返回手势）。图标是 `ui/icon/` 下手写的 MingCute ImageVector，**没有** material-icons 依赖。数据：JSON 文件（kotlinx-serialization）+ Ktor + kotlinx-* + Koin。其他：quickie 扫码、hiddenapibypass 预测性返回、core-splashscreen。核心：mihomo（YuKongA fork + 本地补丁）。

**依赖版本与坐标唯一来源 = `android/gradle/libs.versions.toml`**（含 `[bundles]`），mihomo 版本在 `android/gradle.properties`。应用版本、包名和 SDK 在 `android/buildSrc/src/main/kotlin/ProjectConfig.kt`；应用稳定版只修改 `VERSION_NAME`，界面只展示 `BuildConfig.VERSION_NAME`。`scripta:editor` 经 `includeBuild("../third_party/scripta")` 引入，插件由 scripta 自己的 `pluginManagement` 解析。

**Compose 稳定性**走 [compose_compiler_config.conf](android/app/compose_compiler_config.conf)，**只保留实测起作用的条目**（加之前先跑报告确认确有 unstable 参数），新增 unstable 的三方/平台字段优先进该文件而非散落 `@Stable`。三条易踩：① 条目对**子类生效**——`androidx.lifecycle.ViewModel` 一行覆盖全部 ViewModel，其内部字段稳定性因此完全不影响 composable 参数；② FQN 须与实际依赖一致，包名写错时静默失配、不报错；③ 只认整行 `//` 注释，行尾注释会被当成 matcher 内容。

**注释写什么**：只写读代码看不出来的约束与原因，如内核 / 并发时序、选用当前写法的理由。复述下一行的标签、外部参考来源（「参考 xx example」）、版本沿革（「旧版…」「不再…」）都不写。`// === X ===` 用于给 200 行以上的文件分组**多个**声明。注释若是某条不变式的唯一记录，删除前先把约束落到代码或 skill 里。

## 代码地图

分层靠**包名**，跨层即普通包引用。约定（非 Gradle 强制）：`domain.model` 只放 `@Serializable` 模型、`domain.repository` 只放仓库接口，二者不引 android/compose/ktor。

```
scripts/        prebuild.py（子模块 + 便携 JDK / Go + GeoIP）/ build.py（编译与打包）/ gen_icons.py
third_party/    mihomo（submodule，YuKongA fork branch Mishka；附加补丁在 scripts/patches/）
                scripta（includeBuild 复合构建，YAML 编辑器；app 依赖 scripta:editor）
android/        Gradle 根（wrapper + settings.gradle.kts + buildSrc + 两个模块）
                Gradle 路径仍是 :app / :baselineprofile（名字被 targetProjectPath、CI 产物路径与文档引用着）
android/app/src/main/
├── kotlin/.../stelliberty/  App / MainActivity / StellibertyApplication（startKoin + 全局初始化）
│   ├── domain/{model,repository}
│   ├── data/{api（REST/WS + MihomoConnectionManager）,bridge（StellibertyCoreBridge）,store（JsonFileStore + SubscriptionStore + ProxySelectionStore + OverrideProfileStore + RuleOverrideStore + ProfileTransformWriter）,repository（*Impl + ProfileProcessor + OverrideJsonStore + SubscriptionProxyResolver）,backup}
│   ├── platform/  service/  viewmodel/  util/  di/（4 个 Koin 模块）
│   └── ui/{navigation,component,icon（手写 MingCute 矢量图）,platform,theme,screen,util}
├── res/values{,-zh-rCN,-zh-rTW}/   assets/（构建时下载 GeoIP）
├── cpp/  process_helper.c + stelliberty_jni.c + mihomo_wrapper.c + CMakeLists.txt
├── jniLibs/arm64-v8a/libmihomo.so   native/stelliberty_core/（Go cgo 源）
└── src/release/generated/baselineProfiles/（生成产物，需提交）
```

路由清单（`ui/navigation/Route.kt`，均实现 `NavKey`）、屏幕↔ViewModel 对应、`platform`/`ui.platform`/`service` 三个包内各组件的名字与职责都能从文件名读出，此处不复述；名字不自明的那些，约束写在下方对应条目里。

## 架构

```
StellibertyApplication.startKoin ─ Koin（dataModule + androidPlatformModule + androidAppModule + viewModelModule）
  MainActivity（Koin get 取图）→ App → AppNavigation → HorizontalPager(4 Tab) + NavDisplay(二级页)
    → Screen → ViewModel → domain.repository 接口 → data.repository.*Impl
        ├→ MihomoApiClient(Ktor HTTP) + MihomoWebSocket(WS) → mihomo 进程 127.0.0.1:9090
        └→ SubscriptionStore / ProxySelectionStore（files/mihomo/ 下的 JSON 文件）
```

**Koin**（4 模块按职责拆分，均在 `di/`）：`dataModule` = appScope、SubscriptionStore、ProxySelectionStore、OverrideJsonStore、SubscriptionProxyResolver、RuleLatencyTester、MihomoConnectionManager、AutoDelayTester、OverrideProfileStore、RuleOverrideStore、ProfileTransformWriter、`SubscriptionRepositoryImpl` / `OverrideProfileRepositoryImpl` / `ChainProxyRepositoryImpl` / `RuleOverrideRepositoryImpl` + 接口绑定、`factory { ProfileProcessor }`；`androidPlatformModule`（绑 `androidContext()`）= PlatformStorage、ProxyServiceController、AppListProvider、WifiPolicyController、BootStartManager、BackupManager、ProfileUpdateScheduler；`androidAppModule` = `single<ProfileFileManager>`；`viewModelModule` = 15 个 ViewModel（单 Activity 用 `single`）。**组合根注入**：MainActivity `get()` 取图后透传给 `App(...)`，屏幕保持参数化、不用 koinViewModel；仅需 Activity 上下文的 FilePicker / VPN 授权 launcher 不入 Koin。**repo 实现必配接口**，ViewModel 依赖接口；`ProfileProcessor` 需实体级方法故依赖具体类。

- **通信方案**：runtime（traffic/logs/connections/proxy select/provider 刷新）走 subprocess + Ktor REST + WS，三模式共用；订阅导入（fetch + provider prefetch + Parse）走 JNI in-process，由 [StellibertyCoreBridge](android/app/src/main/kotlin/com/stelliberty/android/data/bridge/StellibertyCoreBridge.kt) 调 libmihomo.so 的 cgo 导出。
- **状态桥接**：ProxyServiceBridge（全局 StateFlow + TunMode），Service 写、ViewModel 读。**进程模型**：单进程（VpnService 与 UI 同进程），ROOT 模式 mihomo 为独立 root 进程。
- **数据持久化**：订阅 JSON 文件（见「订阅数据」）+ PlatformStorage（简单偏好）+ StorageKeys（key 常量）+ OverrideJsonStore（`override.user.json` + `ConfigurationOverride`）；store 自带 `state: StateFlow` + `update(transform)`，Settings 三个切片 VM 共享同一实例。**OverrideJsonStore 的内存 state 是权威值**：`load()` 返回内存值不读盘，写入磁盘由 appScope 串行异步完成（排队期间被新值取代的快照直接放弃）。SubscriptionStore / ProxySelectionStore 同理：当前订阅与节点选择先发布、后异步写盘。因此 **Service / ProfileWorker / 各入口的 ProxyServiceController 必须用 Koin 单例，各自 `new` 会读到盘上旧值**——用户改完设置立刻启动就会用到改前的配置。
- **订阅管理**：Pending → Processing → Imported 三阶段沙箱，`ProfileProcessor` 编排 snapshot → fetchAndValid（JNI 一次完成 fetch + provider prefetch + Parse）→ commit；processLock 串行，profileLock 守护订阅列表与目录一致。
- **app 内下载一律走 mixed-port**：Stelliberty 自身永远绕过 TUN，直连境外资源极慢——这是「图标加载慢」的根因模式，**任何新增的外网下载都要走 mixed-port**。`SubscriptionProxyResolver.resolve()` 按「代理运行中 + 可解析 mixed-port」返回 proxy URL 或 null；订阅下载走 `resolveForSubscription(mode)`，由该订阅的 `UpdateProxyMode` 决定（Direct 直连，Core / SystemProxy 经 mixed-port，Android 没有系统代理）。订阅侧由 native glue 在 fetchAndValid 入口 `os.Setenv("HTTPS_PROXY"/"HTTP_PROXY")` defer Unsetenv，覆盖 fetch + provider prefetch + GeoIP 自动下载，processLock 串行保证 set/unset 并发安全。[IconLoader](android/app/src/main/kotlin/com/stelliberty/android/ui/platform/IconLoader.kt)：内存 LRU(64) → 磁盘缓存 → 网络（限流 3 并发 + 5s/15s 超时），失败 URL 负缓存 60s，磁盘读取/解码在 `Dispatchers.IO`，proxy 解析结果缓存 10s、随 URL 变化 close 旧建新。
- **GeoIP 预制**：构建时 DownloadGeoFilesTask 下载 geoip.metadb/geosite.dat/ASN.mmdb 到 assets，启动时提取到 `files/mihomo/geodata/`。JNI 路径用 `stellibertyCoreInit(geodataDir)` 把 mihomo 全局 homeDir 指到这里；subprocess runtime 按 `-d workDir` + symlink 复用同一份。
- **国际化**：英文 + 简体中文（zh-rCN）+ 繁体中文（zh-rTW，台湾用语：設定/檔案/匯入/連線/連接埠/快取/伺服器/金鑰/還原/套用/群組/逾時，非简转繁），Composable 用 `stringResource`、非 Composable 用 `context.getString`；日志英文，代码注释中文。**data 层拿不到资源**：会被用户看见的兜底值（如订阅自动命名的最后一环）由调用方按 locale 注入。data 层写字面量会丢掉 locale；为此把 Context 拉进去会打破分层。

## 订阅数据

与 PC 版同一数据契约：键名 PascalCase、枚举写序号、时间 ISO 8601、新建订阅 Id 为 32 位十六进制。文件都在 `files/mihomo/` 下：

- `subscriptions/subscriptions_list.json`（`{"Subscriptions":[...]}`，已导入订阅）、`subscriptions/selection_state.json`（`CurrentSubscriptionId`）、`proxies/selection_state.json`（订阅 Id → 代理组 → 节点）与 PC 同名同格式；`subscriptions/pending_list.json`（编辑中草稿，提交后移入列表）是 Android 专有的。
- [Subscription](android/app/src/main/kotlin/com/stelliberty/android/domain/model/Subscription.kt) 字段与 PC 一一对应。**PC 读取时拒绝未知键，新增字段前先确认 PC 已有同名字段**；Android 暂未实现的 PC 字段（覆写、链式代理等）原样读入、原样写回。
- [JsonFileStore](android/app/src/main/kotlin/com/stelliberty/android/data/store/JsonFileStore.kt)：列表与草稿走 `update`（先写盘再发布，提交时 imported/{id}/ 已换入，列表没写盘就被杀会被启动清理当孤儿删掉）；当前订阅与节点选择走 `updateLater`。解析失败时原文件改名 `.bak` 并标记 `loadFailed`，启动清理据此跳过孤儿目录删除。
- `LastUpdatedAt` 在每次获取成功后写入并清掉 `LastError` / `LastErrorAt`，更新失败时写入这两项（原因是英文技术描述）；自动更新闹钟以最近一次成功或失败（都缺失时取 `CreatedAt`）为起点，失败后顺延一个间隔再试；运行期只有存在经内核更新的订阅（或还没有订阅）时才补默认 mixed-port。

**三阶段流程**（ProfileProcessor）：

```
CREATE → Pending ✓, Imported ∅
  → APPLY（processLock 串行）：
      ① snapshot（profileLock 内）：query Pending + enforceFieldValid + prepareProcessing（清 processing/ + 复制 pending/{uuid}/ → processing/）
      ② fetchAndValid（锁外、可取消、JNI in-process）：Url 型 force=true 重新下载 config.yaml；File 型 force=false 跳过 fetch；两者均 prefetch provider（**并发度 5**、每源 60s 超时、无总时限）后 Parse 校验；httpProxy 由 SubscriptionProxyResolver 决定
      ③ commit（profileLock 内，`withContext(NonCancellable)` 原子）：一致性检查 → commitProcessingToImported（拷到 commit.new/ 再 rename 换入）→ 列表更新
  → 失败：cleanupProcessing（NonCancellable）；pending/ 与 imported/ 都不动，可 retry
  → RELEASE：删草稿条目 + pending/{uuid}/
PATCH（编辑已导入）→ APPLY → Imported 更新, Pending ∅
UPDATE（手动/自动）→ 等价 APPLY，snapshot 取自 Imported
DELETE → 列表、草稿与节点选择清理 + imported/{uuid}/ + pending/{uuid}/ 删除
```

**目录**：`files/mihomo/` 下 `subscriptions/`、`proxies/`（见上）、`geodata/`（共享 GeoIP + 符号链接）、`imported/{uuid}/`、`pending/{uuid}/`、`processing/`（临时校验沙箱，单例）、`runtime/{uuid}/`（ROOT 运行时沙箱）、`overrides/`（覆写列表与内容）、`rules/`（规则覆写与模板）、`profile.transform.json`（当前订阅的覆写、链式代理与规则覆写，启动前生成）、`override.user.json`（用户设置）、`override.run.json`（启动时合并 TUN fd + AppProxy + rootMode）。换入用的临时目录：`commit.new` / `commit.old.{uuid}`、`.restore` / `.restore-old`。

## 构建

mihomo 经 submodule 引入 YuKongA fork（branch `Mishka`），附加补丁由 `prebuild.py` 与 `build.py` 应用。Go 首次下载最新稳定版到 `build/go/`，后续复用缓存；更新工具链：`python scripts/prebuild.py --refresh-go`。Go 按 ABI 交叉编译，产物在 `android/app/src/main/jniLibs/<ABI>/`，APK 收集到 `build/apk/`。更新 GeoIP：`python scripts/prebuild.py --refresh-geo`。刷新 Baseline Profile：`python scripts/build.py gradle :app:generateReleaseBaselineProfile`（需 adb 连 arm64 真机，产物提交进仓库）。

任务本身的硬约束（GoBuildTask 三条、downloadGeoFiles、CMake 链接）在 `native-build` skill。

**发布渠道**：`build-stable.yml` 监听 `main` 分支的应用版本变化，手动运行也限定该分支；`build-beta.yml` 监听 `beta` 分支，自动运行时跳过包含应用版本号或更新日志变更的推送，按下一补丁版本递增 `-betaN`，手动触发可重新构建同一提交。两者经可复用的 `build.yml` 打包 `arm64-v8a` 与 `x86_64` 两种 ABI，均为签名 release；`--dev` 仍只表示本地 debug。两个安装包全部构建成功后上传到发布草稿，版本标签由最后公开发布的请求创建，准备阶段只计算版本号。发布逻辑直接维护在工作流，不另设发布脚本。

**应用版本**：`build.py --version` 通过 `stelliberty.versionName` 临时覆盖 APK 版本。`versionCodeFor` 按语义版本与 beta 序号编码，稳定版高于同版本的所有 beta，不能改用分支提交数量。发布版本与双语摘要用 `version-bump` 技能维护；`.github/CHANGELOG.md` 只保存本次版本，格式为英文列表、分隔线、对应中文列表。

## 全局约束

只放跨子系统都成立的。子系统各自的约束在对应 skill，见下方分派表。

**错误兜底**：用户面向异常走 `Throwable.describe()`（`message ?: simpleName ?: "Unknown error"`），避免 Ktor `ConnectException()` 等无参异常漏到 UI 显示 "null"。**类型化错误的 message 只写英文技术描述**（供日志），用户可见文案由 UI 层按类型映射（`SubscriptionViewModel.localizedMessage`）——data 层拿不到 locale。订阅 fetch 的状态码与空 body 检查在 Go 侧（`fetch.go`），只能返回英文字符串，故 `ProfileProcessor.translateCoreError` 按已知文案（`http status N` / `empty response body` / `validate config:` 前缀）还原成 typed `ImportError` / `ConfigValidationException`——**改 Go 那几条文案必须同步改这里**，漏改不报错，只是用户又看到英文原文。ViewModel 的 `error` 字段同理只存原因，「加载失败: 」这类前缀由屏幕 `stringResource` 拼。

**后台卡片隐藏**：`HIDE_TASK_CARD` 由 `MainActivity` 读取并经 `ActivityManager.AppTask.setExcludeFromRecents()` 应用，运行时切换经 callback 透传即时生效。写成 manifest `android:excludeFromRecents="true"` 会失去用户可切换语义；App/屏幕暴露 callback、不直接调 Android API。当前实现依赖单 Activity task（`appTasks.firstOrNull()`）；若引入 document/multi-task 入口，必须改为按当前 `taskId` 匹配。

**其他**：通知 id 按用途分区（1..99 固定前台通知 / 100..999 更新结果环形复用 / `0x10000+` per 订阅进度，散列 uuid 低 16 位），新增通知按区取号——不分区时 uuid 散列出的 id 可能落到 Wi-Fi 监控前台通知或结果通知上，特定 uuid 会把别人的通知覆盖再取消掉；四个 `specialUse` 前台服务都要带 `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`（分发审核会看）；`Activity configChanges=uiMode` 防深浅色切换重建；MainActivity 用 `launchMode="singleTop"`，磁贴长按、`am start` 等外部入口复用栈顶实例，否则会叠出第二个实例与同一批 `single` ViewModel 争状态；预测性返回走 HiddenApiBypass 反射 `setEnableOnBackInvokedCallback`；`network_security_config.xml` 全局 `cleartextTrafficPermitted=true`（订阅源常用 HTTP；CMFA 因 fetch 在 Go 侧绕过 Java 网络栈而无需此设置，Stelliberty 的 Ktor 走 OkHttp 必须显式放行）；`jniLibs.useLegacyPackaging = true` 让 libmihomo.so 解压到 nativeLibraryDir，同时在 APK 内保持压缩存储（实测 arm64 71 MB → 25 MB），是净收益而非体积代价。

## 子系统约束按需读

各子系统的约束拆成了 skill，**改对应代码前先读**：其中多数违反时编译器不报错，只表现为连不上、界面错位或数据串号。

| 要改什么 | 先读 | 里面有什么 |
|---|---|---|
| `ui/` 下任何文件 | `ui-design` | miuix 用法、页面骨架、毛玻璃、宽屏与刘海、Dialog / BottomSheet、主题与语义色、Compose 状态形状与帧率、代理页与首页的页面级决策 |
| `service/` `platform/` 下的 Service、Controller、override、iptables | `proxy-service` | 启动校验单点、幂等串行、状态回报、隧道三模式、分应用代理、Wi-Fi 策略、override 注入、ROOT 与 iptables |
| `data/repository/` 的 ProfileProcessor、`ProfileWorker`、`data/backup/` | `subscription` | 三阶段管线、协程锁、取消语义、深链、age 加密、活跃订阅、自动更新、备份恢复 |
| `data/api/` 或在 ViewModel 里接 repository | `mihomo-api` | client 所有权、切换时取消旧协程、WS 重连、哪些接口在 embed mode 下不可用、测速与速率差分 |
| `cpp/` `native/` `buildSrc/`、加 CLI flag、Baseline Profile | `native-build` | 库加载顺序、SONAME、panic 边界、内存归属、GoBuildTask 三条硬约束、profile 采集 |
| 调试 / 验证应用 | `debug-app` | debug 指令、测试 ID 与模拟点击、截图、设备与日志 |
| 提升应用版本、准备版本摘要 | `version-bump` | 发布基线、应用版本、双语更新日志与验证 |
| 撰写提交文案、创建本地提交、准备推送 | `commit` | 英文提交格式、变更范围、验证、推送前明确确认 |
| 撰写 PR 文案、创建或更新 PR | `pr` | 英文标题与正文、分支比较、验证、远程操作与推送授权 |

skill 的 description 会自动匹配请求，但**匹配不保证命中**（「把这个按钮改成蓝色」未必触发），
所以上表留着当第二道保险——不确定就先读。

**skill 怎么写**：每条只写当前约束与一句原因，用肯定句直接写该怎么做。代码变化时就地改写对应条目，失效的直接删除；修复经过（出过什么 bug、报过什么错、调试时的实测数据）写进提交说明。示例使用占位数据（`Example`、`<device_id>`）。

## AI 助手配置目录

`.claude/skills/` 与 `.codex/skills/` 内容完全相同，分别供不同的 AI 工具读取，修改时两边同步，`diff -r .claude/skills .codex/skills` 应无输出。脚本路径统一写 `.claude/skills/…`。`CLAUDE.md` 只有一行 `@AGENTS.md`，规范只维护本文件这一份。

`.pi/` 只有 `settings.json` 进入版本控制，它把 skill 目录指向 `.claude/skills`。
