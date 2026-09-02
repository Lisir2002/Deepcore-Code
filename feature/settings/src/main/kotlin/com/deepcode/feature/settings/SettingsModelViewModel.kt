package com.deepcode.feature.settings

import androidx.lifecycle.ViewModel
import com.deepcode.core.agent.spi.ModelConfigStore
import com.deepcode.core.agent.spi.ModelProviderConfig
import com.deepcode.core.agent.spi.OpenAIConfig
import com.deepcode.core.agent.spi.modelOf

/**
 * 模型设置首页的状态模型（决策 D5：模型页 = 状态 + 入口，编辑在二级页）。
 *
 * 只读展示「当前生效 Provider + 会话模型（modelId）+ 配置是否可用如何」，点击
 * "配置 Provider" 压栈进 [ProviderEditScreen]。状态经 [ModelConfigStore.current] 快照，
 * 以 Provider 补全展示位（displayName / baseUrl 等）。
 */
class SettingsModelViewModel(
    private val store: ModelConfigStore,
) : ViewModel() {

    /** 模型页 UI 状态的只读快照。 */
    data class UiState(
        val providerName: String,
        val modelId: String,
        val isComplete: Boolean,
        val detail: String,
    )

    /**
     * 当前生效状态快照。页面在每次重组（含从二级编辑页返回）时重新读取，
     * 保证保存后回到模型页能立刻反映最新 Provider / 会话模型。
     */
    fun currentState(): UiState {
        val config: ModelProviderConfig = store.current()
        return UiState(
            providerName = config.displayName,
            modelId = modelOf(config),
            isComplete = config.isComplete(),
            detail = when (config) {
                is OpenAIConfig -> config.baseUrl
                else -> "演示模型（未接真实端点）"
            },
        )
    }
}