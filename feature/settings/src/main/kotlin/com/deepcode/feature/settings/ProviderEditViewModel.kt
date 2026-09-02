package com.deepcode.feature.settings

import androidx.lifecycle.ViewModel
import com.deepcode.core.agent.spi.AnthropicConfig
import com.deepcode.core.agent.spi.GeminiConfig
import com.deepcode.core.agent.spi.ModelConfigStore
import com.deepcode.core.agent.spi.ModelInfo
import com.deepcode.core.agent.spi.ModelProviderConfig
import com.deepcode.core.agent.spi.ModelProviderDescriptor
import com.deepcode.core.agent.spi.ModelProviderIds
import com.deepcode.core.agent.spi.ModelProviderRegistry
import com.deepcode.core.agent.spi.OpenAIConfig
import com.deepcode.core.logging.Log
import com.deepcode.core.logging.LogCategory
import com.deepcode.core.logging.LogLevel

/**
 * 添加供应商流程 ViewModel（两步：端点 → 模型；决策 D5/D6/P1）。
 *
 * - Step1（端点页）：协议单选 + Base URL / API Key / Max Tokens；「下一步」把草稿
 *   存进 [draft]（内存，不进持久化，避免把未定模型的半成品存盘）。
 * - Step2（模型页）：一键 `GET /v1/models` 拉取官方模型列表（[fetchModels]，经注册表
 *   动态实例化对应协议 Provider），也可手输兜底；「完成」按协议分派保存
 *   [ModelConfigStore.saveOpenAi]/[saveAnthropic]/[saveGemini]。
 */
class ProviderEditViewModel(
    private val store: ModelConfigStore,
    private val registry: ModelProviderRegistry,
) : ViewModel() {

    /** 可登记的 Provider 描述（单向列表，设置页据此渲染单选）。 */
    val descriptors: List<ModelProviderDescriptor> = registry.descriptors

    /** Step1 提交的端点草稿（内存态，业务进入 Step2 前不落盘）。 */
    data class Draft(
        val providerId: String,
        val baseUrl: String,
        val apiKey: String,
        val maxTokens: Int,
    )

    var draft: Draft? = null
        private set

    /** 当前激活的 Provider id（决定首次选中的单选）。 */
    fun initialProviderId(): String = store.current().providerId

    /** 当前配置的端点初值（用于 Step1 回填；未配置时为空串）。 */
    fun initialBaseUrl(): String = when (val c = store.current()) {
        is OpenAIConfig -> c.baseUrl
        is AnthropicConfig -> c.baseUrl
        is GeminiConfig -> c.baseUrl
        else -> ""
    }

    fun initialApiKey(): String = when (val c = store.current()) {
        is OpenAIConfig -> c.apiKey
        is AnthropicConfig -> c.apiKey
        is GeminiConfig -> c.apiKey
        else -> ""
    }

    fun initialMaxTokens(): Int = when (val c = store.current()) {
        is OpenAIConfig -> c.maxTokens
        is AnthropicConfig -> c.maxTokens
        is GeminiConfig -> c.maxTokens
        else -> 8192
    }

    /** Step1「下一步」：暂存端点草稿。 */
    fun commitEndpoint(providerId: String, baseUrl: String, apiKey: String, maxTokens: Int) {
        draft = Draft(providerId.trim(), baseUrl.trim(), apiKey.trim(), maxTokens)
    }

    /** Step2「一键拉取」：按草稿构造临时 Provider 并调用 listModels()。 */
    suspend fun fetchModels(): Result<List<ModelInfo>> {
        val d = draft ?: return Result.failure(IllegalStateException("尚未提交端点"))
        val descriptor = registry.resolve(d.providerId)
            ?: return Result.failure(IllegalStateException("未知协议 ${d.providerId}"))
        val config = buildConfig(d, model = "")
        return runCatching { descriptor.instantiate(config).listModels() }
    }

    /** Step2「完成」：按协议分派保存并标记激活。 */
    fun commitModel(model: String) {
        val d = draft ?: return
        if (model.isBlank()) return
        val config = buildConfig(d, model.trim())
        Log.log(
            LogLevel.INFO, LogCategory.OPERATION_USER, "Settings",
            "保存模型配置（provider=${d.providerId}, base=${config.baseUrlOr("")}, model=${model.trim()}）",
        )
        when (config) {
            is OpenAIConfig -> store.saveOpenAi(config)
            is AnthropicConfig -> store.saveAnthropic(config)
            is GeminiConfig -> store.saveGemini(config)
        }
        draft = null
    }

    /** 回退演示模型（清空真实端点）。 */
    fun selectDemo() {
        Log.log(
            LogLevel.INFO, LogCategory.OPERATION_USER, "Settings",
            "切换回演示模型",
        )
        store.resetToDemo()
    }

    private fun buildConfig(d: Draft, model: String): ModelProviderConfig = when (d.providerId) {
        ModelProviderIds.OPENAI_COMPATIBLE -> OpenAIConfig(d.baseUrl, d.apiKey, model, d.maxTokens)
        ModelProviderIds.ANTHROPIC -> AnthropicConfig(d.baseUrl, d.apiKey, model, d.maxTokens)
        ModelProviderIds.GEMINI -> GeminiConfig(d.baseUrl, d.apiKey, model, d.maxTokens)
        else -> throw IllegalStateException("无法为协议 ${d.providerId} 构建配置")
    }
}

/** 便捷取 baseUrl（用于日志）。 */
private fun ModelProviderConfig.baseUrlOr(_unused: String): String = when (this) {
    is OpenAIConfig -> baseUrl
    is AnthropicConfig -> baseUrl
    is GeminiConfig -> baseUrl
    else -> ""
}