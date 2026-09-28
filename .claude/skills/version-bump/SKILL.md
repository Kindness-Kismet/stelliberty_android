---
name: version-bump
description: 更新 Stelliberty Android 应用版本号，并按上一发布版本到当前最终状态重写中英文更新日志。用户要求提升版本、更新版本号或准备发布版本时使用；不处理依赖升级或自动发布。
---

# 版本更新

## 版本来源与范围

- 应用版本唯一来源：`android/buildSrc/src/main/kotlin/ProjectConfig.kt` 的 `VERSION_NAME`，只写稳定版号。
- 稳定版格式 `主版本.次版本.补丁号`，默认补丁号加一；用户指定目标时用指定值，且须高于当前版本。
- 主版本 ≤ 20，次版本与补丁号 ≤ 99，这是 Android 版本编号的编码边界。
- `-betaN` 由开发版工作流计算，经 `build.py --version` 传入。
- `versionCode` 由应用版本计算，稳定版高于同版本的全部 beta。
- 本技能只改版本号与更新日志；依赖、子模块、远程分支、标签与发布属于其他任务。
- 本地提交按用户当次授权执行；推送前说明提交、远端和分支，并取得明确确认。

## 1. 确认工作区和发布基线

先看 `git status --short --branch`、`git diff`、`git diff --cached`，保留用户已有改动。

当前版本以 `HEAD` 的版本文件为准。版本号或更新日志已修改且明确属于本次任务时沿用；归属不清时，只就影响发布范围的部分提问。

通过版本文件的变更历史定位上一发布状态：

```bash
git log --follow -p -- android/buildSrc/src/main/kotlin/ProjectConfig.kt
```

候选基线须确实把版本改为当前值，且位于当前历史中。标签仅用于交叉核对。首个版本可用引入当前版本常量的提交；有回滚或多个候选时，先确认基线再生成摘要。

## 2. 重建最终变化

同时阅读 `BASELINE_COMMIT..HEAD` 的提交与最终差异，并纳入用户明确要求随本次发布交付的工作区改动。

- 同一功能的多次修改合并为最终结果；新增后又删除、改后复原的内容略去。
- 只写用户可感知的变化，格式化、依赖坐标与实现细节略去。
- 内容如实：发布状态、数据与验证结果只写已确认的。
- 确认问题已完整解决、且获得提交授权时，才使用关闭问题的引用。

## 3. 重写更新日志

目标文件 `.github/CHANGELOG.md` 只保存本次发布摘要，整体替换为本次内容。

- 英文条目在上。
- 空一行，单独一行 `---`，再空一行。
- 简体中文条目在下，与英文的数量、顺序、变化类型、含义一一对应。
- 只写条目：每条以 `- ` 开头，末尾不加标点。
- 没有用户可感知的变化时，两种语言各写一条准确的最小说明。

句式按变化类型选用，描述对象、结果或被修复的后果：

```markdown
- Fixed an issue with {what} that previously caused {consequence}
- Improved {feature}, which now {result}
- Added {feature}, so you can now {action}
- Removed {feature}; {scenario} is no longer available
- Changed how {feature} works; it now {new behavior}

---

- 修复了{对象}的问题，该问题曾导致{后果}
- 对{功能}进行了改善，现在{结果}
- 新增了{功能}，现在可以{做什么}
- 移除了{功能}，{场景}不再可用
- 调整了{功能}的行为，现在{新行为}
```

## 4. 更新版本并验证

把 `ProjectConfig.VERSION_NAME` 改为目标版本。检查版本与更新日志的差异后，按发布范围验证：

```bash
git diff --check
python scripts/build.py compile --dev
```

涉及原生构建或打包时运行 `python scripts/build.py --dev`；资源缺失时先执行 `python scripts/prebuild.py`。有新的可交互界面行为时，按 `debug-app` 技能验证对应场景。

验证失败时修复本次范围内的问题后重试；无法解决时如实报告失败原因。

## 5. 提交与交付

获得提交授权时，功能改动与版本元数据分开提交，版本提交只包含：

- `android/buildSrc/src/main/kotlin/ProjectConfig.kt`
- `.github/CHANGELOG.md`

版本提交标题用 `build: Update summary from v{old} to v{new}`，正文只写源码中看不出的根因与取舍。

未获提交授权时，保留修改并报告目标版本、摘要范围与验证结果。推送、标签与发布工作流各需单独授权。
