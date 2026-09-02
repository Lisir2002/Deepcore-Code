# Changelog

本项目所有显著变更记录于此。格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。

## [v0.4.1.3-rc3] — 2026-09-02 · versionCode 40103（预发行）

> **审计修复批次 + 接入真实模型 Provider**。本轮按深度审计清单落地 1/3/4/5/6 项：
> 接入基于 OkHttp 的 OpenAI 兼容模型 Provider 打通 Agent 主循环；MCP 服务器 URL 协议
> 白名单校验下沉到客户端构造入口（防御纵深）；沙箱命令执行增加 60s 真实超时；权限审批
> 审计日志显式记录被批准/拒绝的工具名，且仅当弹窗确实唤醒一次在途审批时才把
> ALWAYS/SESSION 策略写入策略库；统一 MCP 协议版本 2025-11-25，并为核心事件/工具契约
> 补齐序列化往返单测，守住「旧库新读」兼容红线。

### Added

- **接入真实模型 Provider（OkHttp）**（[OkHttpProvider.kt](app/src/main/kotlin/com/deepcode/agent/model/OkHttpProvider.kt)）：
  实现 OpenAI 兼容 `chat/completions` SSE 协议（覆盖 GPT / DeepSeek / 通义），流式解析
  思考增量（`reasoning_content`）、正文、函数调用（按 index 拼接）、用量与停止原因，
  统一映射为 `CompletionChunk`，Agent 主循环对供应商差异无感。新增
  [ModelEndpointConfigStore.kt](app/src/main/kotlin/com/deepcode/agent/model/ModelEndpointConfigStore.kt)
  （SharedPreferences 存 baseUrl / apiKey / model），DI 层配置齐全时主循环走真实模型，
  未配置则回退 DemoProvider 保 UI 全链路可跑（密钥加密存储列为独立待办，不在本批内）。

### Fixed

- **（安全）MCP 服务器 URL 协议白名单下沉**（[McpClient.kt](core/mcp/src/main/kotlin/com/deepcode/core/mcp/McpClient.kt)）：
  `HttpJsonRpcMcpClient` 构造入口校验协议必须为 http/https 且主机名非空，杜绝
  `file://` / `content://` / `javascript://` 等协议跳转，与 UI 层校验构成双重防线。
- **沙箱命令真实超时**（[CommandWhitelistSandbox.kt](core/platform/src/main/kotlin/com/deepcode/core/platform/sandbox/CommandWhitelistSandbox.kt)）：
  进程执行改用 `process.waitFor(timeout)` 施加 60s 超时，超时后先后 `destroy()` 与
  `destroyForcibly()` 强杀，避免恶意或挂起命令无限期阻塞线程。
- **权限审批审计日志补回工具名**（[InteractivePermissionGate.kt](core/agent/src/main/kotlin/com/deepcode/core/agent/InteractivePermissionGate.kt)）：
  修复在清空 `pendingCallValue` 前未暂存工具名、导致「批准/拒绝 XXX 工具调用」日志恒为
  null 的问题，安全审计可追溯。
- **策略持久化时机修正**（[DefaultAgentRuntime.kt](core/agent/src/main/kotlin/com/deepcode/core/agent/DefaultAgentRuntime.kt)）：
  仅在 `gate.respond()` 确实唤醒一个在途审批（返回 true）时才写入 ALWAYS/SESSION 策略，
  杜绝把从未真正执行的调用长期自动放行。
- **核心事件/工具契约序列化往返**（[EventRoundTripTest.kt](core/model/src/test/kotlin/com/deepcode/core/model/EventRoundTripTest.kt)）：
  为事件与 Tool/Attachments 落盘契约补齐「编码→解码→等值」单测，守住数据持久化兼容。

### Changed

- **MCP 协议版本统一 2025-11-25**：客户端握手与文档声明一致，避免版本协商失败。

---

## [v0.4.1.3-rc2] — 2026-09-01 · versionCode 40103（预发行）

> **交互视觉修复与底栏美化**。修复「点击 / 长按卡片出现黑色方角黑边」：
> `appStateLayer` 的交互 overlay 原先按组件外包矩形整块绘制，圆角组件（卡片 / 底栏项 /
> 按钮 / 输入框图标等）按压或长按时会在四角露出黑色方边。overlay 现改为按组件形状裁剪，
> 全 App 所有接入 `appStateLayer` 的组件一次性修复。同时美化底栏 `AppNavBar`：
> 图标均布全宽、尺寸统一 24dp，选中态改为主色 12% 圆角底 + 主色图标 / 标签，并加入
> 颜色平滑过渡与选中图标弹性缩放，视觉交互更跟手。

### Fixed

- **点击 / 长按卡片出现黑边**（[AppInteraction.kt](designsystem/src/main/kotlin/com/deepcode/designsystem/behavior/AppInteraction.kt)）：
  `Modifier.appStateLayer()` 新增 `shape` 参数，overlay 绘制前按组件形状裁剪
  （`shape.createOutline` + `clipPath`），圆角组件按压 / 长按时不再露方角黑边。
  同步为 `AppCard` / `AppPrimaryButton` / `AppSecondaryButton` / `AppTextButton` /
  `AppTopTabs` / `AppNavBar` / `AppTextField` 清空钮 / `AppSwitch` / `AppDropdownMenu` /
  `AppCheckbox` / `AppRadio` / 顶栏返回钮 / 输入坞收发钮等所有接入点传入对应形状。

### Changed

- **底栏美化**（[AppTopTabsAndNavBar.kt](designsystem/src/main/kotlin/com/deepcode/designsystem/components/scaffold/AppTopTabsAndNavBar.kt)）：
  `AppNavBar` 图标均布全宽（`weight(1f)`）、统一 24dp（`iconL`），选中态改为主色
  （`appColors().primary`）12% 圆角底 + 主色图标 / 标签，未选中用 `textTertiary`；
  图标颜色与选中缩放（spring 弹性）平滑过渡。

---

## [v0.4.1.2-rc1] — 2026-09-01 · versionCode 40102（预发行）

> **重命名修复**。修复「修改对话名后保存无效果」：列表标题取「索引标题」优先，
> 归约器自动标题回落兜底，用户改名不再被事件流自动标题覆盖。

### Fixed

- **重命名对话保存后不生效**（[ConversationViewModel.kt](feature/chat/src/main/kotlin/com/deepcode/feature/chat/ConversationViewModel.kt)）：
  列表项标题原先 `summary.title.ifBlank { idx.title }` —— 只要会话有消息，
  归约器从首条用户输入生成的自动标题永远非空，把 `renameSession` 写进会话索引的
  新名字压了下去（改名其实已持久化，只是 UI 不显示）。已改为索引标题优先：
  `idx.title.ifBlank { summary.title }`，用户改名即刻生效；未改名的会话仍回落自动标题。
  会话索引的 `insertSession` 为 `INSERT OR IGNORE`，不会在后续消息追加时覆盖改名结果。

---

## [v0.4.1.1] — 2026-09-01 · versionCode 40101

> **会话隔离 + 列表交互重构**。修复「新建对话进入历史会话、多会话串扰」的隔离问题；
> 对话列表交互从左滑改为长按操作面板，卡片撑满横向屏幕；修复操作按钮无响应的回归。

### Fixed

- **新建对话进入历史会话 / 会话未隔离**：根因是 `DemoProvider` 内部可变 `round` 计数在
  DI 中以共享单例装配，多个会话复用同一实例导致行为互相影响、输出雷同；`ChatViewModel`
  也未按会话隔离。修复：
  - [AppModule.kt](app/src/main/kotlin/com/deepcode/agent/di/AppModule.kt) 的
    `AgentRuntimeFactory` 改为在工厂内为每个会话 `new DemoProvider()`，状态随会话隔离；
  - [ChatScreen.kt](feature/chat/src/main/kotlin/com/deepcode/feature/chat/ChatScreen.kt) 用
    `koinViewModel(key = conversationId)` 按会话 id 隔离 ViewModel，新对话绝不串到旧会话历史。
- **对话列表交互重构**（[ConversationList.kt](feature/chat/src/main/kotlin/com/deepcode/feature/chat/ConversationList.kt)）：
  - 取消左滑（移除 `AppSwipeReveal`），改为**长按列表项弹出 `AppModalSheet` 操作面板**
    （查看 / 重命名 / 删除）；
  - 列表卡片 `AppCard` 撑满横向屏幕（边距自适应），不再半卡露出操作区；
  - 修复「删除 / 改名 / 查看」按钮点击无响应：操作统一收敛到长按面板的 `SheetActionRow`，
    直接绑定 `ConversationViewModel` 的 `rename` / `delete` / `onOpenConversation`。
- **设计系统**（[AppComponents.kt](designsystem/src/main/kotlin/com/deepcode/designsystem/components/AppComponents.kt)）：
  `AppCard` 新增 `onLongClick` 支持（`combinedClickable`），点击 / 长按同时可用。

---

## [v0.4.0.7] — 2026-09-01 · versionCode 40007

> **对话列表布局重构与功能建设**。骨架页面从"纯静态"走向"数据打通"：会话列表接入真实数据源，
> 引入多会话能力，列表项信息加强（标题 + 预览 + 相对时间 + 状态角标 + 模型标识）。
> 布局上保持「整卡横满，左滑半卡露出操作区」交互，不改设计语言。

### Added

- **会话索引数据层**（[EventStore.kt](core/data/src/main/kotlin/com/deepcode/core/data/EventStore.kt)）：
  - 新增 `observeSessions()` / `createSession()` / `renameSession()`，会话列表只读索引、不重放事件流；
  - SQLite 实现（[SQLiteEventStore.kt](core/data/src/main/kotlin/com/deepcode/core/data/event/SQLiteEventStore.kt)）把「写事件 + 维护索引」放进同一事务，索引与事件流永不脱节；
  - 内存实现（[InMemoryEventStore.kt](core/data/src/main/kotlin/com/deepcode/core/data/InMemoryEventStore.kt)）行为对齐，供单测与演示。
- **会话摘要归约器**（[SessionSummaryReducer.kt](core/uistate/src/main/kotlin/com/deepcode/core/uistate/SessionSummaryReducer.kt)）：
  纯 Kotlin 把事件流归约为列表项所需的标题 / 预览 / 状态角标，JVM 可单测（8 例）。
- **相对时间格式化**（[RelativeTime.kt](core/uistate/src/main/kotlin/com/deepcode/core/uistate/RelativeTime.kt)）：
  "刚刚 / N分钟前 / N小时前 / 昨天 / M月d日"，6 例单测覆盖。
- **会话工厂（多会话）**（[AgentRuntime.kt](core/agent/src/main/kotlin/com/deepcode/core/agent/AgentRuntime.kt)）：
  新增 `AgentRuntimeFactory` 按 sessionId 创建 runtime；DI 把「构造 runtime 的全部依赖」打包进工厂，
  [ChatViewModel.kt](feature/chat/src/main/kotlin/com/deepcode/feature/chat/ChatViewModel.kt) 由导航参数 `conversationId` 取对应会话。
- **对话列表接入真实数据**（[ConversationViewModel.kt](feature/chat/src/main/kotlin/com/deepcode/feature/chat/ConversationViewModel.kt) +
  [ConversationList.kt](feature/chat/src/main/kotlin/com/deepcode/feature/chat/ConversationList.kt)）：
  - 列表项信息加强：标题 + 预览 + 相对时间 + 状态角标（运行中/待授权/失败）+ 模型标识槽位；
  - 重命名走模态面板（真实落库），删除走确认弹窗（清事件 + 索引），新建对话 `createSession` 后自动跳转进新会话。
- **导航参数化**（[AppNavRoot.kt](app/src/main/kotlin/com/deepcode/agent/nav/AppNavRoot.kt)）：
  `chat/{conversationId}` 路由 + 列表/新建双入口跳转，会话页由会话 id 驱动。

### 决策

- **列表数据源选「索引 + 事件归约」而非全量重放**：会话少时逐会话读一次事件可接受，
  会话变多后再把预览/状态落到索引列（schema v2）。
- **多会话落「会话工厂」而非共享单例 runtime**：一个会话一个 `AgentRuntime`，互不干扰，
  进程被杀重建后由事件日志 100% 还原界面。

---

## [v0.3.0.6] — 2026-09-01 · versionCode 30006

> **对话列表首页骨架落地**。首个骨架页面：顶栏 + 列表 + 卡片左滑操作，全部走设计系统组件，为后续「骨架页面系列」定下槽位写法。

### Added

- **对话列表（首页）骨架页面**（[ConversationList.kt](feature/chat/src/main/kotlin/com/deepcode/feature/chat/ConversationList.kt)）：
  - 顶栏左侧标题「对话」，右侧「新建对话」图标按钮（走 `AppScaffold` 槽位，页面不新增样式）；
  - 列表卡片左滑半卡露出右侧操作区，重命名 / 删除 / 查看 三个图标按钮（新增设计系统组件 `AppSwipeReveal`，骨架内首个自定义交互组件）；
  - 重命名走模态面板输入，删除走危险操作确认弹窗，查看即打开对话；「新建对话」已接线到 `chat/new` 路由。
- **设计系统**：新增 `AppSwipeReveal`（左滑露出操作区——整卡左滑半卡、过半自动吸附展开 / 未过半回弹收起；业务层禁止自拼该交互）。

---

## [v0.2.2.5] — 2026-09-01 · versionCode 20205

> **修复启动闪退**。崩溃捕获（CrashVault）上报的 `IndexOutOfBoundsException` 根因定位：
> SQLDelight 参数绑定索引写错导致 `StyleController` 初始化即崩，App 无法进入首帧。

### Fixed

- **P0：启动闪退 `Index 1 out of bounds for length 1`（`StyleController` 创建失败）**：
  [StylePersistence.kt](app/src/main/kotlin/com/deepcode/agent/di/StylePersistence.kt) 的
  `rawQuery`/`rawExecute` 参数绑定误用 1-based 索引，而 SQLDelight 2.0.2 Android 驱动
  `AndroidQuery` 以 **0-based** 直接对定长 `ArrayList` 执行 `set(index, ...)`——
  `parameters=1` 时 `bindString(1, ...)` 越界抛 `IndexOutOfBoundsException`，Koin 无法
  创建 `StyleController` 单例，`MainActivity` 首帧崩溃。修正为 `bindString(0, ...)`，
  写路径同步改为 `bindString(0, ...)` / `bindString(1, ...)`（与
  `core:data` 既有单测的 0-based 约定一致）。

---

## [v0.2.2.4] — 2026-09-01 · versionCode 20204

> **日志系统全链路落地**。真机闪退拿不到日志的问题根治：崩溃现场自动捕获 + 实时双写外部存储，并修复审计发现的三个 P0 安全问题。

### Fixed

- **P0：MCP Server URL 无协议校验**（[McpClient.kt](core/mcp/src/main/kotlin/com/deepcode/core/mcp/McpClient.kt) / SettingsViewModel）：
  只放行 `http`/`https`，拒绝 `file://`、`content://`、`javascript://` 等会被 OkHttp 接受的异常协议，防止 Agent 被诱导向本地文件或任意目标发请求。
- **P0：`LocalDirWorkspace.resolve()` 路径越界**：规范化路径 + 前缀边界判断（root 本身或其子路径，杜绝 `/foo/default-evil` 误匹配），符号链接在检查后二次解析到工作区外的漏洞一并封堵，越界直接抛 `SecurityException`。
- **P0：SSE 解析只取最后一个 data 块**：`HttpJsonRpcMcpClient` 兼容多 data 行，逐块解析最后一个有效 JSON 响应。

### Added

- **core:logging 纯 Kotlin 日志模块**（决策文档 `docs/LOGGING_SYSTEM_DESIGN.md`，实施规划 `docs/LOGGING_PLAN.md`）：
  - 统一门面 `Log` + 全局脱敏（凭据/绝对路径/用户输入/设备标识/URL 凭据）+ 环形缓冲；
  - 分类模型：`LogGroup × LogCategory` 正交（SECURITY/OPERATION/STATE/ERROR/SYSTEM + 子类），日志按危险/操作类型归档；
  - 可插拔 Sink：`LogcatSink`（debug）+ `RollingFileSink`（私有目录与 `/sdcard/deepcodefile/logs` 实时双写，1MB × 5 滚动，SECURITY 镜像 `danger.log`）。
- **崩溃捕获与导出**（`CrashVault` + `LogExporter`）：
  - Application.onCreate 首行安装崩溃/ANR 捕获，记录崩溃栈、上下文、环境信息与最近事件流；
  - 设置页新增"日志"区块（导出 ZIP 分享 / 手动同步根目录 / 根目录授权引导）；下次启动若上次崩溃自动弹窗引导导出；
  - 四层导出包（崩溃栈 / 上下文 / 环境 / 事件流），ZIP 打包经 FileProvider 分享。
- **六类埋点接入**（Agent 循环 / MCP 连接 / 权限沙箱 / 设置操作 / 数据层 + 生命周期启动日志），统一走 `Log` 门面，日志文件出现完整分类；`SignatureGuard` 等零散 `android.util.Log` 已全部替换。
- **R8 策略**：`proguard-rules.pro` 保留 `core:logging` 与 `app.logging`（Release 包日志可读、崩溃栈带行号、异常 message 不被优化成 null）。

---

## [v0.2.1.3] — 2026-09-01 · tag [v0.2.1.3](https://github.com/Lisir2002/Deepcore-Code/releases/tag/v0.2.1.3) · versionCode 20103

> **正式可分发发版**。前身 v0.2.0.2 因 release.yml 默认 `draft: true` 产出未发布 Release，
> 修复 workflow + 清理沙箱代理后重打 tag 首次产出可安装 APK。

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

- **UI 令牌体系 v4.2 — 消息链路 UI 专项**（文档 `docs/DESIGN_TOKENS.md` 6.8 章，实施随 T8.5）：
  - 新增 **6.8 消息链路 UI**：基于全网调研（ChatGPT/Claude/Cursor/Anthropic Console 流式与
    工具卡模式 + Claude Code/Cursor/Cline/Aider 进度面板趋同）与用户四项拍板定稿。
  - **八类消息块**（6.8.1）：用户右对齐气泡 / AI 全宽文档流 / 思考块（AI 紫、默认折叠一行）/
    工具卡 / 审批卡 / 阶段状态行 / 错误块（可恢复 vs 中断两型）/ 空状态（竖排建议 chips）。
  - **工具卡注册表模式**（6.8.2 `AppToolCard`）：工具名→图标+人话标题+参数摘要的单一映射
    （与 `core:mcp` 工具注册同构，MCP 外来工具灰色兜底）；运行中展开、完成即折叠、原始 JSON 仅入折叠区。
  - **执行组聚组**（6.8.3 `AppBlockGroup`）：连续 thinking/tool_use 聚组，左缘紫条 + 步数徽标，防 10+ 卡撑爆会话流。
  - **审批卡内联 PermissionGate**（6.8.4）：命令/diff 预览 + 三选择竖排（风险操作主钮转 danger）。
  - **流式渲染契约**（6.8.5）：活光标、零 layout shift、Stop 同槽、停止保留、首响占位、断流两型。
  - **进度移动化变体**（6.8.6，D20）：置顶摘要条 + 时间线抽屉，不做独立第二面板。
  - 决策新增 **D19/D20**；lint 新增 `ForbiddenRawToolCard` / `ForbiddenRawJsonRender`；T8.5 并入消息链路组件。

- **工具与技能层设计定稿**（文档 `docs/TOOLS_SKILLS.md`，随 M1 实施）：
  - 双标准铁律：工具互操作对齐 **MCP**（规范基线 2025-11-25，官方 Kotlin SDK），
    技能包对齐 **Agent Skills 开放标准**（agentskills.io，`SKILL.md` + 渐进披露三层加载），
    禁止自造私有协议，保证外部生态插件可直接适配。
  - 模块规划：`core:mcp` 新模块（MCP Client，Streamable HTTP 先行）；
    `core:agent` 新增 `skill/` 子包（保持纯 Kotlin 零第三方依赖）。

- **工具与技能层 T1–T4 实施完成**：
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

- **T5 `:app` 装配与设置页**：
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

- **T6 架构文档同步**：
  - `ARCHITECTURE.md` 模块图补 `:feature:settings`，`:core:mcp` 由「官方SDK」改为「自实现」
    并加注弃用官方 SDK 的原因（SDK 0.6+ 要求 Kotlin 2.2+，与本项目 2.0.21 元数据不兼容）；
  - 「能力即接口」补 `SkillLoader`/`SkillInjector`/`McpClient`/`McpServerConfigStore`，
    并写明「接口在 `:core:mcp`、Android 实现在 `:app`」——避免 `feature:settings` 反向依赖 `:app`；
  - 「扩展点」表新增「接一个 MCP 服务器」「加一种 MCP 传输（stdio/WebSocket）」两行；
  - 「组件库是唯一出口」表补 `AppText`/`AppTextField`/`AppSwitch`；
  - 「当前状态」表补 `:core:mcp`（15 例）、`:feature:settings`；
  - 技术栈补 OkHttp / Koin 4.0.0 / SQLDelight 2.x 与两项开放标准。

- **沙箱本地 Android 编译环境**：Android SDK（platform-35 / build-tools 34.0.0 + 35.0.1 /
  platform-tools）+ Gradle 8.9（镜像下载）+ JDK 17（与 CI 对齐），依赖经阿里云 google 镜像解析。
  `PLAN.md` 里「沙箱无 Android SDK，本地无法自验」的限制就此解除。

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
- `release.yml` `softprops/action-gh-release@v2` 显式 `draft: false`，避免默认留草稿。

### Fixed

- **修复 T1–T6 遗留的 CI 红**（M1 工具/技能层代码此前从未在 CI 上编译通过）：
  - `app/build.gradle.kts` 补 `implementation(project(":core:mcp"))`；
  - `AppModule.kt` 补 `named`、`DeepCoreCodeApp.kt` 补 `get`/`inject` 导入；
  - `designsystem` 的 `AppTextStyle.toTextStyle()` / `AppTextTone.toColor()` 补 `@Composable`；
  - `RenderBlockView.ToolOutputView` 补 `Image` / `ResourceLink` / `Structured` 三个产物分支。
- **补齐 CI 覆盖缺口**：
  - `ci.yml` 的 core-test 纳入 `:core:mcp:test`；
  - `ci.yml` 的 design-guard 纳入 `:feature:settings:lintDebug`；
  - `release.yml` 的发布前测试同步纳入 `:core:mcp:test`。
- **修复 CI 第二层编译错误**：
  - `McpServerConfigStore` 接口补声明 `fun current()`；
  - `SettingsScreen.kt` 删除无效 `import weight`。
- **修复 `release_helper.py` 无法解析自身写出的 rc 版本名**：`parse_name` 先剥离 `-rcN` 后缀。
- **修复 `:app` 第三层编译错误**：plugins 补 `kotlin.serialization`、dependencies 补 `okhttp` / `kotlinx.serialization.json`。
- **修复 `gradle.properties` 混入沙箱代理**：`127.0.0.1:18080` 是沙箱环境特有代理，CI 环境无此代理导致所有 Maven 依赖拉取失败。已删除。

## [v0.2.0.2] — 2026-09-01 · tag [v0.2.0.2](https://github.com/Lisir2002/Deepcore-Code/releases/tag/v0.2.0.2) · versionCode 20002

> ⚠️ **被 v0.2.1.3 取代**：此 tag 触发 release.yml 时 `softprops/action-gh-release@v2`
> 未显式设 `draft: false`，CI 产出了 draft Release（APK 已构建但未发布），不可分发。
> 完整变更见 **[v0.2.1.3](#v0213--2026-09-01--tag-v0213--versioncode-20103)**。

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

[Unreleased]: https://github.com/Lisir2002/Deepcore-Code/compare/v0.2.1.3...HEAD
[v0.2.1.3]: https://github.com/Lisir2002/Deepcore-Code/compare/v0.2.0.2...v0.2.1.3
[v0.2.0.2]: https://github.com/Lisir2002/Deepcore-Code/compare/0.1.4...v0.2.0.2
[0.1.4]: https://github.com/Lisir2002/Deepcore-Code/compare/0.1.3...0.1.4
[0.1.3]: https://github.com/Lisir2002/Deepcore-Code/compare/0.1.2...0.1.3
[0.1.2]: https://github.com/Lisir2002/Deepcore-Code/compare/0.1.1...0.1.2
[0.1.1]: https://github.com/Lisir2002/Deepcore-Code/compare/0.1.0...0.1.1
[0.1.0]: https://github.com/Lisir2002/Deepcore-Code/releases/tag/v0.1.0
