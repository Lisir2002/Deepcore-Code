package com.deepcode.feature.settings

import androidx.lifecycle.ViewModel
import com.deepcode.core.agent.spi.ModelConfigStore
import com.deepcode.core.agent.spi.ModelProviderRegistry

/**
 * 模型设置页的状态模型（多模型：管理已保存模型 + 查看当前接入）。
 *
 * 读取 [ModelConfigStore] 快照渲染「当前接入」与「全部已保存模型」；点击条目激活、
 * 点删除移除；"配置/添加 Provider" 压栈进 [ProviderEditScreen] 分步新增。保存后
 * popBack 回来经 [models]/[activeState] 重新读取，即可立刻反映最新列表。
 */
class SettingsModelViewModel(
    private val store: ModelConfigStore,
    private val registry: ModelProviderRegistry,
) : ViewModel() {

    /** 单条已保存模型的行展示位。 */
    data class ItemUi(
        val id: String,
        val label: String,
        val modelId: String,
        val providerName: String,
        val isActive: Boolean,
    )

    /** 当前接入的状态快照（Provider + 会话模型 + 可用性）。 */
    data class ActiveState(
        val providerName: String,
        val modelId: String,
        val isComplete: Boolean,
    )

    /** 全部已保存模型（含激活标记）。 */
    fun models(): List<ItemUi> = store.listModels().map { model ->
        ItemUi(
            id = model.id,
            label = model.label.ifBlank { model.model },
            modelId = model.model,
            providerName = providerDisplayName(model.providerId),
            isActive = model.id == store.activeModelId(),
        )
    }

    /** 当前接入快照；激活模型不可用或未激活时为演示模型。 */
    fun activeState(): ActiveState {
        val active = store.activeModel()
        return if (active != null && active.isComplete()) {
            ActiveState(
                providerName = providerDisplayName(active.providerId),
                modelId = active.model,
                isComplete = true,
            )
        } else {
            ActiveState(providerName = "演示模型", modelId = "demo-1", isComplete = false)
        }
    }

    /** 激活某条已保存模型。 */
    fun activateModel(id: String) {
        store.activateModel(id)
    }

    /** 移除某条已保存模型。 */
    fun removeModel(id: String) {
        store.removeModel(id)
    }

    fun providerDisplayName(providerId: String): String =
        registry.resolve(providerId)?.displayName ?: providerId
}