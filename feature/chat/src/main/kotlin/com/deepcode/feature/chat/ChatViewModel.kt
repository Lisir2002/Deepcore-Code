package com.deepcode.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepcode.core.agent.AgentRuntime
import com.deepcode.core.agent.AgentRuntimeFactory
import com.deepcode.core.agent.spi.ModelConfigStore
import com.deepcode.core.model.ApprovalScope
import com.deepcode.core.model.SessionId
import com.deepcode.core.model.ToolCall
import com.deepcode.core.uistate.RenderBlock
import com.deepcode.core.uistate.TranscriptReducer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 会话页的 ViewModel。
 *
 * 界面内容完全由事件日志归约而来，进程被杀重建后重新走一遍 history() 即可还原。
 * 多会话：DI 注入 [AgentRuntimeFactory] + 当前会话 id（导航参数），一个会话一个 runtime。
 *
 * 多供应商/多模型（供应商粒度，借鉴 deepcode-R）：可从已保存供应商的模型列表里选一个
 * [ModelChoice]（供应商 + 模型）——[switchModel] 先 [ModelConfigStore.activateProvider] +
 * [ModelConfigStore.setSelectedModel]，再按当前会话重建 runtime 并重挂事件流，保证新消息
 * 走新模型、历史仍由事件日志还原。
 */
class ChatViewModel(
    private val runtimeFactory: AgentRuntimeFactory,
    private val store: ModelConfigStore,
    conversationId: String,
) : ViewModel() {

    private val sessionId = SessionId(conversationId)
    private var runtime: AgentRuntime = runtimeFactory.create(sessionId)
    private var eventJob: Job? = null
    private val reducer = TranscriptReducer()

    private val _blocks = MutableStateFlow<List<RenderBlock>>(emptyList())
    val blocks: StateFlow<List<RenderBlock>> = _blocks

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    init {
        rewire()
    }

    // ─────────────── 多供应商/多模型选择 ───────────────

    /** 顶栏展示当前生效的「供应商 · 模型」；未激活供应商 → 演示模型。 */
    val activeLabel: String
        get() = store.activeProvider()?.let { p ->
            val n = p.displayName()
            val m = p.effectiveModel()
            if (n.isBlank() || m.isBlank()) null else "$n · $m"
        } ?: "演示模型"

    /** 是否存在已激活供应商（决定下拉里「演示模型」是否高亮）。 */
    val hasActiveProvider: Boolean
        get() = store.activeProvider() != null

    /** 一个可选择的 (供应商, 模型) 组合。 */
    data class ModelChoice(val providerId: String, val providerName: String, val modelId: String)

    /** 全部已保存供应商下的全部模型（供顶栏下拉选择）。 */
    fun availableModels(): List<ModelChoice> = store.listProviders().flatMap { p ->
        p.models.map { m -> ModelChoice(providerId = p.id, providerName = p.displayName(), modelId = m) }
    }

    /** 判断某组合是否为当前生效选项。 */
    fun isActive(choice: ModelChoice): Boolean {
        val p = store.activeProvider() ?: return false
        return p.id == choice.providerId && p.effectiveModel() == choice.modelId
    }

    /** 切换当前会话使用的 (供应商, 模型)：激活该供应商并设置其选中模型，重建 runtime。 */
    fun switchModel(choice: ModelChoice) {
        store.activateProvider(choice.providerId)
        store.setSelectedModel(choice.providerId, choice.modelId)
        rebuild()
    }

    /** 退回首演示模型（取消激活，保留已保存供应商）。 */
    fun switchToDemo() {
        store.resetToDemo()
        rebuild()
    }

    private fun rebuild() {
        // 重建运行时，让后续消息/事件走新模型；历史由事件日志还原，不丢现场。
        runtime = runtimeFactory.create(sessionId)
        rewire()
    }

    // ─────────────── 事件流挂载（可重建） ───────────────

    private fun rewire() {
        eventJob?.cancel()
        eventJob = viewModelScope.launch {
            runCatching { reducer.reset(runtime.history()) }
            _blocks.update { reducer.snapshot() }
            runtime.events().collect { event ->
                reducer.apply(event)
                _blocks.update { reducer.snapshot() }
                _running.update { runtime.isRunning() }
            }
        }
    }

    // ─────────────── 输入与操作 ───────────────

    fun onDraftChange(text: String) {
        _draft.update { text }
    }

    fun send() {
        val text = _draft.value.trim()
        if (text.isEmpty()) return
        _draft.update { "" }
        _errorMessage.update { null }
        _running.update { true }
        viewModelScope.launch {
            runCatching { runtime.submit(text) }
                // 必须显式命名 error：若用 it，内层 update{} 的 it(String) 会
                // 遮蔽外层 it(Throwable)，it.message 就解析不到了。
                .onFailure { error -> _errorMessage.update { error.message ?: "发送失败" } }
        }
    }

    fun approve(call: ToolCall, scope: ApprovalScope) {
        viewModelScope.launch {
            runCatching { runtime.respondToApproval(call, approved = true, scope = scope) }
                .onFailure { error -> _errorMessage.update { error.message ?: "授权失败" } }
        }
    }

    fun deny(call: ToolCall) {
        viewModelScope.launch {
            runCatching { runtime.respondToApproval(call, approved = false, reason = "用户在界面上拒绝") }
                .onFailure { error -> _errorMessage.update { error.message ?: "操作失败" } }
        }
    }

    fun stop() {
        viewModelScope.launch {
            runCatching { runtime.cancel("用户点击停止") }
            _running.update { false }
        }
    }

    fun dismissError() {
        _errorMessage.update { null }
    }
}