---
name: ui-design
description: Compose / miuix 的全部界面约定与约束。改 android/app/src/main/kotlin/.../ui/ 下任何文件之前必须先读。触发词包括 改界面, 改 UI, 调整布局, 加页面, 加屏幕, 加卡片, 加弹窗, 加按钮, 改样式, 改配色, 改主题, 改深色模式, 改动画, 改滚动, 页面骨架, 毛玻璃, 宽屏适配, 刘海, BottomSheet, Dialog, LazyColumn, Composable, 代理页, 首页状态卡, compose 性能, 重组, 卡顿.
user_invocable: true
---

## 核心约束

1. 组件用 miuix，自定义形状用 squircle modifier，与 miuix 圆角保持一致。颜色只取自 `MiuixTheme.colorScheme.*` 或 `ui.theme.StatusColors`，用户强制深 / 浅色时各处才对得上。
2. 深浅色一律读 `LocalAppDarkMode.current`，它反映用户的强制设置。
3. 帧率级 State（滚动折叠比例、动画进度）在 layout / draw 阶段读：以 `State<T>` 或 `() -> T` 透传给消费方，组合期读会让整个 restart scope 每帧失效。

## 指引索引

只读当前任务需要的 guide。

| 任务 | 指引 |
|---|---|
| 新建页面、contentPadding、毛玻璃、宽屏、横屏刘海、搜索页、导航 | `guides/skeleton.md` |
| miuix 组件用法、squircle 三选一、卡片拆 lazy item | `guides/components.md` |
| Dialog 按钮序与长内容、选项列表、单选、BottomSheet inset | `guides/dialogs.md` |
| 深浅色判定、XML 主题层、语义色 token | `guides/theme.md` |
| Flow 收集、`@Immutable`、帧率级读取、动画门控、VM 生命周期 | `guides/performance.md` |
| 代理页（节点展开、收起动画、搜索、空态、更多菜单） | `guides/proxy-screen.md` |
| 首页（状态卡、操作区、水印绘制、1Hz 动画） | `guides/home-screen.md` |

其余架构约束见仓库根的 [AGENTS.md](../../../AGENTS.md)。

## 测试 ID

新增可交互控件在 [TestTags.kt](../../../android/app/src/main/kotlin/com/stelliberty/android/ui/util/TestTags.kt) 登记，命名「页面.控件」；设置项由 `groupedCardItems` 按 `CardItem` 的 key 自动生成。测试 ID 是调试脚本的契约，改名时同步更新 debug-app skill 里的引用（见其 `guides/control.md`）。
