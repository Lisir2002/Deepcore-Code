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
import com.deepcode.core.agent.spi.SavedModel
import com.deepcode.core.agent.spi.savedModelOf
import com.deepcode.core.logging.Log
import com.deepcode.core.logging.LogCategory
import com.deepcode.core.logging.LogLevel

/**
 * 添加供应商流程 ViewModel（两步：端点 → 模型；决策 D5/D6/P1）。
 *
 * - Step1（端点页）：协议单选 + Base URL / API Key / Max Tokens；「下一步」把草稿
 *   存进 [ProviderEditFlow.draft]（内存态，不进持久化，避免把未定模型的半成品存盘）。
 * - Step2（模型页）：一键 `GET /v1/models` 拉取官方模型列表（[fetchModels]，经注册表
 *   动态实例化对应协议 Provider），也可手输兜底；「完成」**新增一条** [SavedModel]
 *   持久化并标记激活（多模型：不影响既有模型）。
 *
 * ⚠ Step1 与 Step2 是两个独立导航目的地，各持一个 [ProviderEditViewModel] 实例
 * （Koin ViewModel 按目的地隔离）。为了让 Step2 读到 Step1 暂存的草稿，草稿挂在
 * 注入的单例 [ProviderEditFlow] 上，两屏共享同一份内存态。
 */
class ProviderEditViewModel(
    private val store: ModelConfigStore,
    private val registry: ModelProviderRegistry,
    private val flow: ProviderEditFlow,
) : ViewModel() {

    /** 可登记的 Provider 描述（单向列表，设置页据此渲染单选/手风琴）。 */
    val descriptors: List<ModelProviderDescriptor> = registry.descriptors

    /** Step1 提交的端点草稿（跨屏共享，内存态）。 */
    val draft: ProviderEditFlow.Draft?
        get() = flow.draft

    /** 当前生效的 Provider id（编辑点进来时回填）。 */
    fun initialProviderId(): String = store.current().providerId

    /** 当前配置的端点初值（回填 Step1；未配置时为空串）。 */
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

    /** Step1「下一步」：暂存端点草稿到共享单例，供 Step2 读取。 */
    fun commitEndpoint(providerId: String, baseUrl: String, apiKey: String, maxTokens: Int) {
        flow.draft = ProviderEditFlow.Draft(providerId.trim(), baseUrl.trim(), apiKey.trim(), maxTokens)
    }

    /** Step2「一键拉取」：按草稿构造临时 Provider 并调用 listModels()。 */
    suspend fun fetchModels(): Result<List<ModelInfo>> {
        val d = flow.draft ?: return Result.failure(IllegalStateException("尚未配置端点"))
        val descriptor = registry.resolve(d.providerId)
            ?: return Result.failure(IllegalStateException("未知协议 ${d.providerId}"))
        val config = buildConfig(d, model = "")
        return runCatching { descriptor.instantiate(config).listModels() }
    }

    /** 回退演示模型（取消激活，保留已保存模型列表）。 */
    fun selectDemo() {
        Log.log(
            LogLevel.INFO, LogCategory.OPERATION_USER, "Settings",
            "切换回演示模型",
        )
        store.resetToDemo()
    }

    /** Step2「完成」：新增一条 [SavedModel] 并标记激活（多模型）。 */
    fun commitModel(model: String) {
        val d = flow.draft ?: return
        if (model.isBlank()) return
        val config = buildConfig(d, model.trim())
        val providerLabel = registry.resolve(d.providerId)?.displayName ?: d.providerId
        val saved = savedModelOf(config).copy(
            label = "${model.trim()} · $providerLabel",
        )
        val id = store.saveModel(saved)
        store.activateModel(id)
        Log.log(
            LogLevel.INFO, LogCategory.OPERATION_USER, "Settings",
            "新增模型（provider=${d.providerId}, model=${model.trim()}, id=$id）",
        )
        flow.draft = null
    }

    private fun buildConfig(d: ProviderEditFlow.Draft, model: String): ModelProviderConfig = when (d.providerId) {
        ModelProviderIds.OPENAI_COMPATIBLE -> OpenAIConfig(d.baseUrl, d.apiKey, model, d.maxTokens)
        ModelProviderIds.ANTHROPIC -> AnthropicConfig(d.baseUrl, d.apiKey, model, d.maxTokens)
        ModelProviderIds.GEMINI -> GeminiConfig(d.baseUrl, d.apiKey, model, d.maxTokens)
        else -> throw IllegalStateException("无法为协议 ${d.providerId} 构建配置")
    }
}

/**
 * 端点草稿的跨屏单例载具（Step1 → Step2）。
 *
 * 两步是独立导航目的地、独立 ViewModel 实例；把草稿从某一步的 ViewModel 挪到这里，
 * 避免因实例隔离导致 Step2 拿不到、从而无法启用"一键拉取"。
 * 生命周期：设置模块级单例，仅保留内存态，进程退出即失。
 */
class ProviderEditFlow {

    /** Step1 提交的端点草稿（内存态，业务进入 Step2 前不落盘）。 */
    data class Draft(
        val providerId: String,
        val baseUrl: String,
        val apiKey: String,
        val maxTokens: Int,
    )

    var draft: Draft? = null
}