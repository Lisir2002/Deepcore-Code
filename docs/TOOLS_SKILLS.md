# TOOLS_SKILLS.md — 工具与技能层设计（MCP × Agent Skills）

> 状态：**设计定稿**（四项关键决策已与用户确认，见 §1.3）。
> 实施进度记录在 `PLAN.md`。本文档是 Tool / Skill / Agent 三者调度的唯一权威口径。

---

## 一、定位：对齐业界统一标准

### 1.1 铁律

**工具与技能不自造私有协议。** 所有跨程序边界的接口一律对齐业界开放标准，
让外部生态的工具/技能插件可以低成本接入，我们产出的插件也能在别的平台通用：

| 层 | 对齐标准 | 版本基线 |
| --- | --- | --- |
| **Tool（工具互操作）** | [MCP](https://modelcontextprotocol.io)（Model Context Protocol，全行业事实标准） | 规范 `2025-11-25`（后续 SDK 升级自动跟进） |
| **Skill（技能包格式）** | [Agent Skills](https://agentskills.io)（Anthropic 2025-12-18 发布的开放标准） | SKILL.md 规范现行版 |

两者互补：**MCP 定义 agent 如何连接工具与数据；Skills 定义 agent 拿工具去做什么事。**

### 1.2 适配原则

1. **外部标准字段为准**：映射时外部字段名、语义、校验规则原样保留，不做私有改写。
2. **自有扩展只走附加属性**：内部调度需要的额外信息（如风险等级、来源标记）
   放在内部类型的自有字段或 JSON 的附加属性上，**绝不污染标准字段**。
3. **不可信边界照抄规范**：MCP 规定客户端必须把 server 的工具 annotations
   当作不可信；Agent Skills 规定 skill 是不可信输入（需安装确认 + 脚本审计）。
   我们的权限模型正好按此设计（§4.3、§5.4）。

### 1.3 已确认的四项决策（2026-08-31）

| # | 决策点 | 结论 |
| --- | --- | --- |
| D1 | Tool 对齐路线 | **MCP Client 先行**：消费外部 MCP Server 的工具；MCP Server 侧（暴露本机工具给他人）仅预留类型映射，暂不实现 |
| D2 | Skill 格式 | **Agent Skills 开放标准全兼容**：SKILL.md + frontmatter 校验 + 渐进披露三层加载 |
| D3 | 模块布局 | `core:agent` 加 `skill/` 子包（纯 Kotlin 零新依赖）；**新建 `core:mcp`** 承载 MCP 集成（引入官方 Kotlin SDK） |
| D4 | 传输层顺序 | **Streamable HTTP 先行**（Android 直接可用）；stdio 等 M2 ProotSandbox 就绪后由 SDK 的 `StdioClientTransport` 自动补上 |

---

## 二、现状盘点

### 2.1 已就位（不用改）

- `spi/Tool.kt`：`Tool`（`spec + execute`）、`ToolRegistry`（动态注册制）、`ToolContext`。
- `core/model/Tool.kt`：`ToolSpec`（`parameters` 已是 JSON Schema —— 与 MCP `inputSchema` 同构）、
  `ToolCall`、`ToolOutput` 多态产物、`ToolResult`。
- `spi/Permission.kt`：集中式 `PermissionGate`，`RiskLevel` 五档，`signature()` 策略持久化。
- `DefaultAgentRuntime` 主循环：组上下文 → 调模型 → 收调用 → 过权限门 → 执行 → 回灌。
  **该循环对工具来源零感知——接入 MCP 后一行不改。**

### 2.2 缺口（本设计补齐）

| 缺口 | 补法 |
| --- | --- |
| 无法接入外部 MCP Server 工具 | 新建 `core:mcp`：官方 Kotlin SDK Client + 桥接层 |
| 无 Skill 加载/注入 | `core:agent` 新增 `skill/` 子包：解析 + 注入 |
| `ToolSpec` 缺少标准对齐字段 | 扩展 `title` / `origin` / `sourceId` / `annotations` |
| 外部工具无命名空间，易冲突 | MCP 工具统一 `server__tool` 命名（§4.6） |

---

## 三、模块布局与依赖图

```
                    ┌─────────────────────────────┐
                    │ :app（DI 装配：MCP 配置、skill 目录）│
                    └──────┬──────────────┬───────┘
                           │              │
              ┌────────────▼───┐   ┌──────▼─────────────────┐
              │ core:mcp（新）  │   │ core:agent              │
              │ McpClientManager│   │  ├─ Runtime（主循环）    │
              │ McpToolBridge  ├──▶│  ├─ spi/Tool …（SPI）    │
              │（kotlin-sdk-client│  │  └─ skill/（新子包）     │
              │  + Ktor）      │   │     SkillParser/Loader/ │
              └────────┬───────┘   │     Injector（纯 Kotlin）│
                       │           └──────┬─────────────────┘
        外部 MCP Server │                  │
        （Streamable HTTP）│           ┌──────▼─────────┐
                       └──────────▶ │ core:model      │
                                    └────────────────┘
```

- **依赖方向**：`core:mcp → core:agent(SPI) → core:model`；`core:agent` 保持纯 Kotlin
  零第三方依赖（skill 解析只依赖文件读取抽象，不引 YAML 库——frontmatter 用
  逐行解析实现，字段集合小且封闭）。
- **传输依赖隔离**：OkHttp + kotlinx-serialization 只出现在 `core:mcp`，不向上渗透；
  `core:agent` 仍保持纯 Kotlin 零第三方依赖。
- **可测性**：`McpClient` 接口把传输与桥接/管理逻辑解耦——单测用 `FakeMcpClient`
  （零网络）覆盖桥接/管理器，另用 okhttp `MockWebServer` 真·起本地端点覆盖
  `HttpJsonRpcMcpClient` 握手与 JSON/SSE 解析，`core:mcp` 全量单测可在 JVM 跑。
- **实施偏差（重要）**：设计稿原定引入官方 `io.modelcontextprotocol:kotlin-sdk`，
  但该 SDK 自 0.6+ 起需 **Kotlin 2.2+**，传递依赖 Ktor 3.5.1 / kotlin-stdlib 2.4.0 /
  coroutines 1.11.0（Kotlin 2.3/2.4 元数据），与项目锁定的 **Kotlin 2.0.21** 元数据
  **硬冲突**（编译 Internal compiler error）。因此 `core:mcp` 改为用项目已有的
  OkHttp + kotlinx-serialization **自实现协议级兼容的最小 MCP 客户端**
  （`McpClient` 接口 + `HttpJsonRpcMcpClient`，JSON-RPC 2.0 握手 / SSE 抽取 /
  `Mcp-Session-Id` 回写）。`McpClient` 接口把传输隔离在单一实现类，**未来升级 Kotlin
  后换官方 SDK 只需新增一个实现本接口的类，桥接/管理器逻辑不动**。测试也从
  `kotlin-sdk-testing` in-memory 改为 `FakeMcpClient` + `MockWebServer`。

---

## 四、Tool 层设计（MCP Client）

### 4.1 规范要点（基线 2025-11-25）

| MCP 概念 | 说明 | 我们的动作 |
| --- | --- | --- |
| `tools/list`（分页） | 发现工具 | 拉取后经桥接层转为内部 `ToolSpec` |
| `tools/call` | 调用工具 | `Tool.execute` 内转发，`content[]` 转内部 `ToolOutput` |
| `inputSchema` | JSON Schema 2020-12 | 原样透传进 `ToolSpec.parameters`（模型层本就吃 JSON Schema） |
| `annotations` | readOnlyHint / destructiveHint / idempotentHint / openWorldHint / title | **不可信**，仅作风险映射输入（§4.3），原样保存在 `ToolSpec.annotations` 供 UI 展示 |
| `notifications/tools/list_changed` | 工具清单变更通知 | 触发 Registry 增量刷新（§4.4） |
| `isError: true` | 工具执行错误（非协议错误） | 转 `ToolError(recoverable=true)` 回灌模型 |

### 4.2 核心类型映射

```
MCP Tool                         内部
─────────────────────────────   ─────────────────────────────────────
name                    ──▶     ToolSpec.name（带命名空间，§4.6）
title / description     ──▶     ToolSpec.title / description
inputSchema             ──▶     ToolSpec.parameters（透传）
annotations             ──▶     ToolSpec.annotations（保存）＋ 风险映射（§4.3）

CallToolResult.content[]:
  { type: "text", text }          ──▶  ToolOutput.Text
  { type: "image", data, mimeType } ──▶ ToolOutput.Image（新增）
  { type: "resource_link", uri }  ──▶  ToolOutput.ResourceLink（新增）
  structuredContent               ──▶  ToolOutput.Structured(JsonObject)（新增）
isError = true                   ──▶  ToolError("tool_execution_error", 拼接 text)
```

### 4.3 风险映射（annotations 不可信原则）

MCP 规范原文："clients MUST consider tool annotations to be untrusted unless
they come from trusted servers"。因此**风险等级的最终裁决永远在本地 PermissionGate，
annotations 只是输入**：

| 条件（server 侧声明） | server 信任级别 | 实际 `RiskLevel` |
| --- | --- | --- |
| 任意 | 未信任（默认） | `NETWORK`（外部调用=数据出域，基线） |
| `readOnlyHint = true` | **已信任**（用户显式标记） | `READ_ONLY` |
| `destructiveHint = true` | 任意 | `DESTRUCTIVE`（取最高档） |
| 其余 | 任意 | `NETWORK` |

- **信任级别是用户的本地设置**（server 配置里的 `trusted: Boolean`），
  不是 server 自己能声明的——杜绝"自封只读"。
- `idempotentHint` / `openWorldHint` 仅存档展示，不参与裁决。

### 4.4 生命周期与调度动态性

```
App 启动 → 读取已配置 server 列表 → 逐个 connect（初始化握手 + capability 协商）
        → tools/list → 桥接 → 注册进统一 ToolRegistry
运行中   → 收到 list_changed → 增量重新 tools/list → Registry 差量更新
断线     → 指数退避重连；重连成功后全量刷新；turn 进行中不刷新（见下）
```

**prompt cache 友好规则**（调度最优的关键细节）：
- 工具清单按 `name` 稳定排序后再给 Provider，避免每次请求顺序抖动击穿缓存。
- **turn 进行中不换工具清单**：list_changed 只更新 Registry，`specs()` 快照在
  turn 开始时取定；下一 turn 自动生效。避免同一 turn 内 tool 列表变化导致
  供应商侧 prompt cache 全部失效。
- server 上下线不影响本地工具的稳定性（本地工具恒在列表头部）。

### 4.5 传输层落地顺序（D4）

| 传输 | 状态 | 说明 |
| --- | --- | --- |
| Streamable HTTP | **M1 实现** | 绝大多数云端 MCP Server 的形态；Android 直接可用（Ktor CIO 引擎） |
| SSE（旧式 HTTP） | M1 顺带支持 | SDK `SseClientTransport`，兼容存量 server |
| stdio | M2（等 ProotSandbox） | 内嵌 Linux userland 后跑本地 MCP Server 进程，SDK `StdioClientTransport` 直接连上 |
| WebSocket | 视需求 | SDK 现成，成本低，按需开 |

### 4.6 命名空间与冲突

- 外部工具统一命名 `server__tool`（双下划线分隔；server 名与 tool 名均需满足
  MCP 工具名字符集 `[A-Za-z0-9._-]`）。
- 冲突规则：本地工具 > 先连接的 server > 后连接的 server（后者被静默跳过并告警）。
- 模型看到的名字即带命名空间；`serverInfo` 的 serverInstructions 注入 system prompt
  的"工具来源"段。

---

## 五、Skill 层设计（Agent Skills 开放标准）

### 5.1 SKILL.md 规范要点

```
skill 目录/
├── SKILL.md        # 必需：YAML frontmatter + Markdown 指令正文
├── references/     # 可选：按需加载的参考文档
├── scripts/        # 可选：确定性脚本（经 Sandbox 执行）
└── assets/         # 可选：模板、数据文件
```

frontmatter 字段与校验（照抄规范）：

| 字段 | 必填 | 规则 |
| --- | --- | --- |
| `name` | ✅ | ≤64 字符；仅小写字母/数字/连字符；须与目录名一致 |
| `description` | ✅ | ≤1024 字符；必须同时说明"做什么"+"何时用"（触发依据） |
| `license` | — | SPDX 表达式 |
| `compatibility` | — | 环境要求（如 "requires git and python3"） |
| `metadata` | — | 任意键值对（author、version…） |
| `allowed-tools` | — | 预批工具清单（实验性字段；我们解析并用于 §5.4） |

### 5.2 渐进披露三层 → 现有能力映射

标准的三层加载模型**不需要任何新工具**即可落地：

| 层 | 加载时机 | 我们的实现 |
| --- | --- | --- |
| **L1 元数据**（~100 token/个） | 会话开始，常驻 | `SkillLoader` 扫描目录 → `SkillInjector` 把全部 `name + description` 注入 system prompt 的"可用技能"段 |
| **L2 指令**（<5k token） | 模型判断任务匹配时 | 模型用**现有 `read` 工具**读 `SKILL.md` 正文（与 Claude Code 用 bash cat 读同构） |
| **L3 资源/脚本** | 按需 | `references/` 用 `read` 读；`scripts/` 经 Sandbox 执行（走 PermissionGate 风险裁决） |

**上下文成本模型**：装 30 个 skill，常驻成本 ≈ 30×100 token；只有被触发的
skill 才产生 L2/L3 开销。这是标准设计的精髓，直接继承。

### 5.3 目录约定与安装

| 来源 | 路径 | 说明 |
| --- | --- | --- |
| 项目级 | `<workspace>/.deepcode/skills/` | 随工作区走（等价 `.claude/skills/` 的项目位） |
| 用户级 | app 私有目录 `skills/` | 跨会话全局 |
| 安装 | M1：本地目录；M2：zip 导入 / URL 拉取 | 安装动作本身需要用户确认（不可信输入） |

`compatibility` 声明的环境缺失（如需要 git 但 Sandbox 无 shell）时，该 skill
标记为"受限可用"，L1 注入时附带说明，避免模型空转。

### 5.4 allowed-tools 与权限关系

- M1：`allowed-tools` **解析 + 展示**，不自动放权。skill 引用的工具照常过
  PermissionGate。
- M2：用户对某 skill 显式点"信任"后，其 `allowed-tools` 进入
  `ApprovalPolicyStore` 预批集合（复用现有 signature 持久化机制，零新表）。
- 脚本执行永远单独裁决（`RiskLevel` 按实际命令定，不受 skill 声明影响）。

### 5.5 安全校验（安装时）

1. frontmatter 语法与字段规则校验（含 name↔目录名一致性）。
2. SKILL.md ≤500 行软校验（超出仅告警，不拒绝——标准是建议非硬约束）。
3. 扫描 `scripts/` 是否存在并提示用户（"该 skill 含可执行脚本"）。
4. 拒绝 name 含保留词（`anthropic`/`claude` 等，规范要求）。

---

## 六、三者调度定稿（Tool × Skill × Agent）

### 6.1 职责分离（避免调度歧义的根本）

| | Tool（含 MCP 工具） | Skill |
| --- | --- | --- |
| 本质 | **能力**：函数调用，改变或读取外部状态 | **知识**：流程指令与资源包 |
| 进入模型的方式 | `CompletionRequest.tools`（function calling） | system prompt 注入 + 文件读取 |
| 触发方 | 模型直接发起 `ToolCall` | 模型判断任务匹配 description 后自读 SKILL.md |
| 结果形态 | `ToolResult`（结构化产物 + 事件流） | 成为上下文的一部分（后续轮次的知识） |
| 风险裁决 | PermissionGate 按 `RiskLevel` | 安装确认 + 脚本执行单独裁决 |

**Skill 永远不伪装成 Tool**（不做 `load_skill` 之类的伪工具）——用文件读取
触发是标准原生的做法，且省一个工具位、不增加 function calling 歧义。

### 6.2 主循环不变式

`DefaultAgentRuntime` 只有两处感知变化：

1. `toolRegistry.specs()` 现在包含 MCP 桥接工具（快照规则见 §4.4）。
2. `buildSystemPrompt()` 末尾追加 `SkillInjector` 产出的"可用技能"段
   （该段内容会话内稳定，不破坏 prompt cache 前缀）。

其余（事件流、权限门、上下文压缩、迭代上限）**一行不改**。

### 6.3 上下文预算策略

| 项 | 策略 |
| --- | --- |
| 工具数量 | server 级启停开关（UI 管理），不做自动裁剪（保确定性） |
| 技能数量 | L1 元数据轻，暂不设上限；>50 个时 UI 提示分组 |
| 压缩时机 | 技能清单在 system prompt，ContextPolicy 压缩只动对话历史，永不压缩技能段与工具清单 |

---

## 七、接口草案（签名级，实施时可微调）

```kotlin
// ── core:model 扩展 ─────────────────────────────────────
data class ToolSpec(
    … // 现有字段不动
    val title: String? = null,
    val origin: ToolOrigin = ToolOrigin.BUILTIN,   // BUILTIN | MCP
    val sourceId: String? = null,                  // MCP: server 名
    val annotations: JsonObject? = null,           // MCP annotations 原样存档
)
enum class ToolOrigin { BUILTIN, MCP }

// ToolOutput 新增产物形态
@SerialName("image") data class Image(val mimeType: String, val base64: String) : ToolOutput
@SerialName("resource_link") data class ResourceLink(val uri: String, val name: String?) : ToolOutput
@SerialName("structured") data class Structured(val json: JsonObject) : ToolOutput

// ── core:agent/skill ────────────────────────────────────
data class SkillManifest(
    val name: String, val description: String,
    val license: String? = null, val compatibility: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val allowedTools: List<String> = emptyList(),
    val dirPath: String, val hasScripts: Boolean, val bodyPath: String,
)
interface SkillParser { fun parse(skillMdContent: String): SkillManifest /* 含校验 */ }
interface SkillLoader { suspend fun loadAll(): List<SkillManifest> }
interface SkillInjector { suspend fun promptSection(manifests: List<SkillManifest>): String? }

// ── core:mcp ────────────────────────────────────────────
data class McpServerConfig(
    val name: String,            // 命名空间用，[a-z0-9-]
    val url: String,             // Streamable HTTP endpoint
    val headers: Map<String, String> = emptyMap(),
    val enabled: Boolean = true,
    val trusted: Boolean = false, // 用户设置；决定 annotations 可否降档（§4.3）
)
class McpToolBridge(
    server: McpServerConfig,
    client: McpSdkClient,        // 官方 SDK Client 薄封装
) : Tool                        // 直接实现内部 Tool SPI → 注册进统一 Registry
class McpServerManager(
    configs: List<McpServerConfig>, registry: ToolRegistry, scope: CoroutineScope,
) {
    fun start()                  // connect + listTools + 注册 + 订阅 list_changed
    suspend fun stop(name: String)
    val states: StateFlow<Map<String, ServerState>>   // CONNECTING/READY/RETRYING/DISABLED
}
```

---

## 八、测试策略

| 层 | 用例 | 手段 |
| --- | --- | --- |
| skill 解析 | 合法 frontmatter / name 超长 / 目录名不一致 / description 空 / 保留词 | 纯 JVM 单测 |
| skill 注入 | L1 段格式稳定（快照）、空 skill 列表省略段 | 纯 JVM |
| MCP 映射 | tools/list→ToolSpec（含 annotations/命名空间）；callTool content 四类型→ToolOutput；isError→ToolError | `FakeMcpClient` + MockWebServer |
| MCP 生命周期 | list_changed→重新拉取清单；单点失败不传染；turn 内不刷新（快照断言） | `FakeMcpClient` + 协程测试调度器 |
| 调度集成 | MCP 工具走完整主循环（权限门/事件流/回灌） | `DefaultAgentRuntimeTest` 增补 |
| 缓存友好 | specs 稳定排序、turn 内快照不变 | 断言两次 specs() 相等 |

---

## 九、实施清单（进 M1，随 0.2.0.x 发布）

- [x] **T1** `core:model`：`ToolSpec` 扩展 + `ToolOutput` 三种新形态（向后兼容，默认值兜底）
- [x] **T2** `core:agent/skill/`：`SkillParser` / `SkillLoader` / `SkillInjector` + 单测
- [x] **T3** 主循环两处感知点落地（specs 快照规则 + system prompt 技能段）
- [x] **T4** 新建 `core:mcp`：`McpClient` 接口 + `HttpJsonRpcMcpClient`（OkHttp 自实现）+ `McpToolBridge` / `McpServerManager` / 风险映射 + FakeMcpClient/MockWebServer 单测（15 例全绿）
- [ ] **T5** `:app` DI 装配（MCP 配置存储、skill 目录声明）+ settings 界面接 server 管理（feature 层）
- [ ] **T6** 文档同步（本文件勾进度、ARCHITECTURE 扩展点表、CHANGELOG）
- [ ] **T7** 发版走四段式版本门禁（`0.2.0.x`，正式版前 AskUserQuestion 确认）

## 十、开放问题（实施时再定）

1. MCP server 配置的 header 密钥存储：M1 先 `EncryptedSharedPreferences` 明键，
   是否需要独立密钥库待定。
2. Sampling / Roots / Elicitation 等 MCP 客户端能力：暂不声明 capability，
   后续按需加（协议向前兼容）。
3. MCP Server 侧（D1 预留）：类型映射已在 §4.2 定义，反向暴露待生态验证后立项。
4. skill 市场/仓库源（M3+）：依赖安装流程（T5 的 zip/URL）先落地。
