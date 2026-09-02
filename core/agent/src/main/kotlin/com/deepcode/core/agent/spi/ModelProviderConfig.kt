package com.deepcode.core.agent.spi

import kotlinx.serialization.Serializable

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

/**
 * 一条已保存的模型配置（多模型支持）。
 *
 * 用户可保存任意多条「Provider + 端点 + 密钥 + 模型」的组合，其中一条标记为激活
 * （[ModelConfigStore.activeModelId]）。聊天页与设置页均从此列表出可选模型。
 *
 * @param id 稳定 id（保存时若为空则由存储方生成）。
 * @param label 用户可读名，如 "DeepSeek V3"。
 */
@Serializable
data class SavedModel(
    val id: String = "",
    val label: String = "",
    val providerId: String = "",
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val maxTokens: Int = 8192,
) {
    /** 转回类型化配置；协议未知/不可用时回退 [DemoConfig]。 */
    fun toConfig(): ModelProviderConfig = when (providerId) {
        ModelProviderIds.OPENAI_COMPATIBLE -> OpenAIConfig(baseUrl, apiKey, model, maxTokens)
        ModelProviderIds.ANTHROPIC -> AnthropicConfig(baseUrl, apiKey, model, maxTokens)
        ModelProviderIds.GEMINI -> GeminiConfig(baseUrl, apiKey, model, maxTokens)
        else -> DemoConfig()
    }

    /** 是否已构成"可用"（缺字段则不能作为真实模型激活）。 */
    fun isComplete(): Boolean = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
}

/** 从类型化配置构一条 [SavedModel]（多模型落盘转换；id 为空时由存储方生成）。 */
fun savedModelOf(config: ModelProviderConfig): SavedModel = when (config) {
    is OpenAIConfig -> SavedModel(providerId = config.providerId, baseUrl = config.baseUrl, apiKey = config.apiKey, model = config.model, maxTokens = config.maxTokens)
    is AnthropicConfig -> SavedModel(providerId = config.providerId, baseUrl = config.baseUrl, apiKey = config.apiKey, model = config.model, maxTokens = config.maxTokens)
    is GeminiConfig -> SavedModel(providerId = config.providerId, baseUrl = config.baseUrl, apiKey = config.apiKey, model = config.model, maxTokens = config.maxTokens)
    else -> SavedModel(providerId = DemoConfig().providerId, label = DemoConfig().displayName)
}

/**
 * Provider 配置的持久化访问抽象（多模型）。实现方在 :app（Encrypted 计划见 M2）。
 *
 * 多模型：可保存多条 [SavedModel]，[activeModelId] 决定当前会话生效哪条；
 * [current] 读激活模型转类型化配置，未激活/不可用回退 [DemoConfig]。
 */
interface ModelConfigStore {
    /** 当前生效的类型化配置（激活模型或 Demo 兜底）。 */
    fun current(): ModelProviderConfig

    /** 所有已保存模型（不含过期的激活 id）。 */
    fun listModels(): List<SavedModel>

    /** 当前激活的模型 id；为空表示未激活（走演示模型）。 */
    fun activeModelId(): String

    /** 当前激活的 [SavedModel]；未激活返回 null。 */
    fun activeModel(): SavedModel?

    /** 保存（按 id 覆盖）或新增一条模型，返回稳定 id。 */
    fun saveModel(model: SavedModel): String

    /** 标记某条模型为激活；id 不存在则忽略。 */
    fun activateModel(id: String)

    /** 删除某条模型；若它为激活则改为未激活。 */
    fun removeModel(id: String)

    /** 退回首演示模型（不再激活任何已保存模型，但保留列表）。 */
    fun resetToDemo()
}