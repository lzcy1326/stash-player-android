# 演员功能开发与验收记录

依据：`docs/PERFORMERS_FEATURE_TECHNICAL_DESIGN.zh-CN.md`。每个阶段通过安卓模拟器验收后才能提交；用户现有未提交改动不纳入演员功能提交。

## MVP-0：接口及模拟器基线（已验收）

2026-11-05 对用户授权的 Stash 服务做了只读 GraphQL introspection 和查询，未执行任何写操作。记录字段名和行为，不保存地址、凭据或演员个人数据。

- `findPerformers(filter, performer_filter)` 返回 `count` 和 `performers`；列表可读取 `id`、`name`、`alias_list`、`scene_count`、`image_path`、`favorite`、`rating100`，以及性别、国家和出生日期等字段。姓名排序的分页查询正常。
- 实测使用存在的别名进行通用 `filter.q` 查询，结果包含对应演员；该结论针对当前服务端与样本。
- `findPerformer(id)` 提供演员详情。已验证 schema 含 `urls`、`birthdate`、`death_date`、`height_cm`、`measurements`、`ethnicity`、`hair_color`、`eye_color`、`career_start`、`career_end`、`details`、`tags` 和 `stash_ids`。
- `PerformerFilterType` 包含 `filter_favorites`、`age`、`rating100`、`gender`、`tags`、`country`、`scene_count`、`height_cm`、`career_start`、`career_end`、`is_missing`，以及 `AND`/`OR`/`NOT`。各类型的具体边界和组合语义仍须测试。
- `performerUpdate(input: PerformerUpdateInput!)` 存在，输入包含 `id`、`favorite`、`rating100`。写入、清空评分、失败处理尚未联调。
- `findScenes` 的 `scene_filter.performers` 支持 `MultiCriterionInput`。以 `value: [演员 ID]` 和 `modifier: INCLUDES` 实测后，结果首个影片确有该演员，`count`、`duration`、`filesize` 可取得。后两项分别以秒和字节为单位；仍需测试用户影片条件叠加、跨页与空结果。
- 当前服务端对演员排序键 `name`、`rating`、`scenes_count`、`latest_scene`、`play_count`、`updated_at`、`random` 均成功返回；各排序的顺序和空值规则仍需针对性验证。网页源码使用 `random_<seed>`。
- 已分别提交年龄、评分、收藏、性别、国家、影片数量、身高、职业起始、资料缺失、标签条件的只读查询并收到有效响应；这只证明输入形式被接受，具体筛选结果的边界语义仍需结合样本验收。同一 `random_12345` 种子连续读取三页并重复，页面顺序一致且无重复 ID（当前样本 12 条）。
- 实测演员固定范围与时长条件以 AND 叠加，返回影片均包含指定演员。新增脱敏响应解析、GraphQL 错误、固定范围组合及分页变量测试，`StashPerformerContractTest` 通过；`:app:assembleDebug` 构建通过。
- 原有模拟器 `emulator-5554` 的系统进程无响应，未重置或删除其数据。临时隔离模拟器 `emulator-5556` 启动成功；在其上安装现有 Debug 应用并连接用户服务，首页显示 Connected 和影片数据。此项是基线，不代表演员功能已经验收。
- 将本阶段新构建安装到 `emulator-5556` 并冷启动，首页仍显示 Connected 和内容，模拟器基线通过。

本阶段验收：接口字段映射、只读样本、脱敏解析和变量测试、模拟器安装及连接基线均通过。排序边界、可选字段和更新值需在对应功能阶段继续联调，不能以本基线代替功能验收。

## MVP-1：演员列表基础（已验收）

- 新增独立演员卡片分页查询和解析，保留原有演员选择器接口；演员入口使用原有可配置底部导航。手机模拟器 `emulator-5556` 连接真实服务，显示两列照片、姓名、性别、国家、年龄与影片数量；姓名升序与服务端结果一致。
- 空搜索显示空状态；搜索使用 300ms 去抖。模拟延迟且忽略取消的旧请求，验证新搜索结果不会被覆盖。
- 模拟 41 名演员验证第二页、失败、重试和结束状态；测试设备为 `emulator-5556`，`PerformersRouteDeviceTest` 两项通过。
- 真实断网刷新时页面顶部显示错误和重试，原有卡片保留；恢复网络后重试成功。Debug 构建与 `StashPerformerContractTest` 通过。
- 当前真实数据不足 41 名，跨页测试使用脱敏模拟数据；收藏写入、点击详情和数量入口分别属于后续 MVP-4 和 MVP-2/3。
- Gradle 设备测试任务受到原有挂起模拟器的测试结果锁文件影响，改用构建好的测试 APK 在 `emulator-5556` 直接运行同一 AndroidJUnitRunner，未删除原设备数据。

## MVP-2：演员资料详情（已验收）

- 演员照片/姓名和影片数量分别进入详情资料与影片汇总区域；返回后恢复已浏览的列表位置。详情和汇总通过独立请求获取，资料支持整体收起、详细资料展开、别名展开、大图和网页外链；空资料隐藏。
- `emulator-5556` 连接真实服务验证资料、影片数量/总时长/总大小、详情展开、照片大图、数量入口和返回位置；实际服务的完整详情字段查询通过。
- `PerformerDetailRouteDeviceTest` 两项通过，分别模拟资料和汇总失败并重试，确认另一部分仍能显示；演员查询解析单元测试、Debug 构建通过。
- 影片区域此阶段使用开发提示；真实影片卡片与播放在 MVP-3 完成并验收。收藏/评分写入在 MVP-4 完成。
