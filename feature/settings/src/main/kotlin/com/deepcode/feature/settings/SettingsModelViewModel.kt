package com.deepcode.feature.settings

import androidx.lifecycle.ViewModel
import com.deepcode.core.agent.spi.ModelConfigStore
import com.deepcode.core.agent.spi.ModelProviderRegistry

/**
 * 模型设置页的状态模型（供应商粒度多模型：管理已保存供应商 + 查看当前接入）。
 *
 * 读取 [ModelConfigStore] 快照渲染「当前接入」与「全部已保存供应商」；点击条目激活、
 * 点删除移除；"添加供应商"压栈进 [ProviderEditScreen] 分步新增。保存后 popBack 回来
 * 经 [providers]/[activeState] 重新读取，即可立刻反映最新列表。
 */
class SettingsModelViewModel(
    private val store: ModelConfigStore,
    private val registry: ModelProviderRegistry,
) : ViewModel() {

    /** 单个已保存供应商的行展示位。 */
    data class ProviderUi(
        val id: String,
        val label: String,
        val modelCount: Int,
        val effectiveModel: String,
        val protocolName: String,
        val isActive: Boolean,
        val isComplete: Boolean,
    )

    /** 当前接入的状态快照（激活供应商 + 会话模型 + 可用性）。 */
    data class ActiveState(
        val providerName: String,
        val modelId: String,
        val isComplete: Boolean,
    )

    /** 全部已保存供应商（含激活标记）。 */
    fun providers(): List<ProviderUi> = store.listProviders().map { p ->
        ProviderUi(
            id = p.id,
            label = p.displayName(),
            modelCount = p.models.size,
            effectiveModel = p.effectiveModel(),
            protocolName = registry.resolve(p.providerId)?.displayName ?: p.providerId,
            isActive = p.id == store.activeProviderId(),
            isComplete = p.isComplete(),
        )
    }

    /** 当前接入快照；激活供应商不可用或未激活时为演示模型。 */
    fun activeState(): ActiveState {
        val active = store.activeProvider()
        return if (active != null && active.isComplete()) {
            ActiveState(
                providerName = active.displayName(),
                modelId = active.effectiveModel(),
                isComplete = true,
            )
        } else {
            ActiveState(providerName = "演示模型", modelId = "demo-1", isComplete = false)
        }
    }

    /** 激活某条已保存供应商（先取消其它供应商的激活标记）。 */
    fun activateProvider(id: String) {
        store.activateProvider(id)
    }

    /** 移除某条已保存供应商（若它为激活态则由存储方改为未激活/自动接替）。 */
    fun deleteProvider(id: String) {
        store.deleteProvider(id)
    }
}