# JNI 与 .so

libmihomo.so（cgo c-shared，arm64 约 71MB）同时承担 JNI 导出与 `mihomoEntry(argc, argv)` 运行时入口；libmihomo_runner.so（C PIE，约 6KB）由 MihomoRunner fork+exec 后 dlopen 前者并调用 mihomoEntry。一份 mihomo 代码服务两条路径。

## 五条硬约束

1. **加载顺序**：`System.loadLibrary("mihomo")` 先于 `loadLibrary("stelliberty_jni")`，后者依赖前者导出的符号。
2. **显式 SONAME**：cgo c-shared 默认不写 SONAME，消费方会把构建期绝对路径写进 DT_NEEDED，运行时报 `UnsatisfiedLinkError`。GoBuildTask 的 `-extldflags=-Wl,-soname,libmihomo.so` 与 CMake `IMPORTED_SONAME` 保持一致。
3. **PIE wrapper**：libmihomo_runner.so 读 `/proc/self/exe` 定位同目录 → dlopen → dlsym `mihomoEntry` → 透传 argv。新 CLI flag 同步注册到 `stelliberty_core/runtime.go` 的 `flag.NewFlagSet`（`ExitOnError` 拦截未注册的 flag）；`RootProcessScript` 根据 `/proc/<pid>/exe` 与包安装路径识别所属进程。
4. **`*C.char` 由 Go 侧释放**：`//export` 返回的字符串内存属于 Go runtime，C 侧调用 `stellibertyFreeString()`（`free()` 会破坏 cgo 堆）。
5. **`//export` 收住 panic**：JNI 在进程内运行，panic 逸出 cgo 边界会终止整个应用。返回字符串的导出函数走 `guardString`，降级为 `"error: "`，覆盖范围限于同一 goroutine。

## 订阅变换

`stellibertyValidateTransform`、`stellibertyChainProxyContext`、`stellibertyRuleContext` 与运行时 `--transform` 共用 `overrides.Transform`：先按顺序套覆写，再处理链式代理，最后合并规则覆写。JNI 传变换文件路径，文件内容由 Go 读取，避免 `GetStringUTFChars` 的改进 UTF-8 改坏补充平面字符；provider 校验期间设置 age 全局密钥并在返回时清空，调用方持 processLock。校验返回 `{"hasCycle":bool}`，失败仍走 `"error: "` 前缀。

引擎位于 `stelliberty_core/overrides/`：链式代理在 `chains.go`、规则覆写在 `rules.go`，规则与 PC 一致，改动时同步核对 PC 实现；YAML 展开别名时检查循环及节点上限；JavaScript 走 goja，执行限时 2 秒、输出上限 1 MiB。Go 模块的 `godebug default=go1.20` 保持 mihomo 运行时默认行为，实际工具链仍由构建脚本选定。

## 地理数据解压

`stellibertyExtractXzAsset` 按 zip entry 直接从 APK（`applicationInfo.sourceDir`）读取 `assets/<文件名>.xz` 解到目标路径，原子替换与并发由 `ProfileFileOps.extractGeodata` 负责。解码用随 mihomo 链接的 `ulikunitz/xz`，不增加 .so 体积；实测比 ART 上的 Java 解码器快约 3 倍。

## fork+exec

Android `ProcessBuilder` fork 后会关闭全部非标准 fd，VPN 模式因此用 JNI `fork()+exec()`（`process_helper.c`）继承 TUN fd。fork 与 exec 之间只调用 async-signal-safe 函数，子进程分支里打日志会死锁。

## 上游 patch

**fd 模式 `forwarderBindInterface` 保持 `true`**：上游 `e38aa82a` 改动了这个取值，在 gvisor stack + VpnService fd 下经 fd 的流量不通。延迟测试由 mihomo 直接 dial、不经 fd，反映不出这个问题。sing-tun 源码里看似只有 `stack_system` 读取该标志，gvisor 路径同样受影响。fork 第 5 patch 在 fd 模式保留 `true`；每次 rebase 上游后确认 `listener/sing_tun/server.go` 的 `forwarderBindInterface = true` 仍然生效，前提是新代码已进 .so（见 `guides/gradle.md` 的 `replacedModuleSources`）。

## 启动就绪契约

- `runtime_probe.go` 通过 `route.Register` 注册 `/stelliberty/runtime`，沿用控制器 secret 鉴权。初始化期间返回 503、完成后返回 200，响应体都是 `{"pid":<pid>}`：应用只把目标进程的 200 当作就绪，凭 503 里的进程号确认仍在加载的是自己，其他进程的应答按端口被占用处理。
- `hub.Parse` 返回、启用的 TUN 确认创建成功并注册退出信号后，才发布就绪状态；配置重载与退出期间撤销就绪。状态使用原子变量，与 HTTP 协程同步。TUN 初始化失败时按启动失败处理。
