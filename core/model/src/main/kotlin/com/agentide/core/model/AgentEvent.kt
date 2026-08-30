package com.agentide.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ═════════════════════════════════════════════════════════════════════════════
// AgentEvent —— 整个 App 最核心的契约
//
// 为什么一切都走事件流：
//   1. UI 无状态。界面只是事件序列的纯函数映射（fold），不持有"当前在干嘛"这种
//      易腐状态。进程被杀后重放事件即可 100% 恢复现场。
//   2. 加功能不改 UI。新增一种工具/能力 = 新增一个事件类型 + 注册一个渲染器，
//      已有页面一行不用动。
//   3. UI 天然统一。所有页面（会话页、历史回放、子 Agent 详情、通知预览）
//      复用同一套 renderer，不存在"这个页面长得不一样"的可能。
//
// 铁律：事件是"已发生的事实"，不是"UI 指令"。事件里不出现颜色、文案模板、
// 排版提示。UI 怎么画，是渲染器的事。
// ═════════════════════════════════════════════════════════════════════════════

@Serializable
sealed interface AgentEvent {
    val id: EventId
    val sessionId: SessionId
    val turnId: TurnId
    val ts: Long
}

// ─────────────────────────── 轮次生命周期 ───────────────────────────

/** 用户发起一轮请求。一个 turn 内部可能包含多轮 LLM 调用（工具结果回灌）。 */
@Serializable
@SerialName("turn_started")
data class TurnStarted(
    override val id: EventId,
    override val sessionId: SessionId,
    override val turnId: TurnId,
    override val ts: Long,
    val userInput: String,
    val attachments: List<Attachment> = emptyList(),
) : AgentEvent

@Serializable
enum class StopReason {
    @SerialName("end_turn") END_TURN,
    @SerialName("max_tokens") MAX_TOKENS,
    @SerialName("max_turns") MAX_TURNS,
    @SerialName("cancelled") CANCELLED,
    @SerialName("awaiting_approval") AWAITING_APPROVAL,
    @SerialName("error") ERROR,
}

@Serializable
@SerialName("turn_completed")
data class TurnCompleted(
    override val id: EventId,
    override val sessionId: SessionId,
    override val turnId: TurnId,
    override val ts: Long,
    val stopReason: StopReason,
    val usage: Usage,
    val iterations: Int,
) : AgentEvent

@Serializable
enum class ErrorCode {
    @SerialName("network") NETWORK,
    @SerialName("auth") AUTH,
    @SerialName("rate_limit") RATE_LIMIT,
    @SerialName("context_overflow") CONTEXT_OVERFLOW,
    @SerialName("provider") PROVIDER,
    @SerialName("tool_not_found") TOOL_NOT_FOUND,
    @SerialName("permission_denied") PERMISSION_DENIED,
    @SerialName("cancelled") CANCELLED,
    @SerialName("internal") INTERNAL,
}

@Serializable
data class AgentError(
    val code: ErrorCode,
    val message: String,
    val retryable: Boolean = false,
    val detail: String? = null,
)

@Serializable
@SerialName("turn_failed")
data class TurnFailed(
    override val id: EventId,
    override val sessionId: SessionId,
    override val turnId: TurnId,
    override val ts: Long,
    val error: AgentError,
) : AgentEvent

@Serializable
@SerialName("turn_cancelled")
data class TurnCancelled(
    override val id: EventId,
    override val sessionId: SessionId,
    override val turnId: TurnId,
    override val ts: Long,
    val by: String,
) : AgentEvent

// ─────────────────────────── 模型输出（流式） ───────────────────────────

/** 思考过程增量。seq 单调递增，用于 UI 端去重与顺序保证。 */
@Serializable
@SerialName("thinking_delta")
data class ThinkingDelta(
    override val id: EventId,
    override val sessionId: SessionId,
    override val turnId: TurnId,
    override val ts: Long,
    val seq: Long,
    val text: String,
) : AgentEvent

/** 正文增量。 */
@Serializable
@SerialName("message_delta")
data class MessageDelta(
    override val id: EventId,
    override val sessionId: SessionId,
    override val turnId: TurnId,
    override val ts: Long,
    val seq: Long,
    val text: String,
) : AgentEvent

// ─────────────────────────── 工具调用全链路 ───────────────────────────

/** 模型提议调用某个工具（尚未执行，等待权限裁决）。 */
@Serializable
@SerialName("tool_call_proposed")
data class ToolCallProposed(
    override val id: EventId,
    override val sessionId: SessionId,
    override val turnId: TurnId,
    override val ts: Long,
    val call: ToolCall,
    val spec: ToolSpec,
    val rationale: String? = null,
) : AgentEvent

@Serializable
enum class ApprovalScope {
    /** 仅这一次。 */
    @SerialName("once") ONCE,

    /** 本会话内同签名调用都放行。 */
    @SerialName("session") SESSION,

    /** 永久记住该签名（写进用户策略）。 */
    @SerialName("always") ALWAYS,
}

@Serializable
@SerialName("tool_call_approved")
data class ToolCallApproved(
    override val id: EventId,
    override val sessionId: SessionId,
    override val turnId: TurnId,
    override val ts: Long,
    val call: ToolCall,
    val scope: ApprovalScope,
) : AgentEvent

@Serializable
@SerialName("tool_call_denied")
data class ToolCallDenied(
    override val id: EventId,
    override val sessionId: SessionId,
    override val turnId: TurnId,
    override val ts: Long,
    val call: ToolCall,
    val reason: String? = null,
) : AgentEvent

@Serializable
@SerialName("tool_call_started")
data class ToolCallStarted(
    override val id: EventId,
    override val sessionId: SessionId,
    override val turnId: TurnId,
    override val ts: Long,
    val call: ToolCall,
) : AgentEvent

/** 工具流式输出增量（命令 stdout、下载进度等）。 */
@Serializable
@SerialName("tool_output_delta")
data class ToolOutputDelta(
    override val id: EventId,
    override val sessionId: SessionId,
    override val turnId: TurnId,
    override val ts: Long,
    val callId: ToolCallId,
    val chunk: String,
) : AgentEvent

@Serializable
@SerialName("tool_call_succeeded")
data class ToolCallSucceeded(
    override val id: EventId,
    override val sessionId: SessionId,
    override val turnId: TurnId,
    override val ts: Long,
    val result: ToolResult,
) : AgentEvent

@Serializable
@SerialName("tool_call_failed")
data class ToolCallFailed(
    override val id: EventId,
    override val sessionId: SessionId,
    override val turnId: TurnId,
    override val ts: Long,
    val callId: ToolCallId,
    val error: ToolError,
) : AgentEvent

// ─────────────────────────── 上下文管理 ───────────────────────────

@Serializable
@SerialName("context_compacted")
data class ContextCompacted(
    override val id: EventId,
    override val sessionId: SessionId,
    override val turnId: TurnId,
    override val ts: Long,
    val tokensBefore: Int,
    val tokensAfter: Int,
    val summary: String,
) : AgentEvent

// ─────────────────────────── 子 Agent（预留扩展点） ───────────────────────────

@Serializable
@SerialName("sub_agent_spawned")
data class SubAgentSpawned(
    override val id: EventId,
    override val sessionId: SessionId,
    override val turnId: TurnId,
    override val ts: Long,
    val childTurnId: TurnId,
    val task: String,
    val modelRef: ModelRef? = null,
) : AgentEvent

/**
 * 子 Agent 产生的事件，原样包裹进父流。
 *
 * 这是"多 Agent"能力的预留口子：UI 的渲染器递归处理这一层即可，
 * 不需要为子 Agent 单独写一套页面——UI 统一的又一个收益点。
 */
@Serializable
@SerialName("sub_agent_event")
data class SubAgentEvent(
    override val id: EventId,
    override val sessionId: SessionId,
    override val turnId: TurnId,
    override val ts: Long,
    val childTurnId: TurnId,
    val event: AgentEvent,
) : AgentEvent

// ─────────────────────────── 伴随结构 ───────────────────────────

@Serializable
data class Usage(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val cacheReadTokens: Int = 0,
    val cacheCreationTokens: Int = 0,
) {
    val totalTokens: Int get() = inputTokens + outputTokens
}

@Serializable
sealed interface Attachment {
    @Serializable
    @SerialName("file")
    data class File(val path: String, val mimeType: String? = null) : Attachment

    @Serializable
    @SerialName("image")
    data class Image(val uri: String, val width: Int? = null, val height: Int? = null) : Attachment

    @Serializable
    @SerialName("text")
    data class Text(val title: String, val content: String) : Attachment
}
