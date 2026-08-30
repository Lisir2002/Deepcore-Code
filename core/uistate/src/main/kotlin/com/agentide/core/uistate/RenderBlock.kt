package com.agentide.core.uistate

import com.agentide.core.model.RiskLevel
import com.agentide.core.model.ToolCall
import com.agentide.core.model.ToolError
import com.agentide.core.model.ToolKind
import com.agentide.core.model.ToolOutput

/**
 * 渲染块：事件流归约后的视图模型。
 *
 * 为什么要有这一层，而不是让 UI 直接消费 AgentEvent：
 *   事件是**流式的、碎片的**（一个字一个 delta），界面需要的是**成型的块**。
 *   如果让页面自己拼装，每个页面都会写一套拼装逻辑，然后各自走样。
 *
 * 有了这一层：
 *   AgentEvent ──(TranscriptReducer，纯 Kotlin，可单测)──▶ RenderBlock ──(RenderBlockView)──▶ 界面
 *
 * 会话页、历史回放、子 Agent 详情、通知预览——全都复用这一条链路，
 * 从结构上就不可能出现"这个页面的工具调用长得不一样"。
 */
sealed interface RenderBlock {
    /** LazyColumn 的 item key，必须稳定，否则流式更新会丢滚动位置。 */
    val key: String

    data class UserMessage(
        override val key: String,
        val text: String,
        val attachmentLabels: List<String> = emptyList(),
    ) : RenderBlock

    data class AssistantText(
        override val key: String,
        val text: String,
        val streaming: Boolean,
    ) : RenderBlock

    data class Thinking(
        override val key: String,
        val text: String,
        val streaming: Boolean,
    ) : RenderBlock

    data class ToolInvocation(
        override val key: String,
        val call: ToolCall,
        val toolName: String,
        val kind: ToolKind,
        val risk: RiskLevel,
        val status: ToolVisualStatus,
        val argumentsSummary: String,
        val output: ToolOutput? = null,
        val error: ToolError? = null,
        val progressText: String = "",
        val durationMs: Long = 0,
    ) : RenderBlock

    data class Notice(
        override val key: String,
        val kind: NoticeKind,
        val text: String,
    ) : RenderBlock

    data class TurnFooter(
        override val key: String,
        val inputTokens: Int,
        val outputTokens: Int,
        val iterations: Int,
        val durationMs: Long,
    ) : RenderBlock
}

enum class ToolVisualStatus {
    /** 已提议，正在等用户在弹窗上点同意。UI 需要画审批按钮。 */
    AWAITING_APPROVAL,
    RUNNING,
    SUCCEEDED,
    FAILED,
    DENIED,
}

enum class NoticeKind { INFO, WARNING, ERROR, COMPACTED }
