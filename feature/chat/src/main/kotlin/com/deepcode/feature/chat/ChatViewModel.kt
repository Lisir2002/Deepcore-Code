package com.deepcode.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepcode.core.agent.AgentRuntime
import com.deepcode.core.agent.AgentRuntimeFactory
import com.deepcode.core.model.ApprovalScope
import com.deepcode.core.model.SessionId
import com.deepcode.core.model.ToolCall
import com.deepcode.core.uistate.RenderBlock
import com.deepcode.core.uistate.TranscriptReducer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 会话页的 ViewModel。
 *
 * 注意这里**没有**任何"消息列表"状态——界面内容完全由事件日志归约而来。
 * 好处：进程被杀重建后，ViewModel 重新走一遍 history() 就能 100% 还原界面，
 * 不需要 savedInstanceState、不需要自己维护 List<Message>。
 *
 * 多会话：构造时由 DI 注入 [AgentRuntimeFactory] + 当前会话 id（导航参数），
 * 一个会话一个 runtime，互不干扰。
 */
class ChatViewModel(
    runtimeFactory: AgentRuntimeFactory,
    conversationId: String,
) : ViewModel() {

    private val runtime: AgentRuntime = runtimeFactory.create(SessionId(conversationId))
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
        viewModelScope.launch {
            // 冷启动：先从事件日志恢复现场
            runCatching { reducer.reset(runtime.history()) }
            _blocks.update { reducer.snapshot() }

            // 再接上实时事件
            runtime.events().collect { event ->
                reducer.apply(event)
                _blocks.update { reducer.snapshot() }
                _running.update { runtime.isRunning() }
            }
        }
    }

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
