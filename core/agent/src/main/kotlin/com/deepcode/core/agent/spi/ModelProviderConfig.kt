package com.deepcode.core.agent.spi

import kotlinx.serialization.Serializable

/**
 * 模型供应商接入编排（决策 D1/D2：注册表统一 + 类型化配置；借鉴 deepcode-R 供应商粒度）。
 *
 * 连接面放纯 Kotlin 核心层，让 :feature:settings 与 :app / Provider 实现共享同一套
 * 类型，而无需反向依赖 :app。属性：
 *
 * - 新增厂商 = 登记一个 [ModelProviderDescriptor]，UI 与 Factory 免改。
 * - 存储粒度 = **供应商**：一条 [ProviderConfig] 是一个供应商，内嵌一组模型
 *   [ProviderConfig.models]，选中一个 [ProviderConfig.selectedModel]，其中一条标记激活。
 *   这与 deepcode-R 的 AIProviderConfig 粒度一致，比"每条一个模型"更贴合用户心智。
 * - 三种流行协议（决策 P1）：OpenAI 兼容 / Anthropic / Google Gemini，各自一个
 *   类型化配置实现；协议差异在 app 层各自的 Provider 适配器内隔离。
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
 * 一条已保存的**供应商**配置（供应商粒度存储，借鉴 deepcode-R 的 AIProviderConfig）。
 *
 * 一个供应商 = 协议 + 端点 + 密钥 + 一组模型；其中一条供应商标记为激活
 * （[ModelConfigStore.activeProviderId]）。聊天页可选该供应商下的任一模型，设置页
 * 管理全部供应商。
 *
 * @param id 稳定 id（保存时若为空则由存储方生成）。
 * @param name 用户可读名，如 "DeepSeek"。
 * @param providerId 协议类型（见 [ModelProviderIds]）。
 * @param apiKey 由存储实现做加密落盘；内存态为明文。
 * @param models 该供应商已添加的可用模型列表（拉取或手动添加）。
 * @param selectedModel 当前选中的模型；为空时回退到 [models] 首个。
 */
@Serializable
data class ProviderConfig(
    val id: String = "",
    val name: String = "",
    val providerId: String = "",
    val baseUrl: String = "",
    val apiKey: String = "",
    val maxTokens: Int = 8192,
    val models: List<String> = emptyList(),
    val selectedModel: String = "",
    val isActive: Boolean = false,
) {
    /** 当前生效模型：优先 selectedModel，其次列表首个。 */
    fun effectiveModel(): String = selectedModel.ifBlank { models.firstOrNull().orEmpty() }

    /** 预览用显示名。 */
    fun displayName(): String = name.ifBlank { providerId }

    /** 是否已构成"可用"（缺字段则不能作为真实模型激活）。 */
    fun isComplete(): Boolean =
        baseUrl.isNotBlank() && apiKey.isNotBlank() && models.isNotEmpty() && effectiveModel().isNotBlank()

    /** 转回类型化配置（用当前生效模型）；协议未知/不可用时回退 [DemoConfig]。 */
    fun toConfig(): ModelProviderConfig = when (providerId) {
        ModelProviderIds.OPENAI_COMPATIBLE -> OpenAIConfig(baseUrl, apiKey, effectiveModel(), maxTokens)
        ModelProviderIds.ANTHROPIC -> AnthropicConfig(baseUrl, apiKey, effectiveModel(), maxTokens)
        ModelProviderIds.GEMINI -> GeminiConfig(baseUrl, apiKey, effectiveModel(), maxTokens)
        else -> DemoConfig()
    }
}

/**
 * Provider 配置的持久化访问抽象（供应商粒度，借鉴 deepcode-R AIProviderRepository 语义）。
 * 实现方在 :app（明文 JSON + API Key 加密，见 ModelEndpointConfigStore + KeyEncryptor）。
 *
 * 供应商粒度：可保存多条 [ProviderConfig]，[activeProviderId] 决定当前会话生效哪个
 * 供应商；供应商内 [selectedModel] 决定用哪个模型。[current] 读激活供应商+选中模型
 * 转类型化配置，未激活/不可用回退 [DemoConfig]。
 */
interface ModelConfigStore {
    /** 当前生效的类型化配置（激活供应商选中模型，或 Demo 兜底）。 */
    fun current(): ModelProviderConfig

    /** 所有已保存供应商（含激活标记）。 */
    fun listProviders(): List<ProviderConfig>

    /** 所有已保存供应商 id（便捷）。 */
    fun listProviderIds(): List<String> = listProviders().map { it.id }

    /** 当前激活的供应商 id；为空表示未激活（走演示模型）。 */
    fun activeProviderId(): String

    /** 当前激活的 [ProviderConfig]；未激活返回 null。 */
    fun activeProvider(): ProviderConfig?

    /** 保存（按 id 覆盖）或新增一条供应商，返回稳定 id。 */
    fun saveProvider(provider: ProviderConfig): String

    /** 标记某供应商为激活（先取消其它供应商的激活标记）；id 不存在则忽略。 */
    fun activateProvider(id: String)

    /** 删除某供应商；若它为激活则改为未激活（可自动接替首个可用）。 */
    fun deleteProvider(id: String)

    /** 设置某供应商当前选中模型；仅当选中的模型在其 models 列表内才生效。 */
    fun setSelectedModel(providerId: String, model: String)

    /** 整体替换某供应商的模型列表。 */
    fun updateModels(providerId: String, models: List<String>)

    /** 退回首演示模型（不再激活任何已保存供应商，但保留列表）。 */
    fun resetToDemo()
}