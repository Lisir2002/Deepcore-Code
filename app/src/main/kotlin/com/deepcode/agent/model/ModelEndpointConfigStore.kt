package com.deepcode.agent.model

import android.content.Context
import android.content.SharedPreferences

/**
 * 模型端点的本地配置（SharedPreferences 明文存储）。
 *
 * 注意：API Key 的**加密存储**（EncryptedSharedPreferences）是 M2 的独立诉求，
 * 审批评级清单里该项被单独列为待办（不在本次 1/3/4/5/6 范围内），这里先以
 * 明文 SharedPreferences 打通"接真实模型"主链路，避免因加密依赖挡住主线。
 *
 * 未配置任何模型时 [config] 返回 null，DI 层据此回退到 [DemoProvider]。
 */
class ModelEndpointConfigStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("llm_config", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, null).orEmpty()
        set(value) = prefs.edit().putString(KEY_BASE_URL, value).apply()

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, null).orEmpty()
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    var model: String
        get() = prefs.getString(KEY_MODEL, null).orEmpty()
        set(value) = prefs.edit().putString(KEY_MODEL, value).apply()

    /** 是否已有"可用"的完整配置（建得成一个有效端点）。 */
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

    /** 端到端配置对象（不完整时返回 null）。 */
    fun config(): LlmEndpointConfig? =
        if (isConfigured) LlmEndpointConfig(baseUrl = baseUrl, apiKey = apiKey, model = model) else null

    companion object {
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
    }
}