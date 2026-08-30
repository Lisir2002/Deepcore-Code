package com.agentide.core.agent.spi

import com.agentide.core.model.ModelRef
import com.agentide.core.model.ToolCall
import com.agentide.core.model.ToolSpec
import com.agentide.core.model.TurnId
import com.agentide.core.model.Usage
import kotlinx.coroutines.flow.Flow

/**
 * 模型供应商适配层。
 *
 * 各家协议差异巨大（Anthropic 的 tool_use block、OpenAI 的 tool_calls、
 * 各家 streaming 的 SSE 格式、prompt caching 的写法），但**这些差异必须烂在这一层**。
 * 对上只暴露统一的 [CompletionChunk] 流，Agent 主循环永远不需要知道
 * 自己调的是谁家的模型。
 *
 * 换模型 = 换一个实现，Runtime 一行不动。
 */
interface ModelProvider {

    /** 稳定 ID，写进 ModelRef.providerId，用于序列化后还原。 */
    val id: String

    val displayName: String

    suspend fun listModels(): List<ModelInfo> = emptyList()

    /** 是否支持某模型（用于运行时路由：主模型 / 压缩用小模型）。 */
    fun supports(modelId: String): Boolean = true

    fun stream(request: CompletionRequest): Flow<CompletionChunk>
}

data class ModelInfo(
    val id: String,
    val displayName: String,
    val contextWindowTokens: Int,
    val maxOutputTokens: Int,
    val supportsTools: Boolean = true,
    val supportsThinking: Boolean = false,
    val supportsPromptCaching: Boolean = false,
    /** 每百万 token 输入价格（美元），用于成本显示。null 表示未知。 */
    val inputPricePerMToken: Double? = null,
    val outputPricePerMToken: Double? = null,
)

// ───────────────────────── 协议中立的消息表示 ─────────────────────────

enum class LlmRole { SYSTEM, USER, ASSISTANT, TOOL }

/**
 * 与具体供应商无关的一条消息。
 * 工具结果统一以 role=TOOL + toolCallId 表达，由 Provider 实现翻译成各家协议。
 */
data class LlmMessage(
    val role: LlmRole,
    val content: String,
    val toolCallId: String? = null,
    val toolName: String? = null,
    /** assistant 消息里携带的工具调用请求。 */
    val toolCalls: List<ToolCall> = emptyList(),
)

data class CompletionRequest(
    val modelRef: ModelRef,
    val messages: List<LlmMessage>,
    val system: String? = null,
    val tools: List<ToolSpec> = emptyList(),
    val maxTokens: Int = 8192,
    val temperature: Double? = null,
    val stopSequences: List<String> = emptyList(),
    /** 开启提示缓存（若供应商支持）——手机上省钱省流量，务必支持。 */
    val enablePromptCaching: Boolean = true,
    /** 用于取消长请求。 */
    val turnId: TurnId? = null,
)

/** 流式产出的增量。UI 最终看到的 AgentEvent 由 Runtime 从这里翻译而来。 */
sealed interface CompletionChunk {

    data class Thinking(val text: String) : CompletionChunk

    data class Text(val text: String) : CompletionChunk

    /** 一批工具调用（模型可能一次返回多个）。 */
    data class ToolCalls(val calls: List<ToolCall>) : CompletionChunk

    data class UsageUpdate(val usage: Usage) : CompletionChunk

    /** 正常结束，附带停止原因。 */
    data class Done(val reason: StopReasonRaw) : CompletionChunk

    data class Error(val message: String, val retryable: Boolean = false) : CompletionChunk
}

enum class StopReasonRaw { END_TURN, MAX_TOKENS, TOOL_USE, STOP_SEQUENCE }
