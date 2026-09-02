package com.deepcode.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepcode.core.agent.AgentRuntime
import com.deepcode.core.agent.AgentRuntimeFactory
import com.deepcode.core.agent.spi.ModelConfigStore
import com.deepcode.core.agent.spi.ModelProviderIds
import com.deepcode.core.agent.spi.SavedModel
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
 * 多模型：可从已保存模型列表 [availableModels] 切换到其它模型——[switchModel] 先写
 * [ModelConfigStore.activateModel]，再按当前会话重建 runtime 并重挂事件流，保证新消息
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

    /** 当前激活的模型 id；空表示未激活（走演示模型）。 */
    private val _activeModelId = MutableStateFlow(store.activeModelId())
    val activeModelId: StateFlow<String> = _activeModelId

    init {
        rewire()
    }

    // ─────────────── 多模型选择 ───────────────

    /** 全部已保存模型（供顶栏下拉选择）。 */
    fun availableModels(): List<SavedModel> = store.listModels()

    /** 模型 id → 显示名（顶栏/下拉展示；未激活或不存在 → 演示模型）。 */
    fun modelLabel(id: String): String {
        if (id.isBlank()) return "演示模型"
        val sm = store.listModels().firstOrNull { it.id == id }
        return sm?.label?.ifBlank { sm.model }?.takeIf { it.isNotBlank() } ?: "演示模型"
    }

    /** 切换当前会话使用的模型；传 [ModelProviderIds.DEMO] 退回首演示模型。 */
    fun switchModel(id: String) {
        if (id == ModelProviderIds.DEMO) {
            store.resetToDemo()
            _activeModelId.update { "" }
        } else {
            store.activateModel(id)
            _activeModelId.update { id }
        }
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