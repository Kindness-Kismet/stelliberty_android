# Gradle 任务约束

## downloadGeoFiles

`downloadGeoFiles` 设为 `outputs.upToDateWhen { false }`，每次被点名都拉取最新版：上游 `latest` tag 原地重发布，URL 与本地文件都保持不变，Gradle 无从判断更新。它没有下游依赖，只在被点名时运行。任务要求：URL 表是 `@Input`；连接与读取都设超时；响应须为 200 且体积超过下限（挡住被写成 `geoip.metadb` 的 404 / 限流页面）；先写 `.part` 再 rename。它的 `@OutputDirectory` 是 `src/main/assets`，同时也是 `mergeAssets` 的输入，因此对 `merge*Assets` 声明 `mustRunAfter`，两者才能出现在同一次调用里。

## GoBuildTask

应用只构建 `arm64-v8a` 与 `x86_64`，脚本参数、NDK 工具链映射与发布矩阵保持一致。

发布工作流使用 GitHub 缓存：工具链按周刷新，GeoIP 按天刷新且不跨日期回退；Go 缓存按 ABI、工具链、依赖与源码分键。Gradle 缓存按 ABI、周与提交保存，回退到同架构缓存；任务输入决定是否复用 `GoBuildTask` 的库与头文件，拉取请求只恢复缓存。

主仓库的 `scripts/patches/mihomo-*.patch` 保存内核附加修正，`prebuild.py` 与 `build.py` 在编译前幂等应用；子模块里的这些差异是补丁结果，不能当作无关改动丢弃。补丁基于锁定的子模块提交，更新提交时同步核对补丁。

`prebuild.py` 首次下载最新稳定版 Go 到 `build/go/` 并校验官方 SHA256，日常构建离线复用；`--refresh-go` 检查更新，本地与发布工作流共用这套流程。`go.mod` 的兼容级别不能当作实际编译器版本。

编译入口为 Gradle 注入便携 Go 的 `GOROOT` / `PATH` 和 `GOTOOLCHAIN=local`。实际版本通过 `stelliberty.goVersion` 传入 `GoBuildTask` 的任务输入，升级编译器必须触发内核重编译。

[GoBuildTask](../../../../android/buildSrc/src/main/kotlin/GoBuildTask.kt) 产出 `libmihomo.so`（CGO_ENABLED=1，NDK clang 取自 `androidComponents.sdkComponents.ndkDirectory`）。三条硬约束：

- **mihomo submodule 整棵声明进 `replacedModuleSources`**：它经 go.mod `replace` 引入，被编译的代码大多在那里而非 `goSourceDir`。声明完整，rebase 或改 patch 后任务才会重跑 `go build`，否则继续产出陈旧的 .so，与「patch 没生效」难以区分。`mihomo.version` 是 gradle.properties 里的手写字面量，不能充当变更信号。过滤用反向排除：`component/ca` 用 `go:embed` 嵌入 `.crt`，扩展名白名单容易漏掉后缀，多收只多一次重建。
- **`libmihomo.h` 与 .so 一起声明为任务输出**：它由 c-shared 一并生成，供 CMake 的 `target_include_directories` 使用；声明为输出才能免于 stale-output 清理（被删时 CMake 报 `No such file`）。
- **`-buildvcs=false`**，与 `-trimpath` 同为可复现构建服务：VCS stamp 让产物随提交变化，并且在没有 git 或仓库属主不匹配的容器里会构建失败。

## CMake

CMake `dependsOn(buildMihomo)`，产出两个链接 libmihomo.so 的轻量库（IMPORTED + IMPORTED_SONAME）：`libmihomo_runner.so`（PIE wrapper）与 `libstelliberty_jni.so`（JNI 桥）。
