# 资源来源与许可

应用图标入口集中在 `ui/icon/AppIcons.kt`。MingCute 图标采用 Apache-2.0，完整许可见 [MingCute-LICENSE.txt](MingCute-LICENSE.txt)。上游来源为 <https://github.com/mingcute-design/mingcute-icons>。

五个固定设置图标采用 miuix 应用规范随附的线框矢量快照，并适配本应用包名。以下 SHA256 标识原始快照；矢量路径保持不变，包名与归属说明按本应用调整。

| 原始文件 | SHA256 |
|---|---|
| MingCuteAppearance.kt | bf80e34d5edf35cd3f95d3550d7565017c9ee821bd4aae3d64ef94a8a324e23c |
| MingCuteDarkMode.kt | 82728e67039957ce5d623afb84a88ff1f8246f2beb292242442ff3ccb5f9e327 |
| MingCuteLanguage.kt | 30f818b22088669cffda180f121fa8c6fd9cfa35a0331fcf102e1035827eab2d |
| MingCuteBlurOn.kt | 81a5138c0c917889c10c19dec47045029e77c687058ec5aae82d6cb13f7b2957 |
| MingCuteSwipeHorizontal.kt | 066e8bf61e03f009b4378f979130204e2e1ca47a3e5396cbb5e7787020ed4d3b |

其余图标的原始样式与 SVG 路径由 `scripts/gen_icons.py` 的 `ICONS` 表和各矢量文件的来源说明记录；底栏当前项使用填充态，其余入口使用线框态。

## 仓库入口图标

`MingCuteGithubLine.kt` 来自 MingCute 官方版本 `9dc7d3ad074fb16477096a6a8109a6467d0e2fe3` 的 `assets/svg/core/regular/brands/github.svg`，为线框样式。原 SVG 的 SHA256 为 `5b4f94cc2564d3ab2d3d149d1332185cb4de1fbe9676708859b80712621c2974`，许可沿用 [MingCute-LICENSE.txt](MingCute-LICENSE.txt)。矢量文件保留完整来源地址。

## 开源库入口图标

`MingCuteLibraryBooks.kt` 使用 Only Player 的 MingCute 书本线框快照，矢量路径与描边保持一致，许可沿用 [MingCute-LICENSE.txt](MingCute-LICENSE.txt)。[原始文件](https://github.com/Kindness-Kismet/only_player/blob/d537dac457a6fa700f831e0e10175c7796e9d684/core/ui/src/main/java/one/only/player/core/ui/designsystem/MingCuteLibraryBooks.kt) 的 SHA256 为 `c24e31e35f81b3b29b87162c9ce255756c90f12bcdc0df37d6d575e0ea11c62e`。

## 关于页流光

关于页沿用项目既有的 `ui/component/effect/BgEffectBackground.kt` 与配套绘制实现，许可见仓库 [LICENSE](../../LICENSE)。明暗预设由 `LocalAppDarkMode` 决定，前景图形与应用名通过背景采样混色。
