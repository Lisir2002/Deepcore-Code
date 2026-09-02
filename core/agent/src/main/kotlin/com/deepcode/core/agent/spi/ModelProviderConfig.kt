package com.deepcode.core.agent.spi

/**
 * 模型供应商接入编排（决策 D1/D2：注册表统一 + 类型化配置）。
 *
 * 连接面放纯 Kotlin 核心层，让 :feature:settings 与 :app / Provider 实现共享同一套
 * 类型，而无需反向依赖 :app。属性：
 *
 * - 新增厂商 = 登记一个 [ModelProviderDescriptor]，UI 与 Factory 免改。
 * - Provider 多模型：`ModelRef(providerId, modelId)`，Provider 可暴露多个模型。
 * - 三种流行协议（本轮决策 P1）：OpenAI 兼容 / Anthropic / Google Gemini，
 *   各自一个类型化配置实现；协议差异在 app 层各自的 Provider 适配器内隔离。
 */
object ModelProviderIds {
    const val OPENAI_COMPATIBLE = "openai"
    const val ANTHROPIC = "anthropic"
    const val GEMINI = "gemini"
    const val DEMO = "demo"
}

/** 单份类型化配置契约：每 Provider 持自己的实现（决策 D2）。 */
interface ModelProviderConfig {
    val providerId: String
    val displayName: String

    /** 是否已构成"可用"（缺字段则回退 Demo）。 */
    fun isComplete(): Boolean
}

/** OpenAI 兼容端点配置（覆盖 GPT / DeepSeek / 通义 / GLM / xAI 等一大族）。 */
data class OpenAIConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val maxTokens: Int = 8192,
) : ModelProviderConfig {

    override val providerId: String = ModelProviderIds.OPENAI_COMPATIBLE
    override val displayName: String = "OpenAI 兼容"

    override fun isComplete(): Boolean =
        baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

    fun completionsUrl(): String = baseUrl.trimEnd('/') + "/chat/completions"

    fun modelsUrl(): String = baseUrl.trimEnd('/') + "/models"
}

/** Anthropic Messages API 配置（`/v1/messages`，`x-api-key` + `anthropic-version` 头）。 */
data class AnthropicConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val maxTokens: Int = 4096,
    val anthropicVersion: String = "2023-06-01",
) : ModelProviderConfig {

    override val providerId: String = ModelProviderIds.ANTHROPIC
    override val displayName: String = "Anthropic"

    override fun isComplete(): Boolean =
        baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

    fun messagesUrl(): String = baseUrl.trimEnd('/') + "/v1/messages"

    fun modelsUrl(): String = baseUrl.trimEnd('/') + "/v1/models"
}

/** Google Gemini 配置（`/v1beta/models/{model}:streamGenerateContent`，`x-goog-api-key` 头）。 */
data class GeminiConfig(
    val baseUrl: String = "https://generativelanguage.googleapis.com",
    val apiKey: String = "",
    val model: String = "",
    val maxTokens: Int = 8192,
) : ModelProviderConfig {

    override val providerId: String = ModelProviderIds.GEMINI
    override val displayName: String = "Google Gemini"

    override fun isComplete(): Boolean =
        baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

    fun generateUrl(): String = baseUrl.trimEnd('/') + "/v1beta/models/" + model + ":streamGenerateContent"

    fun modelsUrl(): String = baseUrl.trimEnd('/') + "/v1beta/models"
}

/** 演示模型（M0 脚手架）配置：无表单、始终可用。 */
data class DemoConfig(
    override val providerId: String = ModelProviderIds.DEMO,
    override val displayName: String = "演示模型",
) : ModelProviderConfig {
    override fun isComplete(): Boolean = true
}

/** 从类型化配置提取会话使用的 modelId。 */
fun modelOf(config: ModelProviderConfig): String = when (config) {
    is OpenAIConfig -> config.model
    is AnthropicConfig -> config.model
    is GeminiConfig -> config.model
    else -> "demo-1"
}

/** Provider 配置的持久化访问抽象（单配置）。实现方在 :app（Encrypted 计划见 M2）。 */
interface ModelConfigStore {
    fun current(): ModelProviderConfig
    fun saveOpenAi(config: OpenAIConfig)
    fun saveAnthropic(config: AnthropicConfig)
    fun saveGemini(config: GeminiConfig)
    fun resetToDemo()
}