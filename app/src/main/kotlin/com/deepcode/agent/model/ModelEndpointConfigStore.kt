package com.deepcode.agent.model

import android.content.Context
import android.content.SharedPreferences
import com.deepcode.agent.security.KeyEncryptor
import com.deepcode.core.agent.spi.DemoConfig
import com.deepcode.core.agent.spi.ModelConfigStore
import com.deepcode.core.agent.spi.ModelProviderConfig
import com.deepcode.core.agent.spi.ProviderConfig
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 模型供应商的本地配置（供应商粒度存储，借鉴 deepcode-R 的 AIProviderConfig）。
 *
 * 落盘 = SharedPreferences 里一份 JSON 数组，每条 [ProviderConfig] 一个供应商，内嵌一组
 * 模型；激活状态单独存 [KEY_ACTIVE_PROVIDER_ID]。**API Key 加密存储**：写盘前经
 * [KeyEncryptor]（Android Keystore AES-256-GCM）加密成密文，读取时解密回明文——
 * 保证密钥不以明文落盘/进备份。[saveProvider] 遵循"用户未改 Key 则保留已有密文"，
 * 避免把已保存密钥误覆盖成空。
 *
 * 兼容旧版"每条一个模型"（models_json + active_model_id）的存储：首次读取时按协议+端点
 * 归并成供应商列表，保留已激活项的选中模型，成功后清除旧键。
 */
class ModelEndpointConfigStore(
    context: Context,
    private val encryptor: KeyEncryptor = KeyEncryptor,
) : ModelConfigStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("llm_provider_config", Context.MODE_PRIVATE)

    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun activeProviderId(): String = prefs.getString(KEY_ACTIVE_PROVIDER_ID, null).orEmpty()

    /** 读取落盘明文项（apiKey 是密文，不动）。 */
    private fun diskList(): List<ProviderConfig> {
        val raw = prefs.getString(KEY_PROVIDERS_JSON, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<ProviderConfig>>(raw) }
            .getOrDefault(emptyList())
            .filter { it.id.isNotBlank() }
    }

    override fun listProviders(): List<ProviderConfig> {
        migrateLegacyIfNeeded()
        return diskList().map { it.copy(apiKey = encryptor.decrypt(it.apiKey)) }
    }

    override fun activeProvider(): ProviderConfig? {
        val id = activeProviderId()
        return listProviders().firstOrNull { it.id == id }
    }

    override fun current(): ModelProviderConfig {
        val active = activeProvider()
        return if (active != null && active.isComplete()) active.toConfig() else DemoConfig()
    }

    override fun saveProvider(provider: ProviderConfig): String {
        val id = provider.id.ifBlank { newId() }
        val existing = diskList().firstOrNull { it.id == id }
        // 用户改了 Key → 全新加密；用户没填 Key 但库里已有密文 → 保留该密文；否则空。
        val encrypted = when {
            provider.apiKey.isNotBlank() -> encryptor.encrypt(provider.apiKey.trim())
            existing != null && existing.apiKey.isNotBlank() -> existing.apiKey
            else -> ""
        }
        val list = diskList().toMutableList()
        val entry = provider.copy(id = id, apiKey = encrypted, isActive = false)
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) list[idx] = entry else list.add(entry)
        writeDisk(list)
        return id
    }

    override fun activateProvider(id: String) {
        if (diskList().any { it.id == id }) {
            prefs.edit().putString(KEY_ACTIVE_PROVIDER_ID, id).apply()
        }
    }

    override fun deleteProvider(id: String) {
        writeDisk(diskList().filterNot { it.id == id })
        if (activeProviderId() == id) {
            // 被删的是激活供应商 → 未激活（走演示），或自动接替首个可用供应商
            val next = listProviders().firstOrNull { it.isComplete() }?.id.orEmpty()
            prefs.edit().putString(KEY_ACTIVE_PROVIDER_ID, next).apply()
        }
    }

    override fun setSelectedModel(providerId: String, model: String) {
        val list = diskList().toMutableList()
        val idx = list.indexOfFirst { it.id == providerId }
        if (idx < 0) return
        val current = list[idx]
        if (current.models.contains(model)) {
            list[idx] = current.copy(selectedModel = model)
            writeDisk(list)
        }
    }

    override fun updateModels(providerId: String, models: List<String>) {
        val list = diskList().toMutableList()
        val idx = list.indexOfFirst { it.id == providerId }
        if (idx < 0) return
        val current = list[idx]
        val distinct = models.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val selected = if (distinct.contains(current.selectedModel)) current.selectedModel else ""
        list[idx] = current.copy(models = distinct, selectedModel = selected)
        writeDisk(list)
    }

    override fun resetToDemo() {
        // 保留已保存供应商，仅取消激活（回退演示模型）
        prefs.edit().putString(KEY_ACTIVE_PROVIDER_ID, "").apply()
    }

    // ─────────────────────────── 内部工具 ───────────────────────────

    private fun writeDisk(list: List<ProviderConfig>) {
        prefs.edit().putString(KEY_PROVIDERS_JSON, json.encodeToString(list)).apply()
    }

    private fun newId(): String = "provider-${System.currentTimeMillis()}"

    /**
     * 旧版"每条一个模型"（models_json: List<SavedModel>）→ 供应商粒度。
     * 按 (协议, 端点) 归并成 [ProviderConfig]，models=该端点下所有模型，选中模型取自
     * 旧激活项的 model；迁移后清除旧键，幂等（providers_json 存在即跳过）。
     */
    private fun migrateLegacyIfNeeded() {
        if (prefs.getString(KEY_PROVIDERS_JSON, null) != null) return
        val rawModels = prefs.getString(KEY_LEGACY_MODELS_JSON, null) ?: return
        val oldArray: JsonArray = runCatching {
            json.parseToJsonElement(rawModels).jsonArray
        }.getOrNull() ?: return
        if (oldArray.isEmpty()) {
            prefs.edit().remove(KEY_LEGACY_MODELS_JSON).apply()
            return
        }

        // 旧激活的 SavedModel id → 其 model，用于还原供应商的 selectedModel。
        val oldActiveModelId = prefs.getString(KEY_LEGACY_ACTIVE_MODEL_ID, null).orEmpty()

        // key = "providerId|baseUrl" → 聚合态
        val groups = linkedMapOf<String, MutableList<Map<String, String>>>()
        var activeModelTarget: String? = null
        oldArray.forEach { el ->
            val o = el.jsonObject
            val savedId = o["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val providerId = o["providerId"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val baseUrl = o["baseUrl"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val providerName = when (providerId) {
                "openai" -> "OpenAI 兼容"
                "anthropic" -> "Anthropic"
                "gemini" -> "Google Gemini"
                else -> "已保存供应商"
            }
            groups.getOrPut("$providerId|$baseUrl") { mutableListOf() }
                .add(
                    mapOf(
                        "name" to providerName,
                        "apiKey" to (o["apiKey"]?.jsonPrimitive?.contentOrNull.orEmpty()),
                        "model" to (o["model"]?.jsonPrimitive?.contentOrNull.orEmpty()),
                        "maxTokens" to (o["maxTokens"]?.jsonPrimitive?.contentOrNull ?: "8192"),
                    ),
                )
            if (savedId == oldActiveModelId) {
                activeModelTarget = o["model"]?.jsonPrimitive?.contentOrNull.orEmpty()
            }
        }

        val migrated = groups.map { (groupKey, members) ->
            val (providerId, baseUrl) = groupKey.split("|", limit = 2).let { it[0] to it.getOrNull(1).orEmpty() }
            val first = members.first()
            val models = members.mapNotNull { it["model"] }.filter { it.isNotBlank() }.distinct()
            ProviderConfig(
                id = newId(),
                name = first["name"].orEmpty(),
                providerId = providerId,
                baseUrl = baseUrl,
                apiKey = first["apiKey"].orEmpty(), // 迁移随后统一加密
                maxTokens = first["maxTokens"]?.toIntOrNull() ?: 8192,
                models = models,
                selectedModel = if (activeModelTarget != null && models.contains(activeModelTarget)) {
                    activeModelTarget
                } else {
                    models.firstOrNull().orEmpty()
                },
            )
        }

        // 统一走 saveProvider：加密 apiKey、去重保序。
        migrated.forEach { entry ->
            val encrypted = if (entry.apiKey.isNotBlank()) encryptor.encrypt(entry.apiKey.trim()) else ""
            writeDisk(diskList() + entry.copy(apiKey = encrypted))
        }
        // 尝试恢复激活：迁移得到的 provider 中，第一个「selectedModel 非空且完整」者设激活。
        val activateId = migrated.firstOrNull { it.isComplete() }?.id?.takeIf { it.isNotBlank() }
        if (activateId != null) prefs.edit().putString(KEY_ACTIVE_PROVIDER_ID, activateId).apply()

        prefs.edit()
            .remove(KEY_LEGACY_MODELS_JSON)
            .remove(KEY_LEGACY_ACTIVE_MODEL_ID)
            .apply()
    }

    companion object {
        private const val KEY_PROVIDERS_JSON = "providers_json"
        private const val KEY_ACTIVE_PROVIDER_ID = "active_provider_id"

        // —— 旧版"每条一个模型"键（迁移后清除）——
        private const val KEY_LEGACY_MODELS_JSON = "models_json"
        private const val KEY_LEGACY_ACTIVE_MODEL_ID = "active_model_id"
    }
}