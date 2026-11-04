# GitNexus Engineering Plan

> Task: 根据已确认的底部导航功能设计，开发固定 6 槽、4 个中间可配置功能、拖拽排序以及演员/厂商占位入口。
> Evidence verified at commit `a6b5dc08a866025afcc7b538114f57b847b1b217`; GitNexus index refreshed this session with `node .gitnexus/run.cjs analyze --index-only --pdg`. The refreshed context resource pins current HEAD, while individual query/context/impact envelopes continued to report the pre-refresh 3-commit staleness; those graph claims are advisory and every proposed edit is source-verified.
> Evidence provenance schema 2; global dirty digest `a8b17ced2881634fefd0a9eb275d95397ba0c53bc4e55061bdbc114cfb8d2670`; cited-path manifest 17 sorted entries; exact generated plan path excluded.

## 1. Objective

在不改变首页和设置固定位置的前提下，将现有写死的 6 项顶层导航扩展为“首页 + 4 个用户配置项 + 设置”。用户可在“设置 → 界面 → 底部导航”中先删除、再添加、长按拖动排序，并且只有点击“完成”且草稿恰好包含 4 个不同功能时才提交。新增“演员”和“厂商”稳定路由及占位页，本期不实现真实业务数据。

完成后的正式导航必须始终显示 6 个等宽按钮；手机底栏和折叠屏/平板导航栏共享同一份已校验配置。设计依据为 [verified] `docs/BOTTOM_NAVIGATION_CUSTOMIZATION_DESIGN.zh-CN.md:16-31,33-83,85-98,100-218,258-323`。

## 2. Current Behaviour

- [verified] `TopLevelDestination` 目前是 `StashNavHost.kt:102-113` 中的私有枚举，只有 Home、Explore、Shorts、Gallery、Queue、Settings 六项，路由、标签与图标耦合在同一类型中。
- [verified] `StashNavHost` 在 `StashNavHost.kt:142-176` 创建设置仓库，但 `topLevelDestinations` 直接取 `TopLevelDestination.entries`；手机底栏和导航栏分别在 `416-439`、`455-473` 遍历同一固定列表。
- [verified] 全部顶层路由在同一 `NavHost` 中注册（`StashNavHost.kt:502-607`），演员和厂商尚无目的地或页面。
- [verified] 设置子页由 `SettingsDestinations`/`SettingsSection` 定义（`SettingsRoute.kt:104-157`），`SettingsDetailRoute` 在 `736-747` 分发内容；界面设置当前只展示语言选项（`2162-2188`）。
- [verified] `StashSettingsRepository` 使用 Preferences DataStore `stash_settings`（`StashSettingsRepository.kt:48-50`），已形成“Flow 读取 → 纯解析函数 → suspend edit 写入 → Keys/DEFAULT 常量”的稳定模式，但没有导航顺序键。
- [verified] 当前 JVM 测试采用 JUnit4 纯函数测试；项目已配置 Compose UI instrumentation 依赖和 `connectedDebugAndroidTest` 任务（`app/build.gradle.kts:55-69,119-165`），但尚无 `app/src/androidTest` 测试文件。

## 3. Relevant Architecture

1. **导航域**：`app/navigation` 管理路由、顶层选择状态、手机底栏/大屏导航栏以及 `NavHost`。本功能的“可用功能池、默认四项、配置修复、固定六槽构造”应落在这里，避免设置页面自行复制业务规则。
2. **持久化边界**：`StashSettingsRepository` 是现有全局 UI 偏好的 DataStore 入口。导航顺序写入一个有序字符串；读取端必须永远输出合法 4 项，写入端必须拒绝不完整草稿。
3. **设置 UI**：`SettingsRoute` 提供设置分类和 `SettingsLinkCard`（`2387-2439`）；新的编辑器作为 `settings/interface/navigation` 独立路由，由界面设置页进入。
4. **展示与业务分离**：稳定目的地 ID/配置纯函数不依赖 `ImageVector`；图标映射留在 Compose 层。这样损坏配置、去重、补齐、容量和排序都可在 JVM 单元测试中验证。
5. **交互复用**：[verified] 播放列表已有拖动手柄、自定义无障碍“上移/下移”动作（`PlayerPlaylistDrawer.kt:395-447`），卡片已有长按触觉反馈（`SceneCard.kt:78-83`）。编辑器应复用这些交互原则，而不是引入第二套不可访问的手势。
6. **占位页**：[verified] 现有 `StashEmptyState`/`StashEmptyStateModel` 提供空状态基线（`StashStateComponents.kt:21-48`; `StashStateModels.kt:5-12`）；演员/厂商页面可使用同一排版语言，但因设计要求展示专属图标，应在新占位 Composable 中组合 Icon + 文案，不修改共享空状态 API。

## 4. GitNexus Findings

- [graph] `context(StashNavHost, file_path=.../StashNavHost.kt)`：直接调用者只有 `StashPlayerAppRoot`，因此改变 `StashNavHost` 内部列表生成方式不需要改公开入口。关键输出：`incoming.calls: StashPlayerAppRoot`。
- [graph] `impact(StashNavHost, direction=upstream, maxDepth=3, summaryOnly=true)`：1 个直接依赖、2 个总影响节点，风险 LOW。直接依赖已由源码确认是 `StashPlayerAppRoot`。
- [graph] `context(InterfaceSettingsContent, file_path=.../SettingsRoute.kt)` 与 `impact(..., maxDepth=3)`：唯一直接调用者为 `SettingsDetailRoute`。计划通过新增回调保持这一边界，不让编辑器逻辑进入设置分类列表。
- [graph] `impact(StashSettingsRepository, direction=upstream, maxDepth=3)`：11 个直接导入文件，包含 App 根、导航、设置、浏览、图库、播放器、队列、短视频、安装页等。计划只新增 Flow、Key 和 setter，不修改现有成员签名，因此这些直接消费者无需代码迁移，但必须通过全量编译回归。
- [graph] `context/impact(TopLevelDestination)` 将一个 `StashPlayerApp.kt` 文件级 import 报为直接依赖；[verified] 源码中的枚举是私有且所有可执行引用均在 `StashNavHost.kt`。该图结论与源码冲突，不作为变更依据。
- [graph] `detect_changes(scope=all)` 报告两个已跟踪脏文件、0 个变更符号、0 个受影响流程；[verified] Windows 工作区另有未跟踪设计文档。实现时必须保留现有 `CHANGELOG.md` 大堆说明和 `AndroidManifest.xml` 的 `largeHeap` 改动。

## 5. Statement-Level PDG Findings

- [graph] `pdg_query(mode=controls, target=StashNavHost.kt)` 显示 `StashNavHost.kt:397` 的 `shouldShowBottomNavigation` 控制完整手机底栏块，`450` 的 `shouldShowNavigationRail` 控制完整大屏导航栏块。规划含义：只生成一次已校验的可见目的地列表，并同时提供给两个分支，禁止各自解析或补齐配置。
- [graph] `pdg_query(mode=flows, target=StashNavHost, variable=topLevelDestinations)` 未返回 reaching-def 边；[verified] 源码清楚显示定义在 `161`、消费在 `416` 和 `455`。Compose 局部状态流以源码为准。
- [graph] `pdg_query(mode=controls, target=InterfaceSettingsContent)` 返回 0 条控制依赖；[verified] 当前函数是顺序式 UI 组合。新增入口只需传入导航回调并插入一张链接卡，不应在此订阅或修改草稿。
- [inferred] 最重要的顺序约束是“读取修复后再渲染”“草稿完整后再持久化”“写入成功后再返回”。若先返回再异步写入，失败会静默丢失；若正式导航直接使用草稿，删除后的临时空槽会违反固定 6 项要求。

## 6. Proposed Changes

### 6.1 建立纯导航配置模型

**文件**：新增 `app/src/main/java/gomeng/dev/stashplayer/app/navigation/TopLevelNavigation.kt`。

- 将现有私有 `TopLevelDestination` 移为包内可复用枚举，加入稳定 `persistedId`、`route`、`labelRes` 和 `configurable` 元数据；新增 `Performers(route="performers")`、`Studios(route="studios")`。图标仍由 Compose 映射函数提供，不进入持久化模型。
- 定义 `CONFIGURABLE_DESTINATION_POOL = [Explore, Shorts, Gallery, Queue, Performers, Studios]`、`DEFAULT_CONFIGURABLE_DESTINATIONS = [Explore, Shorts, Gallery, Queue]`、`MAX_CONFIGURABLE_DESTINATIONS = 4`。
- 新增读取修复纯函数：解析稳定 ID；过滤 Home/Settings、未知 ID；按首次出现去重；截取前 4；不足时依次从默认四项、再从完整池补齐，最终必须得到 4 个不同可配置项。
- 新增严格写入校验：只有恰好 4 个、全部可配置、互不重复的草稿才合法；写入时不得自动补齐，否则会把用户未完成的草稿静默提交。
- 新增固定六槽构造函数：`[Home] + validatedMiddle + [Settings]`，并新增草稿 add/remove/move 纯函数。add 在容量已满时返回 `AtCapacity` 且保持原列表不变。
- [constraint] 持久化不得使用枚举 ordinal/name；使用明确的 ASCII `persistedId`，防止重排枚举或重命名 Kotlin 标识破坏用户配置。

### 6.2 接入 DataStore 持久化

**文件/符号**：`StashSettingsRepository.kt` / `StashSettingsRepository`、`Keys`、`companion object`。

- 增加 `stringPreferencesKey("bottom_navigation_destinations")`。
- 增加 `Flow<List<TopLevelDestination>>`，读取字符串后调用 6.1 的修复函数，因此下游永远收到合法 4 项。
- 采用按顺序连接的逗号分隔稳定 ID，例如 `explore,shorts,gallery,queue`；解析时 trim、忽略空项和未知项。
- 增加 suspend setter，先执行严格校验；无效时返回失败且不写 DataStore，有效时原子写入一个 preference 值。
- 读取损坏数据时只在内存中修复；不要在 Flow map 中触发写入，避免读取产生副作用和循环。下一次用户点击“完成”时自然写入规范格式。
- 保留所有现有偏好成员和方法签名，避免影响 GitNexus 列出的 11 个直接导入文件。

### 6.3 动态生成正式导航并注册全部路由

**文件/符号**：`StashNavHost.kt` / `StashNavHost`、`TopLevelDestinationIcon`、`isTopLevelDestinationSelected`；`StashNavigationChrome.kt` / 标签映射。

- 在当前 `loadedProfile` 早退之前并行收集导航配置，用显式 Loaded 包装避免先展示默认顺序、下一帧再跳为用户顺序。正式 UI 只在服务器配置和导航配置都完成首次读取后生成。
- 用固定六槽构造函数替换 `TopLevelDestination.entries`。手机 `NavigationBar` 与大屏 `NavigationRail` 必须共享同一个 `visibleTopLevelDestinations` 值。
- 将 Person/PersonOutline 与 Videocam 描边图标映射给 Performers、Studios；设置更新徽标逻辑继续只作用于 Settings。
- 更新路由标签映射，增加 performers/studios；保持 `SettingsDestinations.isSettingsRoute` 的选中规则不变。
- `NavHost` 始终注册所有 8 个顶层目的地，而不是只注册当前可见 6 项。用户隐藏功能只影响导航入口，不影响稳定路由和状态恢复。
- 为 Performers、Studios 注册占位页面；它们使用普通顶层选中逻辑，页面内继续显示底部导航。

### 6.4 增加设置入口和独立编辑路由

**文件/符号**：`SettingsRoute.kt` / `SettingsDestinations`、`SettingsDetailRoute`、`InterfaceSettingsContent`、`SettingsLinkCard`；`StashNavHost.kt` / `NavHost`。

- 新增 `SettingsDestinations.BottomNavigation = "settings/interface/navigation"`。
- `SettingsDetailRoute` 增加 `onOpenBottomNavigationEditor` 回调，并传给 `InterfaceSettingsContent`。
- 在界面设置页语言卡之前加入 `SettingsLinkCard`：标题“底部导航”，描述“选择并排列底部导航中的功能”。设置页只负责发出导航事件。
- 在 `StashNavHost` 显式注册编辑器 composable；不要把它加入 `SettingsSection.entries`，否则会成为一级设置分类。
- 编辑页保存成功后 `popBackStack()`；失败则留在当前页显示错误 Snackbar。系统返回始终丢弃本地草稿，因为设计规定只有显式点击“完成”才写入。

### 6.5 实现编辑器状态与交互

**文件**：新增 `app/src/main/java/gomeng/dev/stashplayer/feature/settings/BottomNavigationEditorRoute.kt`。

- 拆分有仓库依赖的 Route 和无仓库依赖的 `BottomNavigationEditorScreen`，便于 Compose UI 测试。
- 首次进入时用已保存 4 项创建 `rememberSaveable` 草稿；旋转/进程重建时保存稳定 ID 列表。候选列表始终为完整池减草稿，按固定池顺序展示。
- 当前导航预览始终保留 6 个等宽槽：Home、4 个中间槽、Settings。草稿不足 4 项时，剩余中间槽显示不可点击的“待添加”占位；真实底栏继续使用上一次已保存配置。
- Home/Settings 显示固定状态，不提供红色减号和拖动。可变项右上角提供红色圆形减号，候选项提供绿色圆形加号；图形直径 20–24dp，实际触摸目标至少 48×48dp。
- 点击减号只更新草稿并把右侧项左移；点击加号且草稿少于 4 时加入第一个空槽。草稿已满时不改变状态，通过可访问 Snackbar 提示“最多添加4个功能”。不实现替换模式、弹窗或静默覆盖。
- “完成”只在草稿恰好含 4 个不同可配置项时启用；点击后禁用重复提交，等待 DataStore 写入成功再返回。写入异常时恢复按钮并显示本地化错误。
- 删除/添加使用 180–220ms 缩放、淡化和 placement 动画；系统降低动画比例时由 Compose 动画时钟自然缩短/关闭，不以动画作为状态正确性的前提。

### 6.6 实现长按拖拽与无障碍排序

**文件**：同 `BottomNavigationEditorRoute.kt`，纯重排函数位于 `TopLevelNavigation.kt`。

- 仅中间草稿项参与拖动。按住约 180ms 后进入拖动，触发一次 `HapticFeedbackType.LongPress`；拖动项缩放到约 1.06 并提升阴影。
- 根据固定中间槽宽和水平中心点计算目标索引；跨过相邻槽中心时调用纯 move reducer，邻项使用中等阻尼 spring placement 动画让位；松手吸附一次。
- 将拖动范围 clamp 到 4 个中间槽，Home/Settings 永不进入重排算法。
- 为每个草稿项提供自定义无障碍“向左移动/向右移动”动作，调用同一 move reducer；颜色不是唯一状态提示，加号、减号、固定、待添加均有本地化内容描述。
- 手势取消、第二指介入、离开边界时必须清除 dragging/offset 状态，不得提交半次重排。

### 6.7 增加演员/厂商占位页与本地化

**文件**：新增 `app/src/main/java/gomeng/dev/stashplayer/feature/placeholder/TopLevelPlaceholderRoute.kt`；更新四套 `strings.xml`。

- 占位 Composable 接收标题、图标和说明资源；演员使用人物描边图标，厂商使用摄像机描边图标。
- 页面只显示标题、图标、“功能正在开发”和后续说明；不增加返回首页按钮；底部导航由外层 Scaffold 保持。
- 在默认韩文资源、英文、简体中文、繁体中文中同时增加导航标签、设置入口、编辑器标题/说明、固定/待添加、添加/移除/移动、容量/保存失败提示和占位页文案，禁止只补简体中文导致其他语言回退缺失。

### 6.8 记录变更但不发布

**文件**：`CHANGELOG.md`。

- 在 `[Unreleased]` 追加英文和简体中文说明，保留当前未提交的大堆条目。
- 本任务不修改 `versionName`/`versionCode`，不创建标签或 Release；发布由后续明确请求处理。

## 7. Implementation Sequence

### 阶段 1：纯模型与单元测试

1. 新建 `TopLevelNavigation.kt`，搬出目的地定义并实现稳定 ID、池、默认值、读取修复、严格校验、六槽构造和草稿 reducer。
2. 新建 `TopLevelNavigationConfigurationTest.kt`，先覆盖缺失/损坏/重复/越界/未知 ID、严格写入、容量、删除、添加和移动。
3. 运行 `:app:testDebugUnitTest`。本阶段结束时不接 UI，现有应用行为保持原样。

**阶段验收**：所有配置输入都产生确定的合法结果；4 项已满时 add 返回容量状态且原列表字节级不变；Home/Settings 永不进入可配置结果。

### 阶段 2：DataStore 接入

1. 在仓库增加 Key、Flow、setter 和序列化适配。
2. 用单元测试覆盖序列化往返和不完整草稿拒绝；不在 Flow map 内写入。
3. 编译所有引用仓库的模块，确认 11 个现有导入方不需修改。

**阶段验收**：缺少键返回默认四项；损坏字符串修复；有效自定义顺序保持；无效 setter 不覆盖已有值。

### 阶段 3：正式导航动态化

1. 在 `StashNavHost` 首次组合时并行加载配置。
2. 手机底栏和大屏导航栏改用同一个固定六项列表。
3. 注册 performers/studios 路由和图标/标签，但先连接占位内容。
4. 扩展现有导航单元测试，验证固定首尾、六项数量和选中状态。

**阶段验收**：默认配置的视觉顺序与当前版本完全一致；自定义配置同时影响 bottom bar/rail；隐藏入口仍有合法路由；启动不先闪默认顺序再跳自定义顺序。

### 阶段 4：设置入口与编辑器基础状态

1. 增加设置子路由和链接卡。
2. 实现 Route/Screen 分离、草稿初始化、删除、添加、容量 Snackbar、完成按钮和保存失败处理。
3. 使用 `rememberSaveable` 验证旋转恢复；系统返回不写入。

**阶段验收**：删除后只改变预览草稿；少于 4 项不能完成；补满后可提交；满 4 点加号只提示且无状态变化；保存成功后真实导航一次性切换。

### 阶段 5：拖拽、动画和无障碍

1. 接入 180ms 长按、触觉反馈、水平索引计算、spring placement、吸附和取消清理。
2. 接入向左/向右无障碍自定义动作和内容描述。
3. 添加 UI 测试覆盖可点击路径和语义；真机验证拖动手感、误触和动画缩放设置。

**阶段验收**：只能在中间四槽重排；固定项不动；手势取消无残留；TalkBack 不依赖拖动也能排序；动画关闭时功能仍正确。

### 阶段 6：占位页与多语言

1. 完成演员、厂商占位页。
2. 同步四套字符串资源并运行 Android resource 编译。

**阶段验收**：两入口图标风格与现有导航一致；页面选中态正确、底栏保留、没有返回首页按钮；四种语言不显示资源名或错误回退。

### 阶段 7：回归、文档和真机验收

1. 执行 JVM、Compose instrumentation、Lint、Debug 构建。
2. 在手机和至少一个 ≥600dp 模拟/真机配置验证 bottom bar/rail 共用顺序。
3. 验证应用强杀重启、旋转、损坏配置恢复、设置返回和保存失败路径。
4. 在 `CHANGELOG.md` 的 Unreleased 追加条目，保留既有未提交内容。

**阶段验收**：第 13 节全部完成，且 Windows 工作区原有 OOM/largeHeap 改动未被覆盖。

## 8. Test Strategy

### 8.1 JVM 单元测试

**新增** `app/src/test/java/gomeng/dev/stashplayer/app/navigation/TopLevelNavigationConfigurationTest.kt`：

1. `null/blank` → 默认 Explore, Shorts, Gallery, Queue。
2. 未知 ID、固定项 ID、重复 ID → 过滤/去重后按默认池补齐到 4。
3. 超过 4 项 → 保留前 4 个合法不同项。
4. 已有 4 个有效旧配置 → 新增 Performers/Studios 不会自动替换。
5. serialize → parse 保持用户顺序。
6. 固定六槽构造始终 Home 首位、Settings 末位、总数 6。
7. 删除产生 0～3 项合法草稿但不可提交；加号补到第一个空位。
8. 4 项已满 add → `AtCapacity`，输入列表不变。
9. move 左右边界、同位移动、普通重排结果正确且不重复。
10. 严格写入校验拒绝少于 4、重复、固定项、未知项。

**更新** `StartupDestinationTest.kt` 或新增独立选择测试：Settings 子路由继续选中 Settings；Performers/Studios 按自身 route 选中。

### 8.2 Compose instrumentation 测试

**新增** `app/src/androidTest/java/gomeng/dev/stashplayer/feature/settings/BottomNavigationEditorScreenTest.kt`，直接测试无仓库 Screen：

1. 默认预览显示固定 Home/Settings 和 4 个可变项；固定项无删除语义。
2. 删除一个 → 末槽出现“待添加”，候选区出现被删项，“完成”禁用。
3. 点击候选加号 → 填第一个空槽，完成恢复启用。
4. 4 项满时点击任一候选加号 → Snackbar“最多添加4个功能”，草稿不变。
5. 点击完成只上报 4 个不同稳定 ID，重复点击被阻止。
6. 自定义 move-left/move-right 语义改变顺序但不移动 Home/Settings。
7. 演员/厂商候选及图标具有内容描述。

拖拽 180ms 手感、跨槽动画和系统动画关闭采用真机验收，因为时钟/触摸阈值在 instrumentation 中容易产生设备差异；纯 reducer 与无障碍点击路径提供确定性自动化覆盖。

### 8.3 集成与手工验收矩阵

| 场景 | 操作 | 预期 |
| --- | --- | --- |
| 首次升级无配置 | 启动应用 | 顺序与旧版一致，6 项等宽 |
| 自定义并重启 | 删除图库、添加演员、完成、强杀重启 | 演员保持原槽和顺序，仍为 6 项 |
| 未完成退出 | 删除一项后系统返回 | 正式导航和持久化配置不变 |
| 满容量加号 | 草稿 4 项时点击厂商加号 | 只提示上限，不替换任何项 |
| 保存失败 | 注入/模拟 DataStore 写失败 | 页面不退出、完成可重试、显示错误 |
| 手机 | 纵向手机检查底栏 | Home 左、Settings 右、中间四项等宽 |
| 大屏 | ≥600dp 检查 NavigationRail | 使用同一四项及顺序 |
| 占位页 | 打开演员/厂商 | 当前项选中、底栏保留、文案正确 |
| 损坏数据 | 写入重复/未知/不足/超量 ID | 读取稳定修复为 4 项，无崩溃 |
| 无障碍 | TalkBack/自定义动作排序 | 能读出添加、移除、固定、移动、上限 |
| 动画关闭 | 系统动画缩放设为 0 | 操作即时完成，无卡死或残留拖动状态 |

### 8.4 验证命令

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebugAndroidTest
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
```

以上任务已通过 `:app:tasks --all` 验证存在。连接设备不可用时，`assembleDebugAndroidTest` 必须先通过，`connectedDebugAndroidTest` 记录为待真机门禁，不得伪报成功。

## 9. Risk and Impact Analysis

| 风险 | 级别 | 缓解措施 |
| --- | --- | --- |
| `StashNavHost` 是所有页面的组合中心 | 高 | 仅替换目的地列表来源和新增路由；保持播放队列、服务器加载、更新检查代码不动；完成后全量 Debug 构建和导航回归 |
| 设置仓库有 11 个直接导入文件 | 中 | 只增加成员，不改现有签名/键；DataStore 新键独立；运行全量编译 |
| 启动时先显示默认顺序再跳自定义顺序 | 中 | 在早退前并行完成首次配置读取；未加载前不构造错误导航 chrome |
| 损坏/旧版本配置造成少项、重复或固定项混入 | 高 | 单一纯 normalization 入口；正式渲染只接受其输出；严格写入拒绝不完整草稿 |
| 草稿误写入导致正式底栏空槽 | 高 | 删除/添加/排序只改 `rememberSaveable`；唯一持久化点是“完成”且校验通过 |
| 拖拽手势与点击减号/加号冲突 | 中 | 操作按钮独立 48dp 区域；仅功能主体启动长按；手势取消彻底清理 offset/dragging |
| 180ms 长按过短导致误拖 | 中 | 真机可用性验收；保持触摸 slop；若误触明显，只在设计允许范围内调阈值，不改功能规则 |
| 图标/文字本地化遗漏 | 中 | 四套资源同一提交更新；resource compile + UI smoke test |
| WSL 证据工具将多处 CRLF 工作树文件标为 unstaged | 低（计划证据） | §11 固定每个引用路径的 HEAD/index/worktree digest；执行者以清单重新锚定，不把“unstaged”误判为本功能已修改 |
| 现有未提交 OOM 改动被覆盖 | 高 | 不清理工作树；对 `CHANGELOG.md` 做增量补丁；不改/回退 Manifest 的 `largeHeap` |

直接依赖核算：

- `StashNavHost` d=1：`StashPlayerAppRoot`；签名保持不变。
- `InterfaceSettingsContent` d=1：`SettingsDetailRoute`；两者在同一文件协调修改。
- `StashSettingsRepository` d=1：App root、StashNavHost、DeveloperSupportCard、Browse、Explore、Gallery、Player、Queue、Settings、Setup、Shorts 共 11 个导入文件；新增 API 不要求消费者迁移。
- `TopLevelDestination` 图报告的 d=1 与源码冲突；按源码只处理 `StashNavHost` 内部引用，并以编译器验证遗漏。

## 10. Files Expected to Change

| File | Symbols / content | Reason |
| --- | --- | --- |
| `app/src/main/java/gomeng/dev/stashplayer/app/navigation/TopLevelNavigation.kt` (new) | destination model, pools, normalization, validation, draft reducers | 单一业务规则与纯测试边界 |
| `app/src/main/java/gomeng/dev/stashplayer/app/navigation/StashNavHost.kt` | `StashNavHost`, icon mapping, route registration | 动态六槽、编辑路由、占位路由 |
| `app/src/main/java/gomeng/dev/stashplayer/app/navigation/StashNavigationChrome.kt` | route-label map/helpers | 增加演员/厂商标签及可测试导航规则 |
| `app/src/main/java/gomeng/dev/stashplayer/core/network/StashSettingsRepository.kt` | Flow, setter, Key, serialization bridge | 持久化四个中间功能及顺序 |
| `app/src/main/java/gomeng/dev/stashplayer/feature/settings/SettingsRoute.kt` | destinations, callbacks, interface link | 设置入口与独立编辑页导航 |
| `app/src/main/java/gomeng/dev/stashplayer/feature/settings/BottomNavigationEditorRoute.kt` (new) | Route/Screen, draft, gestures, semantics | 配置 UI 与交互 |
| `app/src/main/java/gomeng/dev/stashplayer/feature/placeholder/TopLevelPlaceholderRoute.kt` (new) | performers/studios placeholder | 首期占位功能 |
| `app/src/main/res/values*/strings.xml` | 4 套新增文案 | 标签、提示、无障碍和占位页本地化 |
| `app/src/test/java/gomeng/dev/stashplayer/app/navigation/TopLevelNavigationConfigurationTest.kt` (new) | pure model tests | 配置修复与 reducer 回归 |
| `app/src/test/java/gomeng/dev/stashplayer/app/navigation/StartupDestinationTest.kt` 或新选择测试 | route selection assertions | 顶层选中态回归 |
| `app/src/androidTest/java/gomeng/dev/stashplayer/feature/settings/BottomNavigationEditorScreenTest.kt` (new) | stateless Compose UI tests | 删除/添加/上限/完成/语义 |
| `CHANGELOG.md` | Unreleased bilingual bullets | 记录功能且保留既有改动 |

`app/build.gradle.kts` 当前依赖已足够，预期不修改；只有测试编译证明缺少明确依赖时才做最小补充。

## 11. Reusable Implementation Context

```yaml
implementation_context:
  task_summary: "Implement fixed six-slot configurable top-level navigation with four persisted middle destinations, a draft editor, drag reorder, and performer/studio placeholders."
  acceptance_criteria:
    - "Saved/runtime navigation is always Home + four unique configurable destinations + Settings."
    - "Only explicit Done with a valid four-item draft persists; system back discards."
    - "At capacity, plus shows a localized limit message and does not replace anything."
    - "Bottom bar and navigation rail consume the same configuration and ordering."
    - "Performer and studio routes open selectable placeholder pages."
    - "Corrupt/missing persisted data normalizes deterministically without crashing."
  evidence_provenance:
    schema_version: 2
    head_commit: "a6b5dc08a866025afcc7b538114f57b847b1b217"
    generated_plan_path: "docs/plans/2026-11-03-gitnexus-plan-bottom-navigation-customization.md"
    global_dirty_digest:
      algorithm: "sha256"
      canonicalization: "gitnexus-evidence-provenance-v2 NUL-framed UTF-8 records"
      value: "a8b17ced2881634fefd0a9eb275d95397ba0c53bc4e55061bdbc114cfb8d2670"
    cited_path_manifest:
      - path: "CHANGELOG.md"
        object_kind: { head: "regular", index: "regular", worktree: "regular", untracked: "absent" }
        state: "unstaged"
        rename_from: null
        rename_to: null
        head_digest: "sha256:8e50b2148ae23d2d12e263ed245ede18f3621a74a6806b20127ebc8c8d8e4eb8"
        index_digest: "sha256:8e50b2148ae23d2d12e263ed245ede18f3621a74a6806b20127ebc8c8d8e4eb8"
        worktree_digest: "sha256:db8ae9f20e8547dc447b925870650ab4b67d4fc39562b6f79ce4917715e0b484"
        untracked_digest: "absent"
      - path: "app/build.gradle.kts"
        object_kind: { head: "regular", index: "regular", worktree: "regular", untracked: "absent" }
        state: "unstaged"
        rename_from: null
        rename_to: null
        head_digest: "sha256:d45fe480d0af5e007d8e73e6d32e7e634c709cff42d8179e480d8ed29d7e4cf8"
        index_digest: "sha256:d45fe480d0af5e007d8e73e6d32e7e634c709cff42d8179e480d8ed29d7e4cf8"
        worktree_digest: "sha256:8c96e82bfe36b1406f6d9733b65ccd9f3acafb4cfcb55449e6c555450cef5624"
        untracked_digest: "absent"
      - path: "app/src/main/java/gomeng/dev/stashplayer/app/navigation/StashNavHost.kt"
        object_kind: { head: "regular", index: "regular", worktree: "regular", untracked: "absent" }
        state: "unstaged"
        rename_from: null
        rename_to: null
        head_digest: "sha256:60e404173fd7916626d9b9c4eb548c9e813c3b5e42c1c2fd085fa3a09133c80c"
        index_digest: "sha256:60e404173fd7916626d9b9c4eb548c9e813c3b5e42c1c2fd085fa3a09133c80c"
        worktree_digest: "sha256:6837fafe3ed5f990bb15680267837f061993523f0fac569ca92638aa332a9b49"
        untracked_digest: "absent"
      - path: "app/src/main/java/gomeng/dev/stashplayer/app/navigation/StashNavigationChrome.kt"
        object_kind: { head: "regular", index: "regular", worktree: "regular", untracked: "absent" }
        state: "unstaged"
        rename_from: null
        rename_to: null
        head_digest: "sha256:43c8254657308da6f38d841595022117d732728f46d9918aa1a77459f6e023b2"
        index_digest: "sha256:43c8254657308da6f38d841595022117d732728f46d9918aa1a77459f6e023b2"
        worktree_digest: "sha256:c9f0a659e2f03178bd84a88b954cb6eb36c2db18364c131f7377359ee6d4603c"
        untracked_digest: "absent"
      - path: "app/src/main/java/gomeng/dev/stashplayer/core/network/StashSettingsRepository.kt"
        object_kind: { head: "regular", index: "regular", worktree: "regular", untracked: "absent" }
        state: "unstaged"
        rename_from: null
        rename_to: null
        head_digest: "sha256:3b0a6caf1359582cec95051d80f8f265828f4e024f413b4b63e52c7e42d692f9"
        index_digest: "sha256:3b0a6caf1359582cec95051d80f8f265828f4e024f413b4b63e52c7e42d692f9"
        worktree_digest: "sha256:224d74241f86553764c6fec5fcc1f9e547572843219052af2f4ff239312c7e27"
        untracked_digest: "absent"
      - path: "app/src/main/java/gomeng/dev/stashplayer/core/ui/components/SceneCard.kt"
        object_kind: { head: "regular", index: "regular", worktree: "regular", untracked: "absent" }
        state: "unstaged"
        rename_from: null
        rename_to: null
        head_digest: "sha256:d8fddf5a62b32f50f8ef0fac14f4a9968b9c85fb06ff0e53688c7ef86cbca993"
        index_digest: "sha256:d8fddf5a62b32f50f8ef0fac14f4a9968b9c85fb06ff0e53688c7ef86cbca993"
        worktree_digest: "sha256:9d47c92d4f516c7e085a7fadf8d2d32bc0703ab07ca0e8c173218c49723515fd"
        untracked_digest: "absent"
      - path: "app/src/main/java/gomeng/dev/stashplayer/core/ui/designsystem/StashStateComponents.kt"
        object_kind: { head: "regular", index: "regular", worktree: "regular", untracked: "absent" }
        state: "unstaged"
        rename_from: null
        rename_to: null
        head_digest: "sha256:5f05c8a7c7826c7697d596170a192a01ba805d3d483eb591babd7a945bea4f9a"
        index_digest: "sha256:5f05c8a7c7826c7697d596170a192a01ba805d3d483eb591babd7a945bea4f9a"
        worktree_digest: "sha256:064257fd31b4b6e0af97ee28c0b76e0b44b4d35ba67e75d3f611e86499e71aa0"
        untracked_digest: "absent"
      - path: "app/src/main/java/gomeng/dev/stashplayer/core/ui/designsystem/StashStateModels.kt"
        object_kind: { head: "regular", index: "regular", worktree: "regular", untracked: "absent" }
        state: "unstaged"
        rename_from: null
        rename_to: null
        head_digest: "sha256:686d27c7ff807b2c6cdb371468a8e40fa2b4eab2b2c88febf2e24a5e5471eeda"
        index_digest: "sha256:686d27c7ff807b2c6cdb371468a8e40fa2b4eab2b2c88febf2e24a5e5471eeda"
        worktree_digest: "sha256:46845cbe9867a11a45abcb20b5d45efcf6020a4a154fa42fbdb37ec234de5a1d"
        untracked_digest: "absent"
      - path: "app/src/main/java/gomeng/dev/stashplayer/feature/player/PlayerPlaylistDrawer.kt"
        object_kind: { head: "regular", index: "regular", worktree: "regular", untracked: "absent" }
        state: "unstaged"
        rename_from: null
        rename_to: null
        head_digest: "sha256:857812da780b193726cc680665f62248c1766324caf183a7442c133a6b29ce3a"
        index_digest: "sha256:857812da780b193726cc680665f62248c1766324caf183a7442c133a6b29ce3a"
        worktree_digest: "sha256:0203a81637676b0dd7d8d07a64244b6ef85b95f0625a97bc74b45fbabefb7d6c"
        untracked_digest: "absent"
      - path: "app/src/main/java/gomeng/dev/stashplayer/feature/settings/SettingsRoute.kt"
        object_kind: { head: "regular", index: "regular", worktree: "regular", untracked: "absent" }
        state: "unstaged"
        rename_from: null
        rename_to: null
        head_digest: "sha256:bae3440de85e1831a2d10d6fb2570a94896e829d1132b1ac98434ede1a77d9ef"
        index_digest: "sha256:bae3440de85e1831a2d10d6fb2570a94896e829d1132b1ac98434ede1a77d9ef"
        worktree_digest: "sha256:d6723ceff5c7af5a18cc459641da70e4b465e8610473095c318f03d2e955249d"
        untracked_digest: "absent"
      - path: "app/src/main/res/values-b+zh+Hans/strings.xml"
        object_kind: { head: "regular", index: "regular", worktree: "regular", untracked: "absent" }
        state: "unstaged"
        rename_from: null
        rename_to: null
        head_digest: "sha256:87191d3a876a40dd2e372fb9a49c211a487c0373e15d5e61a64f54c7135a5f96"
        index_digest: "sha256:87191d3a876a40dd2e372fb9a49c211a487c0373e15d5e61a64f54c7135a5f96"
        worktree_digest: "sha256:872bfe5931521a1aa63e85c49b8dde84dfba5c92682b537829b10ab30168aa46"
        untracked_digest: "absent"
      - path: "app/src/main/res/values-b+zh+Hant/strings.xml"
        object_kind: { head: "regular", index: "regular", worktree: "regular", untracked: "absent" }
        state: "unstaged"
        rename_from: null
        rename_to: null
        head_digest: "sha256:d333a13c44b1e459616661af78180065548c14e998fe4be308cc00d950835c1c"
        index_digest: "sha256:d333a13c44b1e459616661af78180065548c14e998fe4be308cc00d950835c1c"
        worktree_digest: "sha256:b1eb7f20f8342932e39a8e87767010740594e6ef55dbc716314bafbd9f5b274a"
        untracked_digest: "absent"
      - path: "app/src/main/res/values-en/strings.xml"
        object_kind: { head: "regular", index: "regular", worktree: "regular", untracked: "absent" }
        state: "unstaged"
        rename_from: null
        rename_to: null
        head_digest: "sha256:4bd8c2715036271f49b67cd74d970aa4fff8f0ea12a10f61a13cdf5c5cb0ac21"
        index_digest: "sha256:4bd8c2715036271f49b67cd74d970aa4fff8f0ea12a10f61a13cdf5c5cb0ac21"
        worktree_digest: "sha256:4ddf02fbbc2bc6fd747eff923626e901f7f1b52b12f0584d614e3e6fedd003a3"
        untracked_digest: "absent"
      - path: "app/src/main/res/values/strings.xml"
        object_kind: { head: "regular", index: "regular", worktree: "regular", untracked: "absent" }
        state: "unstaged"
        rename_from: null
        rename_to: null
        head_digest: "sha256:3fd7c6fae906c7bb3f3505ed39a237c6a9c64b906bad22b8aad076bd443baba5"
        index_digest: "sha256:3fd7c6fae906c7bb3f3505ed39a237c6a9c64b906bad22b8aad076bd443baba5"
        worktree_digest: "sha256:917a51baa179b3ff365d6d56be5fa863ad2eebd870e56fe65ae377a620901611"
        untracked_digest: "absent"
      - path: "app/src/test/java/gomeng/dev/stashplayer/app/navigation/PlayerOrientationRequestTest.kt"
        object_kind: { head: "regular", index: "regular", worktree: "regular", untracked: "absent" }
        state: "unstaged"
        rename_from: null
        rename_to: null
        head_digest: "sha256:3615e54d9bb366056283e2d373d9459cc75612571039cfbc8a864d1fbd0cbf79"
        index_digest: "sha256:3615e54d9bb366056283e2d373d9459cc75612571039cfbc8a864d1fbd0cbf79"
        worktree_digest: "sha256:0a268f0258baa25388059cacfc11029137ce507bf1ea8be2ec9263ba5f46e1df"
        untracked_digest: "absent"
      - path: "app/src/test/java/gomeng/dev/stashplayer/app/navigation/StartupDestinationTest.kt"
        object_kind: { head: "regular", index: "regular", worktree: "regular", untracked: "absent" }
        state: "unstaged"
        rename_from: null
        rename_to: null
        head_digest: "sha256:8fefb8589625c5329609b3cfcb4ecd473ff38bcbddd9e6c23e344ace86208ffc"
        index_digest: "sha256:8fefb8589625c5329609b3cfcb4ecd473ff38bcbddd9e6c23e344ace86208ffc"
        worktree_digest: "sha256:7d1fc96044cfe9aa80732418952587524be95b9a747bd154d07da064f1942638"
        untracked_digest: "absent"
      - path: "docs/BOTTOM_NAVIGATION_CUSTOMIZATION_DESIGN.zh-CN.md"
        object_kind: { head: "absent", index: "absent", worktree: "absent", untracked: "regular" }
        state: "untracked"
        rename_from: null
        rename_to: null
        head_digest: "absent"
        index_digest: "absent"
        worktree_digest: "absent"
        untracked_digest: "sha256:14d29620c1e0392df09071de8c1561cfe2a926aee52f2482ac37b61d8bba1736"
  primary_symbols:
    - symbol: "StashNavHost"
      file: "app/src/main/java/gomeng/dev/stashplayer/app/navigation/StashNavHost.kt"
      lines: "137-672"
      role: "Owns top-level chrome, navigation controller and route registration."
    - symbol: "TopLevelDestination"
      file: "app/src/main/java/gomeng/dev/stashplayer/app/navigation/StashNavHost.kt"
      lines: "102-113"
      role: "Current fixed destination definition to extract and extend."
    - symbol: "StashSettingsRepository"
      file: "app/src/main/java/gomeng/dev/stashplayer/core/network/StashSettingsRepository.kt"
      lines: "50-616"
      role: "Preferences DataStore boundary for ordered navigation configuration."
    - symbol: "InterfaceSettingsContent"
      file: "app/src/main/java/gomeng/dev/stashplayer/feature/settings/SettingsRoute.kt"
      lines: "2162-2188"
      role: "Entry point for the bottom-navigation editor."
  related_symbols:
    - symbol: "SettingsDetailRoute"
      relationship: "CALLS InterfaceSettingsContent"
      relevance: "Must receive and forward the editor navigation callback."
    - symbol: "SettingsLinkCard"
      relationship: "existing UI pattern"
      relevance: "Use for the Interface settings entry."
    - symbol: "PlayerPlaylistDrawer drag handle"
      relationship: "existing gesture/accessibility pattern"
      relevance: "Reference custom move actions and drag cancellation."
    - symbol: "StashEmptyState"
      relationship: "existing placeholder pattern"
      relevance: "Visual baseline for performer/studio placeholders."
  execution_path:
    - "Preferences DataStore emits raw ordered IDs."
    - "Pure normalization returns exactly four unique configurable destinations."
    - "StashNavHost constructs Home + four middle destinations + Settings once."
    - "The same six-item list renders in NavigationBar or NavigationRail."
    - "Editor copies the persisted middle list into a local saveable draft."
    - "Delete/add/move mutate only the draft; Done validates then writes atomically."
    - "After successful write, the Flow updates chrome and the editor pops."
  pdg_constraints:
    - description: "Phone and large-screen chrome are independently guarded but consume one destination list."
      affected_statements: ["StashNavHost.kt:397", "StashNavHost.kt:416", "StashNavHost.kt:450", "StashNavHost.kt:455"]
      implementation_consequence: "Normalize/build once before either branch; do not fork configuration logic."
    - description: "Compose local reaching-def was not emitted by PDG."
      affected_statements: ["StashNavHost.kt:161", "StashNavHost.kt:416", "StashNavHost.kt:455"]
      implementation_consequence: "Use source-verified data path and compiler/tests as the guard."
  architectural_patterns:
    - pattern: "Preferences DataStore Flow + pure parse + suspend edit setter"
      example_location: "StashSettingsRepository.kt:119-133,257-278,396-470"
      usage_guidance: "Add an independent ordered-string preference and keep read repair side-effect free."
    - pattern: "Settings detail callback into NavHost"
      example_location: "SettingsRoute.kt:723-749; StashNavHost.kt:602-624"
      usage_guidance: "Pass editor navigation as a callback; keep navController out of settings UI."
    - pattern: "Accessible reorder actions plus pointer drag"
      example_location: "PlayerPlaylistDrawer.kt:395-447"
      usage_guidance: "Use the same reducer for gesture and custom semantics actions."
  files_to_modify:
    - file: "app/src/main/java/gomeng/dev/stashplayer/app/navigation/TopLevelNavigation.kt"
      symbols: ["new destination/configuration domain"]
      intended_change: "Stable IDs, pool/defaults, normalization, strict validation, fixed-six builder and draft reducers."
    - file: "app/src/main/java/gomeng/dev/stashplayer/app/navigation/StashNavHost.kt"
      symbols: ["StashNavHost", "TopLevelDestinationIcon", "isTopLevelDestinationSelected"]
      intended_change: "Consume persisted configuration, render shared six-item chrome, register editor and placeholder routes."
    - file: "app/src/main/java/gomeng/dev/stashplayer/app/navigation/StashNavigationChrome.kt"
      symbols: ["TopLevelDestinationLabelResourcesByRoute"]
      intended_change: "Add performer/studio route labels and keep pure navigation helpers testable."
    - file: "app/src/main/java/gomeng/dev/stashplayer/core/network/StashSettingsRepository.kt"
      symbols: ["StashSettingsRepository", "Keys", "companion object"]
      intended_change: "Add ordered navigation Flow and validated atomic setter."
    - file: "app/src/main/java/gomeng/dev/stashplayer/feature/settings/SettingsRoute.kt"
      symbols: ["SettingsDestinations", "SettingsDetailRoute", "InterfaceSettingsContent"]
      intended_change: "Add editor route constant, callback and settings link."
    - file: "app/src/main/java/gomeng/dev/stashplayer/feature/settings/BottomNavigationEditorRoute.kt"
      symbols: ["new Route/Screen"]
      intended_change: "Draft editor, capacity feedback, submit gating, animation, drag and semantics."
    - file: "app/src/main/java/gomeng/dev/stashplayer/feature/placeholder/TopLevelPlaceholderRoute.kt"
      symbols: ["new placeholder composable"]
      intended_change: "Performer/studio coming-soon content."
    - file: "app/src/main/res/values*/strings.xml"
      symbols: ["new localized resources"]
      intended_change: "Four-language labels, messages, accessibility and placeholders."
    - file: "CHANGELOG.md"
      symbols: ["Unreleased"]
      intended_change: "Append feature notes without replacing existing large-heap entries."
  tests:
    - file: "app/src/test/java/gomeng/dev/stashplayer/app/navigation/TopLevelNavigationConfigurationTest.kt"
      scenarios:
        - "missing/corrupt/duplicate/unknown/too-short/too-long raw IDs → deterministic valid four-item result"
        - "valid custom four-item order → round trip unchanged"
        - "draft remove/add/move/capacity → exact expected immutable state"
        - "four middle items → fixed six-slot list with Home/Settings anchors"
    - file: "app/src/androidTest/java/gomeng/dev/stashplayer/feature/settings/BottomNavigationEditorScreenTest.kt"
      scenarios:
        - "remove → placeholder + Done disabled"
        - "add with space → first empty slot + Done enabled"
        - "add at capacity → localized Snackbar + unchanged draft"
        - "custom move semantics → reorder only configurable items"
  verification_commands:
    - ".\\gradlew.bat :app:testDebugUnitTest"
    - ".\\gradlew.bat :app:assembleDebugAndroidTest"
    - ".\\gradlew.bat :app:connectedDebugAndroidTest"
    - ".\\gradlew.bat :app:lintDebug"
    - ".\\gradlew.bat :app:assembleDebug"
  risks:
    - "StashNavHost is a high-connectivity composition root; keep unrelated playback/server logic untouched."
    - "Never render or persist an incomplete editor draft."
    - "Preserve existing uncommitted largeHeap and changelog changes."
    - "Graph query envelopes reported stale metadata after refresh; source and compiler are authoritative."
  assumptions:
    - "System back discards draft; verify against design lines 289-291 before implementation."
    - "Use Snackbar for capacity/error feedback; verify wording in all four resource sets."
    - "180ms is a target hold duration; verify accidental-drag rate on the connected Xiaomi device."
  open_questions: []
  avoid:
    - "Do not implement real performer/studio API, lists, filters or details in this task."
    - "Do not auto-replace a configured destination when plus is tapped at capacity."
    - "Do not persist enum ordinal/name or an unordered string set."
    - "Do not mutate DataStore from a Flow map/read path."
    - "Do not bump version, tag, publish or release."
    - "Do not reset or overwrite unrelated dirty worktree changes."
```

## 12. Assumptions and Open Questions

### Assumptions

1. [assumed] 系统返回始终放弃编辑草稿；只有显式点击“完成”会持久化。依据是设计文档 `289-291` 的“只有点击完成时写入”。执行前快速复核设计文档未被用户再次修改。
2. [assumed] 容量和保存失败反馈使用 Snackbar，而不是 Toast。它满足“直接提示”并更适合无障碍和 Compose 测试；若项目后续建立统一 Toast 规范，再替换展示层，不改变 reducer。
3. [assumed] 180ms 为目标长按阈值。实现时需在已连接的小米手机上检查误拖；若系统触摸 slop 下仍易误触，先向用户报告测量结果再调整，不擅自改成功能点击拖动。
4. [assumed] 默认配置缺失时继续使用 Explore/Shorts/Gallery/Queue，不把新功能自动插入已存在的合法 4 项配置。

### Open questions

无阻塞问题。所有会改变用户可见规则的选择已由设计文档固定。

### Explicitly deferred follow-ups

- 演员/厂商的 GraphQL 列表、筛选、详情和分页。
- 云同步或按服务器配置分别保存导航布局。
- 发布版本号、签名 APK、Git 标签和 GitHub Release。

## 13. Definition of Done

### 功能验收

- [ ] 任何已保存/修复后的正式导航均为 6 项等宽：Home + 4 个不同可配置项 + Settings。
- [ ] Home 永远第 1、Settings 永远第 6，二者不可删除、拖动或进入持久化中间列表。
- [ ] 默认升级后视觉顺序与当前版本一致；已有合法用户顺序不会因新增演员/厂商被替换。
- [ ] 删除只影响编辑草稿；正式底栏/导航栏继续使用上一次完整配置。
- [ ] 草稿不足 4 项显示固定槽“待添加”，完成按钮禁用。
- [ ] 有空位时绿色加号直接加入第一个空槽；已满时只显示“最多添加4个功能”，不替换、不覆盖、不改变顺序。
- [ ] 只有点击“完成”且严格校验通过才写入；写成功后返回，写失败停留并可重试；系统返回不保存。
- [ ] 中间功能支持长按水平重排、触觉反馈、邻项 spring 让位和松手吸附；Home/Settings 不参与。
- [ ] TalkBack 可读出固定、添加、移除、待添加、容量提示，并能通过向左/向右自定义动作完成排序。
- [ ] 演员和厂商可作为候选加入，点击后打开各自占位页，当前项选中且导航 chrome 保留。
- [ ] 手机 bottom bar 与 ≥600dp NavigationRail 使用相同四项和顺序。
- [ ] 缺失、空白、未知、重复、不足、超量持久化数据均不会崩溃，并确定性恢复到合法 4 项。
- [ ] 四套语言资源齐全，浅色/深色和用户强调色下加减操作可辨识，触摸目标至少 48dp。

### 自动化与构建门禁

- [ ] `:app:testDebugUnitTest` 通过，覆盖配置修复、严格校验、六槽构造和草稿 reducer。
- [ ] `:app:assembleDebugAndroidTest` 通过。
- [ ] 有连接设备时 `:app:connectedDebugAndroidTest` 通过；否则明确记录待执行，不能视为完成发布门禁。
- [ ] `:app:lintDebug` 通过或记录与本功能无关且可复现的环境阻塞。
- [ ] `:app:assembleDebug` 通过，并在连接的小米手机安装验证。

### 变更完整性

- [ ] 不修改真实演员/厂商业务 API，不增加无关重构。
- [ ] `CHANGELOG.md` Unreleased 已追加双语条目，原有 largeHeap 条目仍在。
- [ ] `AndroidManifest.xml` 的现有 `android:largeHeap="true"` 和其他用户未提交改动均未被覆盖。
- [ ] 未修改版本号、未提交、未推送、未打标签、未发布 Release，除非用户另行明确要求。
