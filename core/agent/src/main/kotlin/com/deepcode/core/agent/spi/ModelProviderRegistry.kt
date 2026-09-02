package com.deepcode.core.agent.spi

/**
 * 模型供应商注册表契约（决策 D1：注册表统一）。
 *
 * 把「配置 → Provider 实例」的映射收敛为可插拔注册表；UI（设置页）遍历
 * [descriptors] 渲染单选/表单，Factory 按 providerId resolve 后实例化。
 * 新增厂商在实现方（:app）追加一条 [ModelProviderDescriptor] 即可。
 */
interface ModelProviderRegistry {

    /** 所有可登记的 Provider 描述（设置页据此渲染单选/表单）。 */
    val descriptors: List<ModelProviderDescriptor>

    /** 按 [ModelProviderDescriptor.id] 解析；不存在返回 null（调用方决定 Demo 兜底）。 */
    fun resolve(providerId: String): ModelProviderDescriptor?
}

/**
 * 单个 Provider 的登记描述，驱动设置页渲染与工厂构造。
 *
 * - [requiresConfig] = true 时设置页渲染动态配置表单，否则视为无表单（如 Demo）。
 * - [factory] 吃 [ModelProviderConfig] 产出 [ModelProvider]。
 */
class ModelProviderDescriptor(
    val id: String,
    val displayName: String,
    val requiresConfig: Boolean,
    private val factory: (ModelProviderConfig) -> ModelProvider,
) {
    fun instantiate(config: ModelProviderConfig): ModelProvider = factory(config)
}