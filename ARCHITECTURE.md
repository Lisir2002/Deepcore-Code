# Agent IDE for Android · 架构地基

> M0 目标：把**骨架 + 主循环 + UI 统一机制**立住，跑通一条完整链路。
> 后面的功能都往这套接口上挂，不改地基。

---

## 一、模块划分

```
                    ┌─────────────────────────────────────┐
  Android 层         │  :app          :feature:chat        │
                    └────────────┬────────────────────────┘
                                 │ 只能用 designsystem 的组件
                    ┌────────────▼────────────────────────┐
                    │  :designsystem  （唯一可用 Material3）│
                    └────────────┬────────────────────────┘
  ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┼ ─ ─ ─ ─ ─ ─ ─ 纯 Kotlin 分界线 ─ ─
                    ┌────────────▼────────────────────────┐
                    │  :core:uistate   事件→渲染块 归约     │
                    │  :core:agent     主循环 + 全部 SPI    │
                    │  :core:data      事件日志存储          │
                    │  :core:model     领域模型 + 事件定义   │
                    └────────────┬────────────────────────┘
                    ┌────────────▼────────────────────────┐
                    │  :core:platform  Android 能力实现     │
                    │  :lint           设计系统守卫         │
                    └─────────────────────────────────────┘
```

**分界线以上是 Android/Compose，以下全是纯 Kotlin。**

这条线不是洁癖，是收益：Agent 主循环是这个 App 里最容易出错的部分，把它放在纯 Kotlin 模块，
就能在 JVM 上直接跑测试，**不用开模拟器、不用打包 APK**。M0 阶段已经靠它抓到一个真实 bug
（工具执行过快时流式输出被吞）。

---

## 二、三个地基决策

### 1. 一切都走事件流

`AgentEvent` 是 Agent 与 UI 之间**唯一**的通信契约。UI 不持有"当前在干嘛"这种状态，
它只是事件序列的纯函数映射。

```kotlin
sealed interface AgentEvent { val id; val sessionId; val turnId; val ts }

TurnStarted → ThinkingDelta → MessageDelta → ToolCallProposed
  → ToolCallApproved → ToolCallStarted → ToolOutputDelta → ToolCallSucceeded
  → MessageDelta → TurnCompleted
```

**铁律：事件是"已发生的事实"，不是"UI 指令"。** 事件里不允许出现颜色、文案模板、
排版提示。怎么画是渲染器的事——这条守住了，加功能就不用改 UI。

### 2. 会话存事件序列，不存消息列表

```
存储：append-only 事件日志（EventStore）
恢复：重放事件 → 同一套 reducer → 同一份界面
```

Android 上跑一个 Agent 二三十分钟太正常，进程随时可能被回收。存"UI 状态的快照"，
恢复就是灾难；存事件序列，回放即恢复，而且顺手拿到了时间旅行、分叉、审计。
`ChatViewModel` 里没有任何 `List<Message>`，界面内容完全由 `history()` + 事件流归约而来。

### 3. 能力即接口

`ModelProvider` / `Tool` / `Sandbox` / `Workspace` / `PermissionGate` / `ContextPolicy` /
`ProjectMemory` ——全部是接口。想换实现，只改 `:app` 里的依赖绑定，业务代码一行不动。

---

## 三、UI 统一：三道防线

"UI 越写越乱"的根因从来不是写代码的人不认真，而是**没有强制机制**。靠"大家都用组件库"
这种口头约定，三个页面之后必然崩。所以这里上了三道锁：

### 防线一：唯一映射链路

```
AgentEvent ──(TranscriptReducer)──▶ RenderBlock ──(TranscriptList)──▶ 界面
             纯 Kotlin，可单测        全项目唯一实现
```

归约逻辑**故意放在纯 Kotlin 模块 `:core:uistate`**，因为流式拼接、块复用、状态迁移
正是最容易出 bug 的地方，必须能脱离模拟器直接测（已覆盖 6 个用例）。

关键点：`ToolOutput` 是结构化类型（Text / Diff / FileList / SearchHits / KeyValues），
渲染按**产物类型**分发，不是按工具名 `when (tool.name)`。
否则每加一个工具就要改一遍所有页面——这正是 UI 走样的根源。

### 防线二：组件库是唯一出口

业务页面拿不到"设计"的机会：

| 需求 | 唯一写法 |
|---|---|
| 页面骨架 | `AppScaffold` / `AppScaffoldWithState` |
| 加载/空/错误态 | `UiState<T>` + 自动切换，全项目一份实现 |
| 间距/圆角/字号 | `Dimens` / `TypeScale` 令牌 |
| 工具卡片 | `RenderBlockView`（注册即用，不许自己写） |
| 输入栏 | `AppInputBar` |

看 `ChatScreen.kt` 就明白了——它没有自己的布局、没有自己的卡片、没有自己的状态处理，
只是把 ViewModel 的状态接到组件上。**页面里根本没有发挥"设计才华"的余地。**

### 防线三：Lint 守卫（构建期硬失败）

`:lint` 模块拦截两类行为，命中即 **编译失败**，不是 code review 时才发现：

- `DirectMaterial3Usage` —— feature/app 层 import `androidx.compose.material3.*` 或自建 `Scaffold`/`Button`/`TopAppBar`
- `HardcodedDesignToken` —— 硬编码 `16.dp` / `14.sp`

---

## 四、扩展点：以后加功能往哪插

| 想加的能力 | 在哪加 | 要改的东西 |
|---|---|---|
| 接真实模型（Claude/GPT/DeepSeek） | 实现 `ModelProvider`，换掉 `:app` 里的一行绑定 | **仅此一处** |
| 加一种工具（grep / git / 网页） | 实现 `Tool` + `register()` | Runtime、事件、UI 全不动 |
| 接 MCP 工具 | 实现 `Tool`，内部转发给 MCP server | 同上，Runtime 不认识 MCP |
| 跑 npm / clang（内嵌 Linux） | 新增 `ProotSandbox : Sandbox` | 只换 `:app` 绑定，上层无感 |
| 连远端服务器 | 新增 `SshWorkspace` / `SshSandbox` | 同上 |
| 子 Agent 并行 | 事件模型已有 `SubAgentEvent` | 主循环加分支，UI 递归复用渲染器 |
| Plan Mode（先出计划再执行） | 新增 `AgentRuntime` 策略实现 | 接口不变 |
| 本地小模型（端侧推理） | 新增 `ModelProvider` | 同上 |
| 换存储为 Room | 新增 `EventStore` 实现 | 接口不变 |
| 项目记忆 CLAUDE.md | 实现 `ProjectMemory` | 已预留注入口 `systemPromptProvider` |
| 长任务后台保活 | `:core:platform` 加前台服务 | Runtime 不感知 Android |

**加功能 = 加实现，不是改地基。** 这是整个设计要兑现的承诺。

---

## 五、当前状态

| 层 | 内容 | 验证状态 |
|---|---|---|
| `core:model` | 事件模型、工具规格、风险等级、结构化产物 | ✅ 编译 + 单测 |
| `core:agent` | 主循环、权限门、上下文策略、全部 SPI | ✅ 编译 + **4 个用例通过** |
| `core:data` | 事件日志（append-only） | ✅ 编译 |
| `core:uistate` | 事件→渲染块归约器 | ✅ 编译 + **6 个用例通过** |
| `core:platform` | 本地工作区、命令白名单沙箱、4 个基础工具 | ✅ CI 编译 + 正式包产出 |
| `designsystem` | 主题令牌、组件库、事件渲染器 | ✅ CI 编译 + 正式包产出 |
| `feature:chat` | 会话页 + ViewModel | ✅ CI 编译 + 正式包产出 |
| `app` | DI 装配、演示模型、MainActivity | ✅ CI 编译 + 正式包产出（v0.1.3） |
| `lint` | 设计系统守卫 | ✅ 编译 |

M0 已交付并经 CI 全量编译验证；正式签名 + 加固 + 发版流水线已落地（最新 v0.1.3）。
进度与下一步见 `PLAN.md`，版本历史见 `CHANGELOG.md`。

### M0 测出来的真实 bug

`DefaultAgentRuntime.runToolWithProgress()` 原本在 `finally` 里 `drainJob.cancel()`，
导致执行很快的工具其流式输出会被吞掉。表现是"命令输出偶尔少几行"，极难复现。
改成 `close()` 后 `join()` 排空管道后修复。这个 bug 是被单元测试直接抓到的。

---

## 六、下一步（建议顺序）

- **M1 能真跑**：接一个真实 `ModelProvider`（OkHttp + SSE），补 `OkHttpProvider`，跑通真实对话
- **M2 能用**：工作区文件树、Diff 审阅、git 工具、会话列表页
- **M3 能扛**：Room 持久化存储、前台服务保活、上下文压缩接真实场景
- **M4 扩展**：MCP 客户端、子 Agent、Plan Mode、远端沙箱

**强烈建议 M1 先做**：把演示模型换成真实模型后，prompt 工程、工具描述质量、
上下文策略这些真正决定体验的东西才有办法调优。

---

## 七、本地编译

```bash
# 需要 JDK 17+ 与 Android Studio（或 standalone Android SDK，compileSdk 35）
./gradlew :app:assembleDebug

# 纯 Kotlin 模块可以脱离 Android SDK 单独验证
./gradlew :core:agent:test :core:uistate:test
```

技术栈：Kotlin 2.0.21 · Jetpack Compose（Material3）· Coroutines/Flow ·
kotlinx.serialization · Koin · Gradle 8.9 · AGP 8.7.3 · minSdk 26
