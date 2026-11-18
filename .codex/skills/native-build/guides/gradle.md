# Gradle 任务约束

## downloadGeoFiles

`downloadGeoFiles` 设为 `outputs.upToDateWhen { false }`，每次被点名都拉取最新版：上游 `latest` tag 原地重发布，URL 与本地文件都保持不变，Gradle 无从判断更新。它没有下游依赖，只在被点名时运行。任务要求：URL 表是 `@Input`；连接与读取都设超时；响应须为 200 且体积超过下限（挡住被写成 `geoip.metadb` 的 404 / 限流页面）；下载后压成 `<文件名>.xz`（等同 `xz -9e`，字典取文件大小以压低运行时解压内存），先写 `.part` 再 rename，并删除 assets 里同名的未压缩文件。app 声明 `noCompress += "xz"`，避免已压缩的数据再被 deflate 一遍。它的 `@OutputDirectory` 是 `src/main/assets`，同时也是 `mergeAssets` 的输入，因此对 `merge*Assets` 声明 `mustRunAfter`，两者才能出现在同一次调用里。

## GoBuildTask

应用只构建 `arm64-v8a` 与 `x86_64`，脚本参数、NDK 工具链映射与发布矩阵保持一致。

build 的 `--jobs` 默认 12，范围 1～64，实际命令透传 `--max-workers=N`。GoBuildTask 使用 `go build -p=1`，CMake 编译与链接共享单槽 Ninja pool；各 Native 任务在一个 Gradle worker 内执行，避免嵌套任务池放大总并发。

发布工作流使用 GitHub 缓存：工具链按周刷新，GeoIP 按天刷新且不跨日期回退；Go 缓存按 ABI、工具链、依赖与源码分键。Gradle 缓存按 ABI、周与提交保存，回退到同架构缓存；任务输入决定是否复用 `GoBuildTask` 的库与头文件，拉取请求只恢复缓存。

内核子模块锁定 YuKongA/mihomo 的 `Mishka` 原始提交，独有修复存放在 `scripts/build_support/patches/mihomo-*.patch`。`build_support.patching` 按文件名排序应用：`prebuild.py` 在同步子模块后调用，`build.py` 每次编译前调用；反向检查通过表示已生效，正向检查失败则停止并提示补丁名。子模块工作区中的补丁结果是正常状态，不能当作无关改动丢弃。

独有修复：`hub/route/common.go` 仅在 `RawPath` 非空时解码路由参数，避免节点名中的转义文本被二次解码。更新上游时保留这项语义，删除上游已吸收的补丁，并更新 `mihomo.version`、应用 Go 模块依赖与子模块指针；应用前验证干净源码，重复执行验证幂等性，两个 ABI 都完成完整构建。

`rules/provider/provider.go` 对独立 `payload:` / `rules:` 列表头下的简单 ASCII 标量直接取值，减少大型规则集的逐行 YAML 解析开销；转义、标签、别名与复杂语法仍走 YAML 解析器。维护补丁时核对规则内容、复杂语法与空值处理一致。

HTTP domain / ipcidr 规则源达到 64 KiB 时，在 `MihomoRunner` 传入的 `--rule-cache-dir` 保存匹配结构，MRS 源直接使用其原有结构。缓存核对版本、行为、格式及原文和匹配结构的 SHA-256，失效或损坏时重新解析原始规则；规则语义或序列化格式变化时递增 `ruleCacheMagic` 版本。目录总量限 32 MiB，写入后按保存时间淘汰较早的缓存。

GeoSite succinct 匹配器在同一目录的 `geosite/` 下缓存完整域名集合、正则与关键词及规则计数；以 GeoSite 原文 SHA-256、loader 与筛选条件取键，命中时跳过原始列表解码。缓存核对版本与内容 SHA-256，总量限 16 MiB；格式与语义变化时递增 `domainMatcherCacheMagic`。

GeoSite 内存列表与匹配器同样按源内容和 loader 分版本，源变更时释放旧版本；另一进程可更新共享数据库。列表加载前后核对源内容，发生变化时丢弃结果并重试一次，避免把旧列表保存到新源的缓存键下。

`DomainSetBuilder` 的普通 ASCII 域名直接校验并反转，复杂通配符与 Unicode 使用原语法；匹配集合的二进制读写使用整段数组，格式与 MRS 保持一致。

配置应用期间通过 `PauseAutoHealthChecks` 等待自动测速，配置与规则加载完成后恢复；手动测速即时执行。

`prebuild.py` 首次下载最新稳定版 Go 到 `build/go/` 并校验官方 SHA256，日常构建离线复用；`--refresh-go` 检查更新，本地与发布工作流共用这套流程。`go.mod` 的兼容级别不能当作实际编译器版本。

编译入口为 Gradle 注入便携 Go 的 `GOROOT` / `PATH` 和 `GOTOOLCHAIN=local`。实际版本通过 `stelliberty.goVersion` 传入 `GoBuildTask` 的任务输入，升级编译器必须触发内核重编译。

[GoBuildTask](../../../../android/buildSrc/src/main/kotlin/GoBuildTask.kt) 产出 `libmihomo.so`（CGO_ENABLED=1，NDK clang 取自 `androidComponents.sdkComponents.ndkDirectory`）。三条硬约束：

- **mihomo submodule 整棵声明进 `replacedModuleSources`**：它经 go.mod `replace` 引入，被编译的代码大多在那里而非 `goSourceDir`。声明完整，更新源码后任务才会重跑 `go build`，否则继续产出陈旧的 .so，与「修复没生效」难以区分。`mihomo.version` 是 gradle.properties 里的手写字面量，不能充当变更信号。过滤用反向排除：`component/ca` 用 `go:embed` 嵌入 `.crt`，扩展名白名单容易漏掉后缀，多收只多一次重建。
- **`libmihomo.h` 与 .so 一起声明为任务输出**：它由 c-shared 一并生成，供 CMake 的 `target_include_directories` 使用；声明为输出才能免于 stale-output 清理（被删时 CMake 报 `No such file`）。
- **`-buildvcs=false`**，与 `-trimpath` 同为可复现构建服务：VCS stamp 让产物随提交变化，并且在没有 git 或仓库属主不匹配的容器里会构建失败。

`-extldflags` 带 `-z,pack-relative-relocs`，把约 8 MB 的 `.rela.dyn` 换成 RELR；AGP 默认的 NDK 不会自动开启。系统从 API 30 起支持，minSdk 降到 30 以下时必须去掉，否则库在旧系统上加载失败。

## CMake

CMake `dependsOn(buildMihomo)`，产出两个链接 libmihomo.so 的轻量库（IMPORTED + IMPORTED_SONAME）：`libmihomo_runner.so`（PIE wrapper）与 `libstelliberty_jni.so`（JNI 桥）。
