package com.deepcode.agent.model

import android.content.Context
import android.content.SharedPreferences
import com.deepcode.core.agent.spi.DemoConfig
import com.deepcode.core.agent.spi.ModelConfigStore
import com.deepcode.core.agent.spi.ModelProviderConfig
import com.deepcode.core.agent.spi.SavedModel
import org.json.JSONArray
import org.json.JSONObject

/**
 * 模型供应商的本地配置（SharedPreferences 明文存储，多模型）。
 *
 * 注意：API Key 的**加密存储**（EncryptedSharedPreferences）是 M2 的独立诉求，
 * 审批评级清单里该项被单独列为待办（不在本次范围内），这里先以明文打通"接真实模型"
 * 主链路，避免因加密依赖挡住主线。
 *
 * 多模型（决策：支持保存多个模型）：
 * - 所有已保存模型以 JSON 数组存于 [KEY_MODELS_JSON]，每条 [SavedModel] 一个对象。
 * - [KEY_ACTIVE_MODEL_ID] 决定当前会话生效哪条；为空 → [current] 回退 [DemoConfig]。
 * - 旧版单配置（base_url/api_key/model 等键）在首次读取时惰性迁移为一条 [SavedModel]，
 *   不丢用户已配好的端点与密钥。
 */
class ModelEndpointConfigStore(context: Context) : ModelConfigStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("llm_config", Context.MODE_PRIVATE)

    override fun activeModelId(): String = prefs.getString(KEY_ACTIVE_MODEL_ID, null).orEmpty()

    override fun listModels(): List<SavedModel> {
        migrateLegacySingleConfigIfNeeded()
        val raw = prefs.getString(KEY_MODELS_JSON, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(SavedModel(
                        id = o.optString("id"),
                        label = o.optString("label"),
                        providerId = o.optString("providerId"),
                        baseUrl = o.optString("baseUrl"),
                        apiKey = o.optString("apiKey"),
                        model = o.optString("model"),
                        maxTokens = o.optInt("maxTokens", 8192),
                    ))
                }
                // 过滤掉恒空的僵尸条目（迁移/残缺写入的兜底）
                removeAll { it.id.isBlank() }
            }
        }.getOrDefault(emptyList())
    }

    override fun activeModel(): SavedModel? {
        val id = activeModelId()
        return listModels().firstOrNull { it.id == id }
    }

    override fun current(): ModelProviderConfig {
        val active = activeModel()
        return if (active != null && active.isComplete()) active.toConfig() else DemoConfig()
    }

    override fun saveModel(model: SavedModel): String {
        val id = model.id.ifBlank { newId() }
        val entry = model.copy(id = id)
        val list = listModels().toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) list[idx] = entry else list.add(entry)
        writeModels(list)
        return id
    }

    override fun activateModel(id: String) {
        if (listModels().any { it.id == id }) {
            prefs.edit().putString(KEY_ACTIVE_MODEL_ID, id).apply()
        }
    }

    override fun removeModel(id: String) {
        val list = listModels().filterNot { it.id == id }
        writeModels(list)
        if (activeModelId() == id) {
            // 被删的是激活模型 → 改为未激活（走演示），或自动接替首条可用模型
            val next = list.firstOrNull { it.isComplete() }?.id.orEmpty()
            prefs.edit().putString(KEY_ACTIVE_MODEL_ID, next).apply()
        }
    }

    override fun resetToDemo() {
        // 保留已保存模型列表，仅取消激活（回退演示模型）
        prefs.edit().putString(KEY_ACTIVE_MODEL_ID, "").apply()
    }

    // ─────────────────────────── 内部工具 ───────────────────────────

    private fun writeModels(list: List<SavedModel>) {
        val arr = JSONArray()
        list.forEach { m ->
            arr.put(JSONObject().apply {
                put("id", m.id)
                put("label", m.label)
                put("providerId", m.providerId)
                put("baseUrl", m.baseUrl)
                put("apiKey", m.apiKey)
                put("model", m.model)
                put("maxTokens", m.maxTokens)
            })
        }
        prefs.edit().putString(KEY_MODELS_JSON, arr.toString()).apply()
    }

    private fun newId(): String = "model-${System.currentTimeMillis()}"

    /** 旧版单配置 → 一条 [SavedModel]（首次读取时迁移，成功后清除旧键避免重复迁移）。 */
    private fun migrateLegacySingleConfigIfNeeded() {
        if (prefs.getString(KEY_MODELS_JSON, null) != null) return
        val legacyProvider = prefs.getString(KEY_LEGACY_ACTIVE_PROVIDER, null).orEmpty()
        val legacyBase = prefs.getString(KEY_LEGACY_BASE_URL, null).orEmpty()
        if (legacyProvider.isBlank() && legacyBase.isBlank()) return
        val legacy = SavedModel(
            id = newId(),
            label = legacyDefaultLabel(legacyProvider),
            providerId = legacyProvider,
            baseUrl = legacyBase,
            apiKey = prefs.getString(KEY_LEGACY_API_KEY, null).orEmpty(),
            model = prefs.getString(KEY_LEGACY_MODEL, null).orEmpty(),
            maxTokens = prefs.getInt(KEY_LEGACY_MAX_TOKENS, 8192),
        )
        val list = if (legacy.model.isBlank() && legacy.apiKey.isBlank()) {
            emptyList()
        } else {
            listOf(legacy)
        }
        writeModels(list)
        if (legacy.isComplete()) {
            prefs.edit().putString(KEY_ACTIVE_MODEL_ID, legacy.id).apply()
        }
        // 清除旧键，避免下次再次迁移
        prefs.edit()
            .remove(KEY_LEGACY_ACTIVE_PROVIDER)
            .remove(KEY_LEGACY_BASE_URL)
            .remove(KEY_LEGACY_API_KEY)
            .remove(KEY_LEGACY_MODEL)
            .remove(KEY_LEGACY_MAX_TOKENS)
            .apply()
    }

    private fun legacyDefaultLabel(providerId: String): String = when (providerId) {
        "openai" -> "OpenAI 兼容"
        "anthropic" -> "Anthropic"
        "gemini" -> "Google Gemini"
        else -> "已保存模型"
    }

    companion object {
        private const val KEY_MODELS_JSON = "models_json"
        private const val KEY_ACTIVE_MODEL_ID = "active_model_id"

        // —— 旧版单配置键（迁移后清除）——
        private const val KEY_LEGACY_ACTIVE_PROVIDER = "active_provider"
        private const val KEY_LEGACY_BASE_URL = "base_url"
        private const val KEY_LEGACY_API_KEY = "api_key"
        private const val KEY_LEGACY_MODEL = "model"
        private const val KEY_LEGACY_MAX_TOKENS = "max_tokens"
    }
}