# Changelog

本项目所有显著变更记录于此。格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。

## [Unreleased]

### Added

- **UI 令牌体系与主题包设计定稿（v2 深化）**（文档 `docs/DESIGN_TOKENS.md`，实施随 T8）：
  - 三层令牌模型（Primitive / Semantic / Component）+ 各层**准入标准**；语义色面板
    （品牌/表面/文本/边线/状态）、字体字重/行高/字族、动效档位基线；
  - 决策 D5–D11：风格包可切换/可插拔/高度自定义；`dynamicColor` 默认关；先定稿后实施；
    **语义令牌为唯一 source of truth、M3 全槽位映射 + 镜像断言**（D8）；theme.json v1
    与 W3C DTCG 机械可映射（D9）；A11y 为硬约束（D10）；令牌新增走评审（D11）；
  - 深化章节：Kotlin 类型设计（编译期完整性）、delta 合并精确语义、WCAG 2.1 对比度
    配对矩阵、`staticCompositionLocalOf` 重组语义与切换行为、Dialog 作用域、线程模型、
    M3 权威映射表、测试策略（六项，底线机器保证）、反模式治理（防语义膨胀/原始值泄漏）。

## [0.2.0.2] — 2026-09-01 · tag [v0.2.0.2](https://github.com/Lisir2002/Deepcore-Code/releases/tag/v0.2.0.2) · versionCode 20002

### Added

- **工具与技能层设计定稿**（文档 `docs/TOOLS_SKILLS.md`，随 M1 实施）：
  - 双标准铁律：工具互操作对齐 **MCP**（规范基线 2025-11-25，官方 Kotlin SDK），
    技能包对齐 **Agent Skills 开放标准**（agentskills.io，`SKILL.md` + 渐进披露三层加载），
    禁止自造私有协议，保证外部生态插件可直接适配。
  - 模块规划：`core:mcp` 新模块（MCP Client，Streamable HTTP 先行）；
    `core:agent` 新增 `skill/` 子包（保持纯 Kotlin 零第三方依赖）。

- **工具与技能层 T1–T4 实施完成**（代码随 M1，未发版）：
  - T1 `core:model`：`ToolSpec` 扩展（title/origin/sourceId/annotations）+ `ToolOutput`
    新增 `Image` / `ResourceLink` / `Structured` 三种形态（向后兼容）。
  - T2 `core:agent/skill/`：`SkillParser`（frontmatter 校验）/ `SkillLoader`（多 root
    按目录名去重）/ `SkillInjector`（L1 段）三件套 + 单测全绿。
  - T3 主循环两处感知点：`DefaultToolRegistry.specs()` 改为 BUILTIN 优先 + name 稳定排序
    （保 prompt cache）；`DefaultAgentRuntime` 注入 system prompt 技能段；
    `TranscriptReconstructor` 补三种新产物渲染。
  - T4 `core:mcp`：`McpClient` 接口 + `HttpJsonRpcMcpClient`（OkHttp 自实现，JSON-RPC 2.0
    握手 / SSE data 抽取 / `Mcp-Session-Id` 回写）+ `McpToolBridge`（tools/list→ToolSpec、
    callTool→ToolResult、风险映射：未信任=NETWORK、受信任按 hints 降档、命名空间 `server__tool`）
    + `McpServerManager`（connectAll / list_changed 重拉 / 单点失败不传染）+ FakeMcpClient /
    MockWebServer 单测（15 例全绿）。
  - **偏差**：设计稿原定官方 Kotlin SDK（`io.modelcontextprotocol:kotlin-sdk`），但其 0.6+
    需 Kotlin 2.2+、与本项目锁定的 Kotlin 2.0.21 元数据硬冲突，故改为 OkHttp +
    kotlinx-serialization 自实现协议级兼容客户端；`McpClient` 接口隔离传输，未来升级
    Kotlin 后换官方 SDK 仅需新增一个实现类。
  - 调度定稿：Skill 永不伪装成 Tool（L1 元数据注 system prompt、L2 用现有 read 工具
    触发加载、L3 资源按需）；MCP 工具经桥接进统一 `ToolRegistry`，annotations 视为
    不可信、风险裁决只认本地 `PermissionGate`；turn 内工具清单快照以保证 prompt cache 命中。

- **T5 `:app` 装配与设置页（代码随 M1，未发版）**：
  - `core:mcp` 新增 `McpServerConfigStore` 接口（纯 Kotlin、零 Android 依赖），`:app` 提供
    `AndroidMcpServerConfigStore`（`filesDir/mcp/servers.json`，kotlinx-serialization 本地 DTO
    映射，构造期同步读供非阻塞装配）；`McpServerManager` 增加 `addServer`/`updateServer`/
    `removeServer`/`reconnectAll`/`snapshotConfigs` 支持设置页运行时热插拔。
  - Koin 装配：`McpServerConfigStore` + `McpServerManager`（clientFactory=HttpJsonRpcMcpClient、
    configs 取 store 非阻塞快照）+ `McpCompositeToolRegistry`（内置工具与 MCP 工具实时聚合，
    沿用 BUILTIN 优先 + name 稳定排序）+ `SkillLoader`/`SkillInjector` 接入
    `DefaultAgentRuntime.skillSectionProvider`；`DeepCoreCodeApp` 启动触发 `connectAll`。
  - 新建 `:feature:settings` 模块：MCP server 管理 CRUD 表单（增删、信任切换、重连、状态展示），
    `designsystem` 同步扩展 `AppText`/`AppTextField`/`AppSwitch`（封装 Material3 输入组件，
    业务层不直接触碰 Material3，符合 `:lint` 守卫）；`MainActivity` 经状态切换接入导航。
- **T6 架构文档同步（未发版）**：
  - `ARCHITECTURE.md` 模块图补 `:feature:settings`，`:core:mcp` 由「官方SDK」改为「自实现」
    并加注弃用官方 SDK 的原因（SDK 0.6+ 要求 Kotlin 2.2+，与本项目 2.0.21 元数据不兼容）；
  - 「能力即接口」补 `SkillLoader`/`SkillInjector`/`McpClient`/`McpServerConfigStore`，
    并写明「接口在 `:core:mcp`、Android 实现在 `:app`」——避免 `feature:settings` 反向依赖 `:app`；
  - 「扩展点」表新增「接一个 MCP 服务器」「加一种 MCP 传输（stdio/WebSocket）」两行，
    MCP 工具行更新为 `McpCompositeToolRegistry` 聚合语义（BUILTIN 优先 + name 稳定排序）；
  - 「组件库是唯一出口」表补 `AppText`/`AppTextField`/`AppSwitch`，并写明「缺件先补组件库，
    不要在 feature 层开洞」；
  - 「当前状态」表补 `:core:mcp`（15 例）、`:feature:settings`，`:core:agent` 用例数更新为
    17（主循环 6 + Skill 11），并标注 `:app`/`:feature:settings` 仅 CI 验证；
  - 技术栈补 OkHttp / Koin 4.0.0 / SQLDelight 2.x 与两项开放标准（MCP 2025-11-25、Agent Skills）；
    MCP 规范版本统一为 `2025-11-25`（与 `TOOLS_SKILLS.md` 设计定稿一致）。

### Changed

- **版本号迁移至四段式 `X.Y.Z.W`**（规范见 `Version.md` 一、二、三）：
  - `versionName` 由 `MAJOR.MINOR.PATCH` 改为 `MAJOR.MINOR.PATCH.BUILD`；
  - `versionCode` 编码改为 `X*1_000_000 + Y*10_000 + Z*100 + W`，W 为全局单调递增构建号，
    每次发版（含 RC）+1，永不重置；
  - 基线锚点设为 `0.1.4.0 / 10400`（v0.1.4 实为三段式 `versionCode=5`，10400 > 5 保证升级覆盖）。
- 新增 **确认门禁**：打正式 tag 前必须经用户确认，否则自动发布 `X.Y.Z.W-rcN` 预发行版本
  （`release.yml` 按 tag 是否含 `-rc` 自动标记 prerelease）。
- 新增 `scripts/release_helper.py`：统一计算下一版本（current/plan/code/rc-number），
  取代手改 `versionCode`/`versionName`，杜绝注释误匹配与编码笔误。
- `release.yml` 新增 **tag ↔ versionName 一致性校验**步骤，不一致即中止发版。

### Fixed

- **修复 T1–T6 遗留的 CI 红（M1 工具/技能层代码此前从未在 CI 上编译通过）**：
  - `app/build.gradle.kts` 补 `implementation(project(":core:mcp"))`。DI 里直接构造
    `McpServerManager` / `McpCompositeToolRegistry`，却只靠 `:feature:settings` 传递依赖
    （`implementation` 不向外暴露），`:app` 全片 `Unresolved reference`——
    这是 android-build / design-guard / release-build 三个 job 同红的根因。
  - `AppModule.kt` 补 `org.koin.core.qualifier.named` 导入；
    `DeepCoreCodeApp.kt` 补 `org.koin.core.component.get` / `inject` 导入。
  - `designsystem` 的 `AppTextStyle.toTextStyle()` / `AppTextTone.toColor()` 补
    `@Composable`：二者读 `MaterialTheme.typography` / `colorScheme` / `appColors()`，
    在普通函数里读组合状态会直接编译失败。
  - `RenderBlockView.ToolOutputView` 补 `Image` / `ResourceLink` / `Structured` 三个产物分支。
    T1 给 `ToolOutput` 加了这三种形态却没补渲染落点，`when` 不穷尽 → 编译失败，
    这是 CI 自 b3164b5 起连红的**直接**根因。
- **补齐 CI 覆盖缺口**：
  - `ci.yml` 的 core-test 纳入 `:core:mcp:test`（此前 15 例单测从未在 CI 执行，
    「测试绿」实际只覆盖旧模块）；
  - `ci.yml` 的 design-guard 纳入 `:feature:settings:lintDebug`（新模块此前不受设计系统守卫约束）；
  - `release.yml` 的发布前测试同步纳入 `:core:mcp:test`；
  - 两处均补注释写明「新增模块必须同步进本清单」。
- **修复 CI 第 5 轮暴露的第二层编译错误（前一层修好后才浮出）**：
  - `McpServerConfigStore` 接口补声明 `fun current(): List<McpServerConfig>`（非阻塞内存
    快照）。`AndroidMcpServerConfigStore` 早已按此契约实现（构造期同步读 + `override fun
    current()`），`AppModule` 装配与 `SettingsViewModel` 也按契约调用，唯独接口本身漏了
    声明——`:feature:settings` / `:app` 共 4 处 `Unresolved reference 'current'` 连锁。
  - `SettingsScreen.kt` 删除 `import androidx.compose.foundation.layout.weight`：
    `weight` 是 `RowScope`/`ColumnScope` 的成员扩展，无需（也不能）顶层 import，
    该 import 恰好解析到 Compose internal 属性，报 "Cannot access ... internal in file"。
- **修复 `release_helper.py` 无法解析自身写出的 rc 版本名**：`parse_name` 先剥离 `-rcN`
  后缀再按四段解析——上一版写入 `0.2.0.1-rc1` 后，`plan` 子命令必然崩溃（rc → 下一版
  路径此前从未被走过）。
- **修复 CI 第 6 轮暴露的 `:app` 编译错误（错误修到第三层，`:app` 首次真正进入编译）**：
  - `app/build.gradle.kts` plugins 补 `kotlin.serialization`：`AndroidMcpServerConfigStore`
    的 `@Serializable` 依赖该插件在编译期生成 `serializer()`，缺失导致 22 处连锁类型
    推断错误（`Unresolved reference 'serializer'` 等）；
  - dependencies 补 `libs.okhttp` / `libs.kotlinx.serialization.json`：core:mcp 对这两个
    库均为 `implementation`（不向外传递），而 `:app` 直接构造 `HttpJsonRpcMcpClient`
    （默认参数引用 `OkHttpClient` 类型）——谁直接用谁声明。

### Added

- **沙箱本地 Android 编译环境**：Android SDK（platform-35 / build-tools 34.0.0 + 35.0.1 /
  platform-tools）+ Gradle 8.9（镜像下载）+ JDK 17（与 CI 对齐），依赖经阿里云 google 镜像解析。
  `PLAN.md` 里「沙箱无 Android SDK，本地无法自验」的限制就此解除：推送前可本地跑
  `:app:compileDebugKotlin`，不必靠推 commit 等 CI 盲试。

## [0.1.4] — 2026-08-31 · tag [v0.1.4](https://github.com/Lisir2002/Deepcore-Code/releases/tag/v0.1.4) · versionCode 5

### Added

- **数据层 M0.6 落地 —— SQLite 经 SQLDelight 2.x 接管持久化**（设计见 `DATA_LAYER.md`）：
  - `:core:data` 引入 SQLDelight 2.0.2 + sqlite-3-18 方言；`.sq` 定义 `events`/`sessions` 表，
    生成 `DeepCoreDatabase` 类型安全查询。
  - 门面 SPI `SqliteDatabase`（`transaction` / `observe` / `rawQuery` / `rawExecute`，
    IO 统一切单线程，主线程永不碰库）。
  - 扩展协议 `TableModule` 注册制 + `SchemaManager` 版本链：新功能加表/加字段不改核心框架。
  - `SQLiteEventStore`：events 与 sessions 同事务更新，`EventCodec` 多态事件 JSON 编解码。
  - `:app` 装配 `AndroidSqliteDriver` + `dataTableModules` 注册表，`EventStore` 一行切换。
  - JVM 全链路单测 **21/21 绿**（含迁移链、多态往返、会话索引、事务一致性）。
  - CI：core-test job 纳入 `:core:data:test`。

## [0.1.3] — 2026-08-31 · tag [v0.1.3](https://github.com/Lisir2002/Deepcore-Code/releases/tag/v0.1.3) · versionCode 4

### Fixed

- 恢复正式签名方案 **v1+v2+v3 显式三开**（`app/build.gradle.kts`），
  修复 v0.1.2 起签名块中 **v3 缺失**的回归。

### 决策

- 经 APK Signing Block 字节级解析证实：v0.1.1（三显式开启）v1/v2/v3 全部命中，
  此前"V2 缺失"是验证脚本把 v2 方案 ID `0x7109871a` 的小端字节序写错
  （`1a 79 08 71` ≠ 正确的 `1a 87 09 71`）造成的误判；
  而 v0.1.2（仅显式 v1）才真正丢失 v3 —— **AGP 8.7.3 + minSdk 26 的默认签名方案不含 v3**。
  结论：三个方案一律显式开启，验证一律走 `scripts/check_apk_signing.py`。

## [0.1.2] — 2026-08-31 · tag [v0.1.2](https://github.com/Lisir2002/Deepcore-Code/releases/tag/v0.1.2) · versionCode 3

### Changed

- 版本对齐：`versionName` 0.1.1 → 0.1.2，`versionCode` 2 → 3。

### Security（回归，已于 0.1.3 修复）

- ⚠️ 签名配置被改为仅显式 `enableV1Signing = true`（基于错误验证结论），
  产物实际为 v1+v2，**v3 缺失**。v1 证书指纹与 `SignatureGuard` 校验一致。
- 期间确认 AGP 8.7.3 正确属性名为 `enableV1/V2/V3Signing`；
  `v1SigningEnabled`/`isV1SigningEnabled` 等写法编译报 Unresolved reference。

## [0.1.1] — 2026-08-31 · tag [v0.1.1](https://github.com/Lisir2002/Deepcore-Code/releases/tag/v0.1.1) · versionCode 2

### Added

- **正式签名体系**：PKCS12 密钥（仓库外物理隔离）+ GitHub Secrets 注入 + v1+v2+v3 三方案开启
  （字节级复验确认 0.1.1 产物三方案齐备、证书指纹与 SignatureGuard 逐位一致）。
- **代码加固**：R8 混淆 + 资源收缩；CI 断言 mapping 非空（< 5 条直接失败）。
- **清单加固**：`allowBackup=false`、`usesCleartextTraffic=false` + networkSecurityConfig、
  Release 剥离 `android.util.Log`。
- **运行时防篡改**：`SignatureGuard` 启动比对 APK 证书 SHA-256 与官方指纹。

### Fixed

- CI 强制签名判断从 `CI=true` 改为 `CI=true && taskNames 含 Release`——
  GitHub 给每个 job 注入 `CI=true`，旧逻辑导致 core-test 配置阶段抛 `GradleException`。

### Changed

- 版本对齐：`versionCode` 1 → 2。

## [0.1.0] — 2026-08-31 · tag [v0.1.0](https://github.com/Lisir2002/Deepcore-Code/releases/tag/v0.1.0) · versionCode 1

### Added

- M0 架构地基（详见 [ARCHITECTURE.md](ARCHITECTURE.md)）：
  - 多模块工程：`core:{model,agent,data,uistate}`（纯 Kotlin 可测底座）、
    `core:platform`、`designsystem`（唯一 Material3 出口）、`lint`（设计守卫）、`feature:chat`、`app`。
  - 事件驱动主循环 + 全部能力 SPI（`ModelProvider`/`Tool`/`Sandbox`/`Workspace`/`PermissionGate`…）。
  - append-only 事件日志存储，会话重放即恢复。
  - 事件 → 渲染块归约器（含 6 个 JVM 单测）与 Lint 编译期守卫。
- CI 流水线：`ci.yml`（core-test / android-build / design-guard）、`release.yml`（tag 触发发版）。
- 全量重命名至 `com.deepcode.agent`，应用名 DeepCore-Code，自适应图标。

### Fixed

- Android 编译两处根因（包名对齐、lambda `it` 遮蔽）后首个可编译 tag。

[Unreleased]: https://github.com/Lisir2002/Deepcore-Code/compare/v0.2.0.2...HEAD
[0.2.0.2]: https://github.com/Lisir2002/Deepcore-Code/compare/v0.2.0.1-rc1...v0.2.0.2
[0.1.4]: https://github.com/Lisir2002/Deepcore-Code/compare/v0.1.3...v0.1.4
[0.1.3]: https://github.com/Lisir2002/Deepcore-Code/compare/v0.1.2...v0.1.3
[0.1.2]: https://github.com/Lisir2002/Deepcore-Code/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/Lisir2002/Deepcore-Code/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/Lisir2002/Deepcore-Code/releases/tag/v0.1.0
