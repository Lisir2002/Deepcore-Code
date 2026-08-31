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
- [ ] 实施清单 **T6–T7 待做**：T6 文档同步（本文件/PLAN/CHANGELOG 已随 T5 更新）；T7 发版走四段式门禁 `0.2.0.x`。

---

## 当前焦点

**M1 能真跑**：接真实模型（OkHttp + SSE 的 `ModelProvider`），让 Demo 链路升级为真实对话。
数据层 M0.6 已随 **v0.1.4** 发布（CI 全量验证通过，`:app` 装配 `android-build` 绿），
设计见 `DATA_LAYER.md`、进度见上方 M0.6 清单。下一步直接进入 M1。

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

## 风险与依赖

| 风险 | 缓解 |
| --- | --- |
| Android SDK 依赖网络下载（沙箱不稳定） | CI 已跑通全量构建，本地通道见 docs/github-sandbox-tunnel.md |
| 密钥单点故障 | `/root/deepcode-signing/` 离线备份；丢失只能换密钥 + 全量重装（已在 RELEASING.md 声明） |
| proguard 规则回归 | mapping 断言 + Release 手测清单 |
| AI 协作者误改已调好的配置 | agent.md 铁律 + 构建脚本内注释 + 本计划约束 |
