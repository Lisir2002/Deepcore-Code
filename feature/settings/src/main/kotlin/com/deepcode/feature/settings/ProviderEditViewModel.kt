package com.deepcode.feature.settings

import androidx.lifecycle.ViewModel
import com.deepcode.core.agent.spi.ModelConfigStore
import com.deepcode.core.agent.spi.ModelProviderDescriptor
import com.deepcode.core.agent.spi.ModelProviderRegistry
import com.deepcode.core.agent.spi.OpenAIConfig
import com.deepcode.core.logging.Log
import com.deepcode.core.logging.LogCategory
import com.deepcode.core.logging.LogLevel

/**
 * 二级编辑页 ViewModel（决策 D5/D7）：Provider 单选 + OpenAI 兼容动态表单 + 保存。
 *
 * - [descriptors] 来自注册表（决策 D1），供页面渲染单选。
 * - [initial] 提供当前生效的 OpenAI 配置作为表单初值；非 OpenAI 则给空表单。
 * - [saveOpenAi] 落到 [ModelConfigStore.saveOpenAi] 并标记激活；[selectDemo] 回退脚手架。
 * 保存成功由页面 popBack 返回，模型页在恢复时重读快照。
 */
class ProviderEditViewModel(
    private val store: ModelConfigStore,
    private val registry: ModelProviderRegistry,
) : ViewModel() {

    /** 可登记的 Provider 描述（单向列表，设置页据此渲染单选）。 */
    val descriptors: List<ModelProviderDescriptor> = registry.descriptors

    /** 当前激活的 Provider id（决定首次选中的单选）。 */
    fun initialProviderId(): String = store.current().providerId

    /** 当前生效的 OpenAI 配置（编辑表单初值；非 OpenAI 返回空表单）。 */
    fun initialConfig(): OpenAIConfig = store.current().let { it as? OpenAIConfig ?: OpenAIConfig() }

    /** 保存一组 OpenAI 兼容配置并标记激活（决策 D4：单配置，切换即覆盖）。 */
    fun saveOpenAi(baseUrl: String, apiKey: String, model: String, maxTokens: Int) {
        val config = OpenAIConfig(
            baseUrl = baseUrl.trim(),
            apiKey = apiKey.trim(),
            model = model.trim(),
            maxTokens = maxTokens,
        )
        Log.log(
            LogLevel.INFO, LogCategory.OPERATION_USER, "Settings",
            "保存 OpenAI 兼容模型配置（base=${config.baseUrl}, model=${config.model}）",
        )
        store.saveOpenAi(config)
    }

    /** 回退演示模型（清空真实端点）。 */
    fun selectDemo() {
        Log.log(
            LogLevel.INFO, LogCategory.OPERATION_USER, "Settings",
            "切换回演示模型",
        )
        store.resetToDemo()
    }
}