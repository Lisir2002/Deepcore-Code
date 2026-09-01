# PLAN.md — 项目开发设计计划

> 里程碑路线与当前进展。本文回答"现在在哪、下一步去哪"；
> 架构怎么落地见 ARCHITECTURE.md，版本节奏见 Version.md。

---

## 里程碑总览

| 里程碑 | 目标 | 状态 |
| --- | --- | --- |
| **M0 架构地基** | 骨架 + 主循环 + UI 统一机制跑通完整链路 | ✅ 完成（v0.1.0） |
| **M0.5 发布基线** | 正式签名 + 加固 + CI 发版流水线 | ✅ 完成（v0.1.3） |
| **M0.6 数据层 SQLite 化** | SQLDelight 接管持久化（events/sessions 表 + TableModule 扩展协议） | ✅ 完成（v0.1.4，CI 全量验证通过） |
| **M1 能真跑** | 接真实模型（OkHttp + SSE 的 `ModelProvider`）+ **工具/技能层标准化**（MCP 客户端 + Agent Skills） | 🔜 下一步（工具/Skill 设计已定稿） |
| **M2 能用** | 工作区文件树、Diff 审阅、git 工具、会话列表页；ProotSandbox 后补 stdio MCP | ⏳ 规划中 |
| **M3 能扛** | 上下文压缩实战化、前台服务保活、数据层深化 | ⏳ 规划中 |
| **M4 扩展** | 子 Agent 并行、Plan Mode、远端沙箱、MCP Server 侧反向暴露 | ⏳ 规划中 |

---

## 已交付能力清单

### M0 架构地基（v0.1.0）

- [x] Gradle 多模块工程（L0/L1 纯 Kotlin 底座 + L2 能力层 + UI 统一层 + Lint 守卫）
- [x] 事件驱动主循环：`AgentEvent` 契约 + 流式工具输出
- [x] 会话存 append-only 事件日志，重放即恢复
- [x] 事件 → 渲染块归约器（纯 Kotlin，6 个用例）
- [x] Lint 守卫：绕过 designsystem 直接编译失败
- [x] 单测抓出真实 bug（工具执行过快时流式输出被吞）

### M0.5 发布基线（v0.1.1 → v0.1.3）

- [x] 正式签名体系：PKCS12 密钥（仓库外隔离）+ CI Secrets 注入 + v1+v2+v3 三方案
- [x] R8 代码混淆 + 资源收缩（mapping 断言：< 5 条混淆直接失败）
- [x] 清单加固：禁备份、禁明文流量、Release 剥离日志
- [x] 运行时防篡改：`SignatureGuard` 启动比对证书指纹
- [x] CI 流水线：`ci.yml`（core-test / android-build / design-guard）+ `release.yml`（tag 触发）
- [x] 产物验证脚本 `scripts/check_apk_signing.py`（字节级解析 Signing Block）
- [x] 版本验证复盘：定位 v0.1.2 缺 v3 根因（AGP 默认不含 v3），v0.1.3 修复

### 文档体系（随 v0.1.3）

- [x] README / agent.md / PLAN.md / Version.md / CHANGELOG.md / RELEASING.md 全套

### M0.6 数据层 SQLite 化（2026-08-31）

- [x] `:core:data` 引入 SQLDelight 2.0.2 + sqlite-3-18 方言（JVM 内存库即可跑测试，零 Android 依赖）
- [x] `SqliteDatabase` 门面 SPI（transaction / observe / rawQuery / rawExecute），所有 IO 切单线程
- [x] `TableModule` 注册制扩展协议 + `SchemaManager` 版本链（首装建最新形态、升级补跑迁移）
- [x] `SQLiteEventStore`：events + sessions 同事务，多态事件 JSON 编解码（`EventCodec`）
- [x] `:app` 装配 `AndroidSqliteDriver` + `dataTableModules` 注册表，一行切换 `EventStore` 实现
- [x] JVM 全链路单测 21/21 绿（含迁移链、多态往返、会话索引、事务一致性）
- [x] CI：core-test 纳入 `:core:data:test`
- [x] 设计定稿与实施记录见 `DATA_LAYER.md`

### 版本治理（2026-08-31 落地）

- [x] 版本号迁移至 **四段式 `X.Y.Z.W`**（`Version.md` 一、二、三）：`W` 为全局单调递增构建号，
  `versionCode = X*1_000_000 + Y*10_000 + Z*100 + W`；基线锚点 `0.1.4.0 / 10400`。
- [x] **确认门禁**：打正式 tag 前必须经用户确认，否则自动发布 `X.Y.Z.W-rcN` 预发行
  （`release.yml` 按 tag 是否含 `-rc` 自动标记 prerelease）。
- [x] 新增 `scripts/release_helper.py`（current/plan/code/rc-number），统一计算下一版本，
  取代手改 `versionCode`/`versionName`，并规避注释误匹配与编码笔误。
- [x] `release.yml` 新增 **tag ↔ versionName 一致性校验**步骤。

### 工具与技能层设计定稿（2026-08-31，随 M1 实施）

- [x] 确立双标准铁律：工具互操作走 **MCP**（规范基线 2025-11-25）、技能包走 **Agent Skills 开放标准**
  （agentskills.io），禁止自造私有协议——外部生态的工具/技能插件可直接适配。
- [x] 四项决策定稿：MCP Client 先行 / SKILL.md 全兼容 / `core:agent` 加 skill 子包 + 新建 `core:mcp` /
  Streamable HTTP 先行 stdio 后补。
- [x] 设计文档 `docs/TOOLS_SKILLS.md`（类型映射表、风险映射、渐进披露三层落地、调度定稿、接口草案、测试策略）。
- [x] 实施清单 **T1–T4 已完成**（设计见 `TOOLS_SKILLS.md` 九）：T1 `core:model` 扩展、T2
  `core:agent/skill/` 三件套 + 单测、T3 主循环两处感知点、T4 `core:mcp` 自实现 MCP 客户端
  （OkHttp，因官方 Kotlin SDK 需 Kotlin 2.2+ 与本项目 2.0.21 不兼容而弃用）+ 桥接/管理器/风险映射
  + 单测全绿（15 例）。
- [x] 实施清单 **T5 已完成**：`:app` DI 装配（`McpServerConfigStore` 接口 + `AndroidMcpServerConfigStore` JSON 持久化 + Koin 装配 `McpServerManager`/`McpCompositeToolRegistry` + `SkillLoader`/`SkillInjector` 接入 `skillSectionProvider` + 启动 `connectAll`）+ 新建 `:feature:settings`（CRUD 表单，designsystem 扩展 `AppText`/`AppTextField`/`AppSwitch`）。`:app` 沙箱无 Android SDK，**仅 CI 验证**，纯 Kotlin 的 `:core:mcp` 单测已绿。
- [x] 实施清单 **T6 已完成（文档同步）**：`ARCHITECTURE.md` 同步模块图（补 `:feature:settings`、
  `:core:mcp` 标注自实现并注明弃用官方 SDK 原因）、扩展点表（新增「接一个 MCP 服务器」「加一种 MCP 传输」）、
  组件库出口表（补 `AppText`/`AppTextField`/`AppSwitch`）、当前状态表（补 `:core:mcp` 15 例与
  `:feature:settings`，`:core:agent` 更新为 17 例）、技术栈与两项开放标准；MCP 规范版本全文统一为
  `2025-11-25`。`CHANGELOG.md` 补 T6 条目。
- [x] 实施清单 **T7（rc1 发布未成功）**：`0.2.0.1-rc1` 已按四段式门禁打 tag，
  但 Release 构建失败、GitHub Release 未产出。
- [x] 实施清单 **T7-fix（已完成，2026-09-01）**：修复 T1–T6 遗留的三层 CI 红（共 5+2+2 处
  根因，见下方「CI 红根因与修复」），CI 四 job 全绿（run 33443248890）；经确认门禁发
  **正式版 v0.2.0.2**（versionCode 20002），Release 构建成功、APK 经
  `check_apk_signing.py` 三绿验证 + 指纹比对一致。

---

## 当前焦点

**修红完成 ✅ M1 收官。** 2026-09-01：CI 四 job 全绿（run 33443248890），正式版
**v0.2.0.2**（versionCode 20002）经确认门禁发布，Release + APK 产出并三绿验证。
T1–T6 的工具/技能层代码至此全部经过 CI 全量验证（此前 `core-test` 一直绿，掩盖了
`:app` / `:feature:settings` 的编译失败，共三层根因，见下方表）。

**M1 下一目标——能真跑**：接真实模型（OkHttp + SSE 的 `ModelProvider`），
让 Demo 链路升级为真实对话。数据层 M0.6 已随 **v0.1.4** 发布（CI 全量验证通过，
`:app` 装配 `android-build` 绿），设计见 `DATA_LAYER.md`、进度见上方 M0.6 清单。

**UI 令牌体系已定稿（T8 待启动）**：多设计页面 / 风格包可插拔的三层令牌设计见
`docs/DESIGN_TOKENS.md`（2026-09-01 定稿 v3，16 章，决策 D5–D14）——
v1/v2 定稿令牌类型 + brand 色板（黑白灰主调、蓝紫点缀、红绿状态），v3 补齐
**行为层三系统**：交互态八态机（State Overlay 弃 ripple）、动效编排（八种转场
模式 × 档位绑定）、页面骨架（三段式五型模板）。实施清单 T8.1→（T8.2 ∥ T8.5）
→ T8.3 → T8.4 见下方。

### CI 红根因与修复（2026-09-01 接手盘点）

| 根因 | 后果 |
| --- | --- |
| `app/build.gradle.kts` 未声明 `implementation(project(":core:mcp"))`，但 DI 直接 new `McpServerManager`/`McpCompositeToolRegistry` | `:app` 全片 `Unresolved reference` → android-build / design-guard / release-build 三 job 同红 |
| `AppModule.kt`、`DeepCoreCodeApp.kt` 用了未导入的 `named()` / `inject()` / `get()` | 同上 |
| `ci.yml` 的 core-test 只跑 `agent/uistate/data` | `:core:mcp` 15 例从未执行，「测试绿」只覆盖旧模块 |
| `ci.yml` 的 design-guard 只跑 `app/chat` | `:feature:settings` 不受设计系统守卫约束 |
| `McpServerConfigStore` 接口漏声明 `current()`（实现类与调用方都按契约写，唯独接口没写） | 第 5 轮 CI 暴露：`:feature:settings` / `:app` 共 4 处 `Unresolved reference 'current'` |
| `SettingsScreen.kt` 顶层 import `layout.weight`（RowScope/ColumnScope 成员扩展不可顶层 import） | `:feature:settings` 报 "Cannot access internal in file" |
| `:app` plugins 缺 `kotlin.serialization`；okhttp/serialization-json 未显式依赖（core:mcp 均 implementation 不传递） | 第 6 轮 CI 暴露：`:app` 22 处错误（`serializer()` Unresolved、`Cannot access OkHttpClient`） |

**教训（第 5 轮）**：编译错误是分层浮出的——前一层修好后才轮到下一层。本地编译因
沙箱 40 分钟超时截断在 `:feature:settings` 之前，未能提前暴露；裁决以 GitHub CI 为准。
同轮顺带修复 `release_helper.py` 解析自身写出的 rc 版本名（`parse_name` 剥 `-rcN` 后缀）。

**沙箱能力升级**：已装 Android SDK（platform-35 / build-tools 34.0.0+35.0.1 / platform-tools）
+ Gradle 8.9 + JDK 17（与 CI 对齐），依赖走阿里云 google 镜像。
现在本地可跑 `:app:assembleDebug`，不再只能靠推 commit 等 CI 盲试。

**版本治理已就位**：后续每次发版走四段式 `X.Y.Z.W` + `scripts/release_helper.py`，
正式版需用户确认、否则自动发 `X.Y.Z.W-rcN` 预发行（详见 `Version.md` 三、确认门禁）。
M1 首发预计为 `0.2.0.x`。

## M1 设计要点（数据层之后）

1. **`OkHttpProvider : ModelProvider`**：SSE 流式解析，映射到 `ThinkingDelta` /
   `MessageDelta` 事件；超时/重试策略进 `ContextPolicy` 之外的独立配置。
2. **模型配置面**：API Key 存 `EncryptedSharedPreferences`；`PermissionGate`
   增加网络访问确认（复用现有风险等级模型）。
3. **工具与技能标准化**（设计见 `TOOLS_SKILLS.md`，实施 T1–T4 已完成）：`core:mcp`
   （**OkHttp 自实现** MCP 客户端，弃用官方 Kotlin SDK——因其需 Kotlin 2.2+ 与本项目
   2.0.21 元数据冲突）+ `core:agent/skill/`（Agent Skills 标准解析与渐进披露注入）；
   主循环仅两处感知点（specs 快照、system prompt 技能段），其余零改动。
4. **验收标准**：真实模型流式对话跑通一轮完整工具调用；`core:agent` 全部用例保持绿；
   事件序列可重放恢复；MCP 内存服务器端到端单测绿。
5. **发布口径**：M1 功能进 `0.2.0.x`（次版本号 + 全局构建号 W，符合 Version.md 语义）。

## T8 实施清单（UI 令牌体系与主题包，设计见 `docs/DESIGN_TOKENS.md`）

- [ ] **T8.1 令牌层扩展**：`AppTokens` 聚合（colors/typography/motion）+ 语义色面板补全
  + 字重/行高/字族 + `AppTextStyle` 角色扩展；`dynamicColor` 删除（D6）。
- [ ] **T8.2 主题包机制（编译期）**：`AppThemeSpec` + brand 内置包 + `StyleController`
  （StateFlow + 持久化）+ 设置页风格切换 UI；`AppTheme(spec)` 参数化。
- [ ] **T8.5 行为层落地（v3 新增）**：`Modifier.appStateLayer()` 统一封装 +
  `AppTransitions` 全局转场配置 + `AppTopTabs`/`AppNavBar`/`AppModalSheet`/`AppDialog`
  新组件 + 页面骨架五型化（Chat/Tabbed/Nav/Detail/Form）+ insets 统一消化。
- [ ] **T8.3 运行时可插拔**：`theme.json` v1 解析 + `ThemePackLoader`（assets/filesDir
  双根，复用 SkillLoader 模式）+ 对比度/触控校验兜底 + 设置页导入入口。
- [ ] **T8.4 lint 扩展**：拦业务层 `Color(0x…)` 字面量与裸 `TextStyle` 构造。
- 节奏：T8.1 →（T8.2 ∥ T8.5）→ T8.3 → T8.4；T8.2 与 T8.5 相互独立可并行；
  T8.1 可与 M1 并行；T8.3/T8.4 排在 M1 之后（真实模型优先）。

## 风险与依赖

| 风险 | 缓解 |
| --- | --- |
| Android SDK 依赖网络下载（沙箱不稳定） | CI 已跑通全量构建，本地通道见 docs/github-sandbox-tunnel.md |
| 密钥单点故障 | `/root/deepcode-signing/` 离线备份；丢失只能换密钥 + 全量重装（已在 RELEASING.md 声明） |
| proguard 规则回归 | mapping 断言 + Release 手测清单 |
| AI 协作者误改已调好的配置 | agent.md 铁律 + 构建脚本内注释 + 本计划约束 |
