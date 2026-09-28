<div align="center">

# Stelliberty Android

[![English](https://img.shields.io/badge/English-red?style=flat-square)](README.md)
&nbsp;
[![简体中文](https://img.shields.io/badge/简体中文-blue?style=flat-square)](.github/docs/README.zh-CN.md)

<br>

[![Android](https://img.shields.io/badge/Android-3DDC84?logo=android&logoColor=white&style=flat-square)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white&style=flat-square)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-GPL--3.0-blue?style=flat-square)](LICENSE)

</div>

<br>

Stelliberty is a native Android proxy client powered by [mihomo](https://github.com/MetaCubeX/mihomo), with a Jetpack Compose interface built on [miuix](https://github.com/miuix-kotlin-multiplatform/miuix).

Use Android VPN without root, or choose ROOT TUN and ROOT TPROXY for privileged traffic capture. Manage subscriptions, proxy groups, connections, and routing from one app.

<br>

---

## Navigation

- [Installation](#-installation)
- [Quick Start](#-quick-start)
- [Features](#-features)
- [FAQ](#-faq)
- [Development Guide](#-development-guide)
- [Release Workflow](#-release-workflow)
- [Contributing](#-contributing)
- [License and Credits](#-license-and-credits)

<br>

---

## 📦 Installation

<sub>[↑ Back to Navigation](#navigation)</sub>

Download an APK from the [Releases page](https://github.com/Kindness-Kismet/stelliberty_android/releases).

| Channel | Where to find it | Intended use |
|---|---|---|
| Stable | [Latest stable release](https://github.com/Kindness-Kismet/stelliberty_android/releases/latest) | Regular use |
| Beta | Releases marked **Pre-release**, with a `-betaN` version | Try upcoming changes |

Android 12 or later is required. Choose `arm64-v8a` for current phones or `x86_64` for devices and emulators with that architecture.

Stable and beta packages share the same application ID and signing key. Android checks the version code when installing an update; a beta of the next version may be newer than the current stable release.

<br>

---

## 🚀 Quick Start

<sub>[↑ Back to Navigation](#navigation)</sub>

1. Import a subscription URL, scan a QR code, or select a local configuration file.
2. Select the active subscription and choose nodes on the proxy page.
3. Choose Rule, Global, or Direct mode on the home page.
4. Start the proxy and approve the Android VPN permission. ROOT modes require root access instead.

For configuration options, see the [mihomo documentation](https://wiki.metacubex.one/en/config/).

<br>

---

## ✨ Features

<sub>[↑ Back to Navigation](#navigation)</sub>

| Area | Capabilities |
|---|---|
| Traffic capture | VPN, ROOT TUN, ROOT TPROXY, per-app allowlists and blocklists, ROOT hotspot handling |
| Subscriptions | URL, file, and QR import; age-encrypted configurations; scheduled updates; per-subscription User-Agent |
| Proxy management | Group selection, latency tests, provider refresh, configuration editing |
| Diagnostics | Live traffic, connections, logs, and DNS queries |
| Automation | Quick Settings tile, start on boot, and Wi-Fi policies |
| Appearance | Light and dark themes, dynamic colors, blur effects, and wide-screen layouts |
| Data | Backup, restore, and WebDAV support |

<br>

---

## ❓ FAQ

<sub>[↑ Back to Navigation](#navigation)</sub>

### Do I need root?

VPN mode works without root. ROOT TUN and ROOT TPROXY need root permission; TPROXY also requires support from the device kernel.

### How do the tunnel modes differ?

| Mode | Traffic capture |
|---|---|
| VPN | Android creates and manages the VPN interface |
| ROOT TUN | The core creates a TUN interface and configures routing |
| ROOT TPROXY | Firewall rules and policy routing redirect traffic to the core |

### Why does a configuration change require a restart?

Configuration changes currently take effect by restarting the core. Rule, Global, and Direct describe routing behavior; they are separate from the tunnel mode.

### Does the app provide a subscription?

No. Import your own subscription or configuration. The project supplies the client and its proxy core.

<br>

---

## 🛠 Development Guide

<sub>[↑ Back to Navigation](#navigation)</sub>

### Prerequisites

| Tool | Requirement |
|---|---|
| Python | 3.10 or later |
| Git | Required for submodules |
| Android SDK | Install command-line tools and accept SDK licenses; SDK settings are in [ProjectConfig.kt](android/buildSrc/src/main/kotlin/ProjectConfig.kt) |
| Go | The prebuild script downloads the latest stable release into `build/go/`; no system installation is needed |
| JDK and Gradle launcher | Downloaded by the prebuild script |

Android Gradle Plugin resolves the NDK and CMake used for native compilation. Android dependency coordinates and tool versions are maintained in [libs.versions.toml](android/gradle/libs.versions.toml).

Local builds and CI use the same portable Go setup, with archives verified against the official SHA256 checksums. Regular builds reuse the downloaded toolchain without checking the network. Run `python scripts/prebuild.py --refresh-go` to check for and install the latest stable release; a compiler version change also rebuilds the native core.

### Architecture

```text
android/app/src/main/kotlin/com/stelliberty/android/
├── ui/           Compose screens, components, navigation, and themes
├── viewmodel/    Screen state and user actions
├── domain/       Models and repository interfaces
├── data/         Repositories, JSON stores, core API clients, and backup
├── platform/     Android integration and service control
└── service/      VPN, ROOT, subscriptions, and background services
android/app/src/main/cpp/                        JNI and process helpers
android/app/src/main/native/stelliberty_core/     Go core integration
third_party/                                    mihomo and scripta submodules
scripts/                                        Prebuild and build entry points
```

Screens receive dependencies through parameters. ViewModels depend on repository interfaces, and platform code owns Android-specific behavior. See [AGENTS.md](AGENTS.md) for project conventions.

### Build and Verify

```bash
git clone --recurse-submodules https://github.com/Kindness-Kismet/stelliberty_android.git
cd stelliberty_android
python scripts/prebuild.py
python scripts/build.py --dev
```

| Command | Result |
|---|---|
| `python scripts/build.py compile --dev` | Compile debug Kotlin without rebuilding the native core |
| `python scripts/build.py --dev` | Build a debug APK |
| `python scripts/build.py` | Build a release APK |
| `python scripts/build.py --abi x86_64` | Build for a selected architecture |
| `python scripts/build.py --version 1.0.1-beta1` | Override the packaged version without editing source metadata |
| `python scripts/prebuild.py --refresh-geo` | Refresh bundled GeoIP resources |
| `python scripts/prebuild.py --refresh-go` | Update portable Go to the latest stable release |

APKs are collected in `build/apk/`. Release signing uses `KEYSTORE_PATH`, `KEYSTORE_PASS`, `KEY_ALIAS`, and `KEY_PASSWORD` environment variables. An unsigned local release build must be signed before installation.

The repository uses a directory and extension allowlist. CI also rejects tracked files outside that allowlist, including files added with force. Downloaded launchers, JDKs, Go toolchains, GeoIP data, native binaries, caches, and temporary files stay outside Git. Collected Baseline Profiles remain tracked because a download cannot recreate them.

<br>

---

## 🔖 Release Workflow

<sub>[↑ Back to Navigation](#navigation)</sub>

| Workflow | Trigger | Output |
|---|---|---|
| [Stable Build](.github/workflows/build-stable.yml) | Version change on `main`, or manual dispatch from `main` | `vX.Y.Z`, marked as the latest stable release |
| [Beta Build](.github/workflows/build-beta.yml) | Product changes on `beta` excluding pushes that change the application version or changelog, or manual dispatch from `beta` | Next patch version with an increasing `-betaN` suffix, marked as a pre-release |
| [Verify Pull Request](.github/workflows/verify.yml) | Pull requests targeting `beta` or `main` | Kotlin compilation without release credentials or publishing |

Both release channels build signed **release** APKs for all two supported 64-bit architectures. The local `--dev` flag selects a debug build and does not select the beta release channel.

Configure these repository Actions secrets before publishing:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | Release keystore encoded as Base64 without line wrapping |
| `KEYSTORE_PASS` | Keystore password |
| `KEY_ALIAS` | Signing key alias |
| `KEY_PASSWORD` | Signing key password |

Use the same key for both channels. APK uploads finish before a release becomes public. Stable releases use [.github/CHANGELOG.md](.github/CHANGELOG.md); beta releases list commits since the previous release baseline. The first beta uses the current changelog if no baseline exists.

The [version-bump skill](.codex/skills/version-bump/SKILL.md) updates `ProjectConfig.VERSION_NAME` and rewrites the current changelog: English bullets first, a separator, then matching Simplified Chinese bullets. It does not raise dependency versions or publish releases on its own.

<br>

---

## 📋 Contributing

<sub>[↑ Back to Navigation](#navigation)</sub>

Submit changes to `beta`. Promote reviewed changes from `beta` to `main` for a stable release.

- Keep each change focused and preserve existing user data and configuration behavior.
- Maintain English, Simplified Chinese, and Traditional Chinese app strings together.
- Register new interactive controls in the debug test ID system.
- Run `git diff --check` and the relevant compile or package command.
- Read the matching project skill before changing UI, services, subscriptions, core APIs, or native builds.

<br>

---

## 📄 License and Credits

<sub>[↑ Back to Navigation](#navigation)</sub>

This project is licensed under [GPL-3.0](LICENSE). Third-party projects retain their own licenses.

- [mihomo](https://github.com/MetaCubeX/mihomo) — proxy core; integrated through the [mihomo submodule](https://github.com/YuKongA/mihomo)
- [miuix](https://github.com/miuix-kotlin-multiplatform/miuix) — Compose UI components
- [scripta](https://github.com/YuKongA/scripta) — configuration editor submodule
