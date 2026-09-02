package com.deepcode.agent.model

import com.deepcode.agent.demo.DemoProvider
import com.deepcode.core.agent.spi.DemoConfig
import com.deepcode.core.agent.spi.ModelProviderDescriptor
import com.deepcode.core.agent.spi.ModelProviderIds
import com.deepcode.core.agent.spi.ModelProviderRegistry
import com.deepcode.core.agent.spi.OpenAIConfig

/**
 * 默认注册表：内建演示模型 + OpenAI 兼容两条（决策 D1：注册表统一）。
 *
 * 实现 [ModelProviderRegistry]，把「配置 → Provider 实例」的映射收敛为可插拔表；
 * AgentRuntimeFactory、设置页、Demo 回退一律经本表解析，不再各自维护 if/else。
 * 未来新增厂商（DeepSeek / Anthropic ...）在此追加一个 [ModelProviderDescriptor] 即可，
 * UI 与 Factory 无需改动。
 */
class DefaultProviderRegistry : ModelProviderRegistry {

    private val demo = ModelProviderDescriptor(
        id = DemoConfig().providerId,
        displayName = DemoConfig().displayName,
        requiresConfig = false,
    ) { _ -> DemoProvider() }

    private val openAi = ModelProviderDescriptor(
        id = OpenAIConfig().providerId,
        displayName = "OpenAI 兼容",
        requiresConfig = true,
    ) { config ->
        OkHttpProvider(config as OpenAIConfig)
    }

    override val descriptors: List<ModelProviderDescriptor> = listOf(openAi, demo)

    override fun resolve(providerId: String): ModelProviderDescriptor? =
        descriptors.firstOrNull { it.id == providerId }

    /** 解析/配置缺失时的兜底描述（始终可用）。 */
    fun demoDescriptor(): ModelProviderDescriptor = resolve(ModelProviderIds.DEMO) ?: demo
}