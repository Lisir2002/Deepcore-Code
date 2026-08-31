package com.deepcode.core.agent.spi

import com.deepcode.core.model.ModelRef
import kotlinx.coroutines.flow.toList

/**
 * 上下文策略：什么时候压缩、怎么压缩。
 *
 * 手机上这不是优化项而是生存问题——8B 内存里塞 200K 上下文会直接 OOM。
 *  policy 做成接口，是为了让"裁剪 / 摘要 / 分层记忆"几种策略能独立演进。
 */
interface ContextPolicy {

    fun shouldCompact(estimatedTokens: Int, contextWindowTokens: Int): Boolean

    suspend fun compact(
        messages: List<LlmMessage>,
        provider: ModelProvider,
        modelRef: ModelRef,
    ): CompactionOutcome
}

data class CompactionOutcome(
    val messages: List<LlmMessage>,
    val summary: String,
    val tokensBefore: Int,
    val tokensAfter: Int,
)

/**
 * 保守粗略估算：中英混排约 3.5 字符/token。
 * 只用于判断是否触发压缩，计费一律以 Provider 返回的 usage 为准。
 */
fun estimateTokens(messages: List<LlmMessage>): Int {
    var chars = 0
    for (m in messages) {
        chars += m.content.length + 8
        if (m.toolCalls.isNotEmpty()) chars += m.toolCalls.sumOf { it.arguments.toString().length }
    }
    return (chars / 3.5).toInt()
}

/**
 * 默认策略：占用超过窗口 70% 时触发，保留 system + 最近若干轮，
 * 中间部分交给模型生成摘要。
 */
class DefaultContextPolicy(
    private val threshold: Double = 0.70,
    private val keepRecentMessages: Int = 12,
) : ContextPolicy {

    override fun shouldCompact(estimatedTokens: Int, contextWindowTokens: Int): Boolean =
        contextWindowTokens > 0 && estimatedTokens >= contextWindowTokens * threshold

    override suspend fun compact(
        messages: List<LlmMessage>,
        provider: ModelProvider,
        modelRef: ModelRef,
    ): CompactionOutcome {
        val tokensBefore = estimateTokens(messages)
        if (messages.size <= keepRecentMessages) {
            return CompactionOutcome(messages, "", tokensBefore, tokensBefore)
        }

        val head = messages.firstOrNull { it.role == LlmRole.SYSTEM }?.let { listOf(it) } ?: emptyList()
        val tail = messages.takeLast(keepRecentMessages)
        val middle = messages.drop(head.size).dropLast(keepRecentMessages)
        if (middle.isEmpty()) {
            return CompactionOutcome(messages, "", tokensBefore, tokensBefore)
        }

        val summary = runCatching {
            val chunks = provider.stream(
                CompletionRequest(
                    modelRef = modelRef,
                    messages = listOf(
                        LlmMessage(
                            role = LlmRole.USER,
                            content = buildString {
                                appendLine("请将以下对话历史压缩为简洁摘要，保留：用户目标、已做的修改、")
                                appendLine("关键结论、待办事项、以及所有文件路径。用中文分条列出。")
                                appendLine()
                                appendLine(middle.joinToString("\n") { "${it.role}: ${it.content}" })
                            },
                        )
                    ),
                    maxTokens = 2048,
                    enablePromptCaching = false,
                )
            ).toList()
            chunks.filterIsInstance<CompletionChunk.Text>().joinToString("") { it.text }
        }.getOrDefault("（摘要生成失败，已丢弃中间历史）")

        val compacted = head + LlmMessage(
            role = LlmRole.SYSTEM,
            content = "【历史摘要】\n$summary",
        ) + tail

        return CompactionOutcome(compacted, summary, tokensBefore, estimateTokens(compacted))
    }
}
