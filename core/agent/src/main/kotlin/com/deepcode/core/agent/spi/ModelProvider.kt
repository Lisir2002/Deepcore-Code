package com.deepcode.core.agent.spi

import com.deepcode.core.model.ModelRef
import com.deepcode.core.model.ToolCall
import com.deepcode.core.model.ToolSpec
import com.deepcode.core.model.TurnId
import com.deepcode.core.model.Usage
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

    /**
     * 测试某模型的连通性（借鉴 deepcode-R ModelApiService）：对该模型发一条极短最小请求，
     * 返回耗时与结果。各协议实现应复用本 Provider 的鉴权与端点逻辑；未实现时默认报失败。
     */
    suspend fun testModel(modelId: String): ModelTestResult =
        ModelTestResult(success = false, latencyMs = 0, message = "此协议未实现连通性测试")

    fun stream(request: CompletionRequest): Flow<CompletionChunk>
}

/** 单次模型连通性测试的结果（供设置页「测试」按钮展示）。 */
data class ModelTestResult(
    val success: Boolean,
    val latencyMs: Long,
    val message: String,
)

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

/** 多模态图片：base64 内联数据 + MIME 类型，由 Provider 翻译成各家协议的图片块。 */
data class LlmImage(
    val base64Data: String,
    val mimeType: String,
)

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
    /** 附带的图片（多模态输入）。非空时 Provider 以图片块形式发送，text 与图片并存。 */
    val images: List<LlmImage> = emptyList(),
    /**
     * 思考模式：assistant 的推理过程（对应 OpenAI/DeepSeek 的 reasoning_content）。
     * 非空时需原样回传给 API，否则 DeepSeek 思考模式多轮/工具循环会报 400。
     */
    val reasoning: String? = null,
    /** Anthropic extended thinking 的加密签名（thinking block 的 signature）。随 thinking 原样回传，否则 400。其他供应商为 null。 */
    val signature: String? = null,
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

    /**
     * Anthropic extended thinking 的加密签名（thinking block 的 signature）。
     * 一轮回复产出后由 Provider 下发，供多轮/工具循环判定后原样回传。其他供应商不产出。
     */
    data class Signature(val text: String) : CompletionChunk

    data class UsageUpdate(val usage: Usage) : CompletionChunk

    /** 正常结束，附带停止原因。 */
    data class Done(val reason: StopReasonRaw) : CompletionChunk

    data class Error(val message: String, val retryable: Boolean = false) : CompletionChunk
}

enum class StopReasonRaw { END_TURN, MAX_TOKENS, TOOL_USE, STOP_SEQUENCE }
