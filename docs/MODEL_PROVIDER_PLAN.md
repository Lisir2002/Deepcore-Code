# MODEL_PROVIDER_PLAN.md — 模型接入统一规划与设置页落地

> 状态：**设计定稿**（关键决策已与用户逐项确认，见 §1.3）。
> 实施进度记录在 `PLAN.md`。本文是"模型接入层 + 模型设置页"落地的唯一权威口径；
> 底层 `ModelProvider` SPI（`core/agent/spi/ModelProvider.kt`）已是既有权威，本文只在上层做**接入编排**，不动该 SPI 稳定面。

---

## 一、定位与铁律

### 1.1 要解决什么问题

现状（[AppModule.kt] 的 `AgentRuntimeFactory`）：Provider 选择是**硬编码二选一**——
`if (modelConfig != null) OkHttpProvider else DemoProvider`。加新厂商就要改工厂，工厂线性膨胀。

### 1.2 铁律

1. **换 Provider 不动 Runtime**：`ModelProvider` SPI 保持零改动，Agent 主循环永远只认统一的 `CompletionChunk` 流。
2. **新增厂商 = 注册一条**：通过注册表登记，改 AgentRuntimeFactory、改设置页、改 Demo 回退均不必要。
3. **配置类型化**：每 Provider 持有自己的配置类，字段类型安全、可校验，设置页按 Provider 动态渲染表单。
4. **协议差异烂在 Provider Impl**：各家 SSE/loading/字段差异只出现在实现类里。
5. **改设计先改本文档，再改代码**（沿用 DESIGN_TOKENS 铁律）。

### 1.3 已确认决策（2026-09-02）

| # | 决策点 | 结论 |
| --- | --- | --- |
| D1 | 接入统一粒度 | **注册表统一**：`ProviderRegistry` + 每表项 `ProviderDescriptor`；按 `modelConfig.providerId` 查表 |
| D2 | 配置形态 | **类型化配置**：`ModelConfig` 接口，每 Provider 一个实现（`OpenAIConfig`/`DemoConfig`） |
| D3 | Provider↔模型关系 | **provider 多模型**：`ModelRef(providerId, modelId)`，Provider 可暴露多个模型 |
| D4 | 配置基础形态 | **单配置**：当前状态 + 可编辑，切换 Provider 即覆盖 |
| D5 | 编辑载体 | **两步流程**：模型页压栈进 `ProviderEditScreen`(Step1 端点/密钥/MaxTokens) → `ModelPickScreen`(Step2 选模型) |
| D6 | 模型选择 | **一键拉取 + 手输兜底**：`GET /v1/models` 拉官方目录（失败提示），可手输 modelId |
| D7 | 字段范围 | **全做现有字段**：端点 / API Key / 模型 / maxTokens（OpenAIConfig 现有四字段）；流式开关暂缓 |

> D1–D3 决定"接入编排"；D4–D7 决定"设置页交互"。两者叠加为本次完整落地。

---

## 二、整体结构

### 2.1 模块边界

```
:core:agent/spi          ModelProvider / ModelInfo / CompletionRequest / CompletionChunk  ← 稳定面，零改动
:core:agent              (可选) ModelProviderRegistry 接口层        ← 纯 Kotlin，可 JVM 单测
:app:agent/model         ModelConfig 接口 + OpenAIConfig/DemoConfig + ProviderDescriptor + DefaultProviderRegistry
:app:agent/model         ModelEndpointConfigStore 持久化         ← 单配置，增/改字段
:feature:settings        SettingsModelScreen(入口/状态) + ProviderEditScreen(二级编辑)
:app:di/AppModule        AgentRuntimeFactory 按 providerId 查注册表
```

### 2.2 数据流

```
保存配置 ─► ModelEndpointConfigStore（单配置）
              │
              ▼
AgentRuntimeFactory 启动 ─► DefaultProviderRegistry.resolve(providerId)
                              │            (不存在/未配置 → DemoConfig 兜底)
                              ▼
                         Provider 实例 (OkHttpProvider / DemoProvider)
                              │
                              ▼
                     ModelRef(providerId, modelId)  → stream(request)
```

### 2.3 类型化配置模型

```kotlin
/** 统一配置契约：各 Provider 持自己的实现。 */
interface ModelProviderConfig {
    val providerId: String
    val displayName: String
    fun isComplete(): Boolean
}

open class BaseModelConfig(...) : ModelProviderConfig

class OpenAIConfig(
    val baseUrl: String, val apiKey: String,
    val model: String, val maxTokens: Int = 8192,
) : ModelProviderConfig {
    override val providerId = "openai"
    fun completionsUrl(): String = baseUrl.trimEnd('/') + "/chat/completions"
}

class DemoConfig : ModelProviderConfig {
    override val providerId = "demo"; override val displayName = "演示模型"
    override fun isComplete() = true
}
```

### 2.4 注册表

```kotlin
interface ProviderRegistry {
    val descriptors: List<ProviderDescriptor>       // 设置页遍历渲染
    fun resolve(providerId: String): ProviderDescriptor?
}

class ProviderDescriptor(
    val id: String,
    val displayName: String,
    val requiresConfig: Boolean,                    // false ⇒ 无表单（Demo）
    val factory: (ModelProviderConfig) -> ModelProvider,
)
```

`DefaultProviderRegistry` 内建 `DemoProvider` 兜底项与 `OkHttpProvider`（OpenAI 兼容）项；
未来 DeepSeek/Anthropic 各自新增一个 `ProviderDescriptor` 登记即可。

---

## 三、设置页交互

### 3.1 模型页 `SettingsModelScreen`（入口 / 状态）

- 状态卡：当前生效 Provider + 模型 + `AppStatusChip`（已配置/未接入）。
- 一行入口「配置模型」→ 压栈 `ProviderEditScreen`。
- 未配置时提示"未接入真实模型（当前用演示模型）"。

### 3.2 二级编辑页 `ProviderEditScreen`

- Provider 单选（注册表项，`requiresConfig` 才可选/可编辑）。
- 按所选 Provider **动态渲染**表单：
  - OpenAI 兼容 → 端点 / API Key / 模型（下拉 + 手输兜底）/ maxTokens。
  - Demo → 只读说明。
- 「保存」：类型化配置写入 `ModelEndpointConfigStore`，回栈。

### 3.3 生效时机

配置保存后**对新会话生效**（`AgentRuntimeFactory` 每次建会话时读 store → resolve 注册表），
已开会话维持原 Provider（DI 装配时即快照）。不引入运行时热切换（复杂度收益比低）。

---

## 四、改造面清单

| 文件 | 改动 |
| --- | --- |
| `app/.../agent/model/`（新）`ModelProviderConfig.kt` | 配置接口 + 类型化配置类 |
| `app/.../agent/model/`（新）`ProviderRegistry.kt` | `ProviderRegistry` + `ProviderDescriptor` + `DefaultProviderRegistry` |
| `app/.../agent/model/ModelEndpointConfigStore.kt` | 增 `maxTokens`、`activeProviderId` 字段；`config()` 返回类型化 config |
| `app/.../agent/model/OkHttpProvider.kt` | 改用 `OpenAIConfig`（行为不变，仅源类型替换） |
| `app/.../di/AppModule.kt` | 注册 `ProviderRegistry`；`AgentRuntimeFactory` 改按 `providerId` resolve + Demo 兜底 |
| `feature/settings/.../SettingsModelScreen.kt` | 状态卡 + 入口行，接 `onOpenEdit` |
| `feature/settings/.../ProviderEditScreen.kt`（新） | 二级编辑页：Provider 单选 + 动态表单 + 保存 |
| `app/.../nav/AppNavRoot.kt` | 加 `settings/model/edit` 子路由 |
| `docs/MODEL_PROVIDER_PLAN.md` | 本文档 |
| `PLAN.md` / `CHANGELOG.md` | 登记进展与版本条目 |

> **API Key 加密存储**（EncryptedSharedPreferences）仍是 M2 独立诉求，不随本次落（避免加密依赖挡主线），维持明文标注（已有 Note 在该 Store）。

---

## 五、验收门禁

- `:app:assembleDebug` 编译绿。
- 核心相关单测绿（注册表 resolve / 类型化配置往返）。
- CI `design-guard` 四 job 绿，`:feature:settings` lint 无脏点。
- 设置页可配可用：选 Provider → 填字段 → 保存 → 新会话走真实 Provider。