<div align="center">

# Stelliberty Android

[![English](https://img.shields.io/badge/English-red?style=flat-square)](../../README.md)
&nbsp;
[![简体中文](https://img.shields.io/badge/简体中文-blue?style=flat-square)](README.zh-CN.md)

<br>

[![Android](https://img.shields.io/badge/Android-3DDC84?logo=android&logoColor=white&style=flat-square)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white&style=flat-square)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-GPL--3.0-blue?style=flat-square)](../../LICENSE)

</div>

<br>

Stelliberty 是基于 [Mishka](https://github.com/YuKongA/Mishka) 的安卓代理客户端，采用 [mihomo](https://github.com/MetaCubeX/mihomo) 内核，界面用 Jetpack Compose 和 [miuix](https://github.com/miuix-kotlin-multiplatform/miuix) 构建。

无需 Root 即可使用系统 VPN，也可以选择 ROOT TUN 或 ROOT TPROXY 接管流量。订阅、代理组、连接和分流设置都能在应用内管理。

<br>

---

## 导航

- [安装](#-安装)
- [快速上手](#-快速上手)
- [主要功能](#-主要功能)
- [常见问题](#-常见问题)
- [开发指南](#-开发指南)
- [发布工作流](#-发布工作流)
- [参与开发](#-参与开发)
- [许可证与致谢](#-许可证与致谢)

<br>

---

## 📦 安装

<sub>[↑ 回到导航](#导航)</sub>

从 [发布页面](https://github.com/Kindness-Kismet/stelliberty_android/releases) 下载 APK。

| 渠道 | 下载位置 | 适用场景 |
|---|---|---|
| 稳定版 | [最新稳定版](https://github.com/Kindness-Kismet/stelliberty_android/releases/latest) | 日常使用 |
| 开发版 | 发布页面中标记为预发布、版本带 `-betaN` 的条目 | 提前体验开发中的改动 |

系统要求为 Android 12 及以上。新手机选择 `arm64-v8a`；`x86_64` 安装包用于对应架构的设备和模拟器。

稳定版和开发版使用相同的应用包名与签名。覆盖安装时，安卓会检查版本编号；下一版本的开发版可能比当前稳定版更新。

<br>

---

## 🚀 快速上手

<sub>[↑ 回到导航](#导航)</sub>

1. 导入订阅链接、扫描二维码，或选择本地配置文件。
2. 选中要使用的订阅，在代理页面选择节点。
3. 在首页选择规则、全局或直连模式。
4. 启动代理并同意系统 VPN 授权；使用 ROOT 模式时，需要授予 Root 权限。

配置选项详见 [mihomo 官方文档](https://wiki.metacubex.one/config/)。

<br>

---

## ✨ 主要功能

<sub>[↑ 回到导航](#导航)</sub>

| 类别 | 功能 |
|---|---|
| 流量接管 | VPN、ROOT TUN、ROOT TPROXY、分应用白名单与黑名单、ROOT 热点流量处理 |
| 订阅管理 | 链接、文件、二维码导入，age 加密配置，定时更新，独立 User-Agent |
| 代理管理 | 代理组选择、延迟测试、提供者刷新、配置编辑 |
| 运行诊断 | 实时流量、连接列表、日志和 DNS 查询 |
| 自动化 | 快捷设置磁贴、开机自启、Wi-Fi 策略 |
| 界面 | 深浅色主题、动态取色、模糊效果、宽屏布局 |
| 数据管理 | 备份、恢复与 WebDAV |

<br>

---

## ❓ 常见问题

<sub>[↑ 回到导航](#导航)</sub>

### 必须有 Root 权限吗？

VPN 模式不需要 Root。ROOT TUN 和 ROOT TPROXY 需要 Root 权限，其中 TPROXY 还要求设备内核支持。

### 三种隧道模式有什么区别？

| 模式 | 接管方式 |
|---|---|
| VPN | 由安卓系统创建和管理 VPN 网卡 |
| ROOT TUN | 由代理内核创建 TUN 网卡并配置路由 |
| ROOT TPROXY | 通过防火墙规则和策略路由将流量转交给代理内核 |

### 为什么修改配置需要重启？

目前通过重启内核应用配置变更。规则、全局、直连控制的是分流行为，与隧道模式是两项不同的设置。

### 应用自带订阅吗？

不提供订阅，需要自行导入订阅或配置。项目提供客户端和代理内核。

<br>

---

## 🛠 开发指南

<sub>[↑ 回到导航](#导航)</sub>

### 前置依赖

| 工具 | 要求 |
|---|---|
| Python | 3.10 及以上 |
| Git | 用于获取子模块 |
| Android SDK | 安装命令行工具并接受 SDK 许可，SDK 配置见 [ProjectConfig.kt](../../android/buildSrc/src/main/kotlin/ProjectConfig.kt) |
| Go | 预构建脚本将最新稳定版下载到 `build/go/`，无需安装到系统 |
| JDK 与 Gradle 启动器 | 由预构建脚本下载 |

原生编译所需的 NDK 和 CMake 由 Android Gradle Plugin 解析。安卓依赖坐标和工具版本统一维护在 [libs.versions.toml](../../android/gradle/libs.versions.toml)。

本地和工作流使用相同的便携 Go 准备流程，下载包经过官方 SHA256 校验。日常构建直接复用已下载的工具链，不联网检查版本；运行 `python scripts/prebuild.py --refresh-go` 可检查并安装最新稳定版，编译器版本变化也会触发内核重编译。

### 架构

```text
android/app/src/main/kotlin/com/stelliberty/android/
├── ui/           Compose 页面、组件、导航与主题
├── viewmodel/    页面状态与用户操作
├── domain/       模型与仓库接口
├── data/         仓库实现、JSON 存储、内核接口与备份
├── platform/     安卓平台适配与服务控制
└── service/      VPN、ROOT、订阅与后台服务
android/app/src/main/cpp/                        JNI 与进程辅助代码
android/app/src/main/native/stelliberty_core/     Go 内核集成
third_party/                                    mihomo 与 scripta 子模块
scripts/                                        预构建与编译入口
```

页面通过参数接收依赖，ViewModel 依赖仓库接口，安卓专属行为由平台代码处理。项目约定见 [AGENTS.md](../../AGENTS.md)。

### 编译与验证

```bash
git clone --recurse-submodules https://github.com/Kindness-Kismet/stelliberty_android.git
cd stelliberty_android
python scripts/prebuild.py
python scripts/build.py --dev
```

| 命令 | 用途 |
|---|---|
| `python scripts/build.py compile --dev` | 只编译调试版 Kotlin，跳过原生内核构建 |
| `python scripts/build.py --dev` | 构建调试版 APK |
| `python scripts/build.py` | 构建正式 APK |
| `python scripts/build.py --abi x86_64` | 指定目标架构 |
| `python scripts/build.py --version 1.0.1-beta1` | 临时覆盖安装包版本，不修改源码中的版本号 |
| `python scripts/prebuild.py --refresh-geo` | 更新内置 GeoIP 资源 |
| `python scripts/prebuild.py --refresh-go` | 将便携 Go 更新到最新稳定版 |

安装包保存在 `build/apk/`。正式签名通过 `KEYSTORE_PATH`、`KEYSTORE_PASS`、`KEY_ALIAS`、`KEY_PASSWORD` 环境变量配置；本地生成的未签名正式包需要签名后才能安装。

仓库按目录和后缀设置白名单。工作流还会检查已跟踪文件，即使强制加入索引，白名单以外的文件也会被拦截。下载的启动器、JDK、Go 工具链、GeoIP、原生库、缓存和临时文件不进入 Git；真机采集的 Baseline Profile 继续跟踪，因为它无法靠下载重建。

<br>

---

## 🔖 发布工作流

<sub>[↑ 回到导航](#导航)</sub>

| 工作流 | 触发方式 | 产物 |
|---|---|---|
| [稳定版构建](../workflows/build-stable.yml) | `main` 分支版本号变化，或在 `main` 分支手动运行 | `vX.Y.Z`，标记为最新稳定版 |
| [开发版构建](../workflows/build-beta.yml) | `beta` 分支产品代码变化（跳过包含应用版本号或更新日志变更的推送），或在 `beta` 分支手动运行 | 下一补丁版本追加递增的 `-betaN`，标记为预发布 |
| [合并请求验证](../workflows/verify.yml) | 向 `beta` 或 `main` 提交合并请求 | 验证 Kotlin 编译，不读取发布凭据、不发布安装包 |

两条发布渠道都会为两种支持的六十四位架构生成经过签名的正式构建 APK。本地 `--dev` 选择的是调试构建，不代表开发版发布渠道。

发布前，在仓库的 Actions secrets 中配置：

| 名称 | 内容 |
|---|---|
| `KEYSTORE_BASE64` | 发布签名文件的 Base64 内容，不换行 |
| `KEYSTORE_PASS` | 签名文件密码 |
| `KEY_ALIAS` | 签名密钥别名 |
| `KEY_PASSWORD` | 签名密钥密码 |

两条渠道使用同一份签名。所有安装包上传完成后，发布条目才会公开。稳定版直接使用 [.github/CHANGELOG.md](../CHANGELOG.md)；开发版列出上一次发布基线之后的提交，没有基线的首个开发版使用当前更新日志。

[版本更新技能](../../.codex/skills/version-bump/SKILL.md)负责修改 `ProjectConfig.VERSION_NAME` 并重写本次更新日志：英文列表在前，分隔线居中，对应的简体中文列表在后。它不会自行升级依赖或发布版本。

<br>

---

## 📋 参与开发

<sub>[↑ 回到导航](#导航)</sub>

日常修改提交到 `beta`，稳定版通过审查后从 `beta` 合并到 `main`。

- 每次修改围绕一个明确目标，保留已有用户数据与配置语义。
- 应用文案同步维护英文、简体中文和繁体中文。
- 新增可交互控件时登记调试测试标识。
- 运行 `git diff --check` 和与改动匹配的编译或打包命令。
- 修改界面、服务、订阅、内核接口和原生构建前，阅读对应项目技能。

<br>

---

## 📄 许可证与致谢

<sub>[↑ 回到导航](#导航)</sub>

本项目使用 [GPL-3.0](../../LICENSE) 许可证，第三方项目继续遵循各自的许可证。

- [Mishka](https://github.com/YuKongA/Mishka) —— 直接上游
- [mihomo](https://github.com/MetaCubeX/mihomo) —— 代理内核，通过 [mihomo 子模块](https://github.com/YuKongA/mihomo)集成
- [miuix](https://github.com/miuix-kotlin-multiplatform/miuix) —— Compose 界面组件
- [scripta](https://github.com/YuKongA/scripta) —— 配置编辑器子模块
