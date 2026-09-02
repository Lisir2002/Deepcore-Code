package com.deepcode.agent.model

import android.content.Context
import android.content.SharedPreferences
import com.deepcode.core.agent.spi.DemoConfig
import com.deepcode.core.agent.spi.ModelConfigStore
import com.deepcode.core.agent.spi.ModelProviderConfig
import com.deepcode.core.agent.spi.ModelProviderIds
import com.deepcode.core.agent.spi.OpenAIConfig

/**
 * 模型供应商的本地配置（SharedPreferences 明文存储）。
 *
 * 注意：API Key 的**加密存储**（EncryptedSharedPreferences）是 M2 的独立诉求，
 * 审批评级清单里该项被单独列为待办（不在本次 1/3/4/5/6 范围内），这里先以
 * 明文 SharedPreferences 打通"接真实模型"主链路，避免因加密依赖挡住主线。
 *
 * 按决策 D1/D2/D5 演进为**单配置 + 类型化**：持久化「当前选中的 Provider ID +
 * 该 Provider 的可编辑字段」。当前只落地 OpenAI 兼容（[OpenAIConfig]）；未配置或
 * Provider 不可用时 [current] 返回 [DemoConfig] 兜底。契约见 core-spi [ModelConfigStore]。
 */
class ModelEndpointConfigStore(context: Context) : ModelConfigStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("llm_config", Context.MODE_PRIVATE)

    var activeProviderId: String
        get() = prefs.getString(KEY_ACTIVE_PROVIDER, null).orEmpty()
        set(value) = prefs.edit().putString(KEY_ACTIVE_PROVIDER, value).apply()

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, null).orEmpty()
        set(value) = prefs.edit().putString(KEY_BASE_URL, value).apply()

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, null).orEmpty()
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    var model: String
        get() = prefs.getString(KEY_MODEL, null).orEmpty()
        set(value) = prefs.edit().putString(KEY_MODEL, value).apply()

    var maxTokens: Int
        get() = prefs.getInt(KEY_MAX_TOKENS, 8192)
        set(value) = prefs.edit().putInt(KEY_MAX_TOKENS, value).apply()

    /** 当前生效的类型化配置；Provider 缺失或字段不完整时回退 [DemoConfig]。 */
    override fun current(): ModelProviderConfig {
        if (activeProviderId == ModelProviderIds.OPENAI_COMPATIBLE) {
            val openAi = OpenAIConfig(
                baseUrl = baseUrl,
                apiKey = apiKey,
                model = model,
                maxTokens = maxTokens,
            )
            return if (openAi.isComplete()) openAi else DemoConfig()
        }
        return DemoConfig()
    }

    /** 保存一组 OpenAI 兼容配置并标记为激活。 */
    override fun saveOpenAi(config: OpenAIConfig) {
        activeProviderId = config.providerId
        baseUrl = config.baseUrl
        apiKey = config.apiKey
        model = config.model
        maxTokens = config.maxTokens
    }

    /** 清空为演示模型（回退脚手架）。 */
    override fun resetToDemo() {
        activeProviderId = DemoConfig().providerId
        baseUrl = ""
        apiKey = ""
        model = ""
        maxTokens = 8192
    }

    companion object {
        private const val KEY_ACTIVE_PROVIDER = "active_provider"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_MAX_TOKENS = "max_tokens"
    }
}