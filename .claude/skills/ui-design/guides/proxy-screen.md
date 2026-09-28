# 代理页

## 横向标签与纵向分组

- 默认用文字 + 下划线的横向标签，选中态用主题主色，标签透明，点击时无涟漪与容器背景。布局偏好存在 `PROXY_GROUP_TABS`。选中组按名字保存：全局模式会调整组顺序，搜索也会改变下标。
- 标签栏消费剩余的横向滚动与惯性，拖到边缘时外层主页保持不动；纵向余量交给外层。
- 横向模式直接展示节点，组测速与解除固定放在顶栏，标签位于顶栏下方（放进内容的 stickyHeader 会被顶栏遮住）。标签用横向懒列表，滚动位置与内容列表分开保存。每组内容用 `SaveableStateProvider` 保存位置，并在 ViewModel 缓存偏移（宽屏外壳会重建组合树）；展开集合也由 ViewModel 持有。切组用整视口淡入淡出；淡出的列表使用动画内容参数里的组快照，淡出期间显示的始终是旧组。
- 全局模式固定选中并展示 GLOBAL，与「显示 GLOBAL」开关无关。当前组消失或被搜索过滤掉时，展示第一个可见组。

## 展开与节点布局

- 分组卡片两行：组名；「类型 · 当前节点 · 已测可用数/总数」。已测可用数只计正延迟。右侧延迟状态兼作组测速入口，展开按钮单独响应点击。
- 分组图标在图片加载成功后才显示，缺失或失败时不占位。
- 节点行始终是顶层 lazy item，从第一帧起就占满高度，懒列表只测量可见行（从 0 插值高度会让整组被当作首屏内容一次测完）。
- 展开用可见行的淡入与位置动画；收起直接移除节点行。组头与节点卡片各自是完整圆角。
- 分行固定 `chunked(2)`，单列切换在行内 Layout 中插值宽度与位置；动画进度以 `State<Float>` 传入测量阶段。行参数为 `ImmutableList`。
- 排序、过滤、分行在构建 LazyColumn 内容前按组缓存。仅在按延迟排序或隐藏不可用节点时把延迟表纳入缓存键，普通测速回填保持默认顺序。
- 「仅显示可用节点」只排除已测且超时的节点，未测速的保留。搜索命中组名时显示整组，否则只展示命中的节点。
- 节点卡片首行是名称，第二行协议与延迟按基线对齐；左右留白相同，延迟靠右对齐。第二行保留最小高度，各测速状态下卡片高度一致，底部留 6.dp。延迟按钮与节点选择分别响应点击。
- 选中态只用侧边强调条与勾选，固定节点带图钉；背景与名称颜色同普通节点。

## 搜索

顶栏图标切换搜索框显隐，图标 `holdDownState` 跟随 `searchVisible`。收起时同时清空 query，列表回到完整内容。展开时用 SmallTopAppBar 默认居中的小标题，收起时恢复自适应大标题；展开后先把列表滚到顶，再 `FocusRequester.requestFocus()` 进入输入态。`filterGroups`：组名命中或组内有节点命中都保留该组。

## 空态

空态是一个居中的 lazy item，顶栏与列表结构保持可用。文案按 `groups.isEmpty()` 在「暂无代理组」与「搜索无命中」间选择；「请先启动代理服务」只在 `groups.isEmpty() && !isProxyRunning` 时出现（代理在跑而列表为空也是真实情形：只有 GLOBAL 组且用户隐藏了它）。

## 图标

- 横向标签布局的顶栏测速图标用 `AppIcons.TestDelay`（MingCute dashboard_2_line）。分组卡片右侧用延迟标签，节点卡片的延迟区域无背景。未测速显示「—」，点击即测速，测速中显示 `CircularProgressIndicator`。
- 展开按钮用 `AppIcons.ChevronRight`（right_small），旋转为向下或向上。
- 换图标时改 `gen_icons.py` 的 ICONS 表后重跑，调用点保持不变。

## 更多菜单

出站模式与 TUN Stack 放在右上角「更多」菜单顶部，用分隔线与下方四个显示开关（单列 / 只看可用 / GLOBAL 组 / 刷新图标）隔开：前两项改的是选路行为。条目文案带当前值（`代理模式: Rule`）。菜单条目用 `buildList` + `forEachIndexed` 计算 `index` / `optionSize`，有条件项时（TUN Stack 在未运行或 ROOT TPROXY 下 `enabled=false`）`DropdownImpl` 的圆角分段才正确。写 override + 重启归 `HomeViewModel.switchMode` / `switchTunStack`（配置修改单点），菜单只传值与回调。

## 两个 mode 值

展示用 `homeUiState.mode`：它是「override 持久值 + 运行时 `/configs` 轮询值」，停止态也有值，`switchMode` 内同步 `copy(mode=)` 即可回显。判断分流行为的 `globalModeActive` 读 `ProxyUiState.mode`，与 `loadProxies` 的组排序共用运行时真值（停止态下 `ProxyUiState()` 会整体重置）。

## 出站模式与代理组

各出站模式下都显示全量代理组，并在内容顶部渲染模式提示。`GET /group` / `GET /proxies` 的返回与 `mode` 无关：direct 模式下 mihomo 在 `resolveMetadata` 直接返回 DIRECT，global 模式只由 GLOBAL 决定出口，而 `PUT /proxies/{group}` 在任何模式下都被接受并保存，切回 rule 立即生效，延迟测试照常可用，用户可以先选好节点再切回规则模式。mode 常量在 `ProxyViewModel.companion`。

## GLOBAL 组

`GET /group` 里 GLOBAL 与普通组同级（Selector）。`loadProxies` 用它的 `all` 作为组排序基准，同时把它排进列表：`mode == "global"` 时置顶（唯一生效出口），其余时候沉底；mode 从 `GET /configs` 实时读取。非全局模式下是否显示由 `PROXY_SHOW_GLOBAL_GROUP`（默认开）控制；全局模式始终显示，生效出口随时可选。开关只影响展示：`orderMap` 仍从 `globalGroup.all` 计算，过滤在 UI 层完成。默认开启的原因：rule 模式下 GLOBAL 的 `all` 覆盖全部节点与组，`/group/GLOBAL/delay` 是唯一的一键全量测速入口，提前选好的出口切到 global 时也立即生效。
