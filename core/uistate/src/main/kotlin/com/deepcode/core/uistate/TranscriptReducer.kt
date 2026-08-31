package com.deepcode.core.uistate

import com.deepcode.core.model.AgentEvent
import com.deepcode.core.model.ContextCompacted
import com.deepcode.core.model.MessageDelta
import com.deepcode.core.model.RiskLevel
import com.deepcode.core.model.ThinkingDelta
import com.deepcode.core.model.ToolCall
import com.deepcode.core.model.ToolCallApproved
import com.deepcode.core.model.ToolCallDenied
import com.deepcode.core.model.ToolCallFailed
import com.deepcode.core.model.ToolCallProposed
import com.deepcode.core.model.ToolCallStarted
import com.deepcode.core.model.ToolCallSucceeded
import com.deepcode.core.model.ToolOutputDelta
import com.deepcode.core.model.TurnCompleted
import com.deepcode.core.model.TurnFailed
import com.deepcode.core.model.TurnStarted
import kotlinx.serialization.json.jsonPrimitive

/**
 * 事件流 → 渲染块 的归约器。
 *
 * **这是纯 Kotlin，不碰 Compose**，所以能直接单测。
 * UI 层最容易出 bug 的地方恰恰是这类增量拼接逻辑（重名覆盖、顺序错乱、
 * 流式追加丢失），放在可测的纯函数里，比放在 Composable 里靠肉眼靠谱得多。
 */
class TranscriptReducer {

    private val blocks = LinkedHashMap<String, RenderBlock>()
    private var activeTextKey: String? = null
    private var activeThinkingKey: String? = null
    private var turnStartTs: Long = 0L

    fun apply(event: AgentEvent) {
        when (event) {
            is TurnStarted -> {
                turnStartTs = event.ts
                closeTextRun()
                add(
                    RenderBlock.UserMessage(
                        key = "user-${event.turnId.value}",
                        text = event.userInput,
                        attachmentLabels = event.attachments.map { labelOf(it) },
                    )
                )
            }

            is ThinkingDelta -> {
                thinkingKey(event)?.let { key ->
                    val previous = blocks[key] as? RenderBlock.Thinking
                    blocks[key] = RenderBlock.Thinking(
                        key = key,
                        text = (previous?.text.orEmpty()) + event.text,
                        streaming = true,
                    )
                }
            }

            is MessageDelta -> {
                textKey(event)?.let { key ->
                    val previous = blocks[key] as? RenderBlock.AssistantText
                    blocks[key] = RenderBlock.AssistantText(
                        key = key,
                        text = (previous?.text.orEmpty()) + event.text,
                        streaming = true,
                    )
                }
            }

            is ToolCallProposed -> {
                // 工具出现意味着上一段正文结束，后续正文要另起一块
                closeTextRun()
                blocks["tool-${event.call.id.value}"] = RenderBlock.ToolInvocation(
                    key = "tool-${event.call.id.value}",
                    call = event.call,
                    toolName = event.spec.name,
                    kind = event.spec.kind,
                    risk = event.spec.riskLevel,
                    status = if (event.spec.riskLevel == RiskLevel.READ_ONLY) {
                        ToolVisualStatus.RUNNING
                    } else {
                        ToolVisualStatus.AWAITING_APPROVAL
                    },
                    argumentsSummary = summarize(event.call),
                )
            }

            is ToolCallApproved -> updateTool(event.call.id.value) { block ->
                block.copy(status = ToolVisualStatus.RUNNING)
            }

            is ToolCallStarted -> updateTool(event.call.id.value) { block ->
                block.copy(status = ToolVisualStatus.RUNNING)
            }

            is ToolOutputDelta -> updateTool(event.callId.value) { block ->
                block.copy(progressText = block.progressText + event.chunk)
            }

            is ToolCallSucceeded -> updateTool(event.result.callId.value) { block ->
                block.copy(
                    status = ToolVisualStatus.SUCCEEDED,
                    output = event.result.output,
                    durationMs = event.result.durationMs,
                )
            }

            is ToolCallFailed -> updateTool(event.callId.value) { block ->
                block.copy(status = ToolVisualStatus.FAILED, error = event.error)
            }

            is ToolCallDenied -> updateTool(event.call.id.value) { block ->
                block.copy(status = ToolVisualStatus.DENIED)
            }

            is ContextCompacted -> {
                closeTextRun()
                add(
                    RenderBlock.Notice(
                        key = "compact-${event.id.value}",
                        kind = NoticeKind.COMPACTED,
                        text = "上下文已压缩：${event.tokensBefore} → ${event.tokensAfter} tokens",
                    )
                )
            }

            is TurnFailed -> {
                closeTextRun()
                add(
                    RenderBlock.Notice(
                        key = "fail-${event.id.value}",
                        kind = NoticeKind.ERROR,
                        text = event.error.message,
                    )
                )
            }

            is TurnCompleted -> {
                closeTextRun()
                add(
                    RenderBlock.TurnFooter(
                        key = "footer-${event.id.value}",
                        inputTokens = event.usage.inputTokens,
                        outputTokens = event.usage.outputTokens,
                        iterations = event.iterations,
                        durationMs = (event.ts - turnStartTs).coerceAtLeast(0),
                    )
                )
            }

            else -> Unit
        }
    }

    /** 冷启动恢复：把历史事件一次性喂进来即可，界面原样重现。 */
    fun reset(events: List<AgentEvent>) {
        blocks.clear()
        activeTextKey = null
        activeThinkingKey = null
        events.forEach { apply(it) }
    }

    fun snapshot(): List<RenderBlock> = blocks.values.toList()

    // ─────────────────────────── 内部 ───────────────────────────

    private fun add(block: RenderBlock) {
        blocks[block.key] = block
    }

    private fun updateTool(callIdValue: String, transform: (RenderBlock.ToolInvocation) -> RenderBlock.ToolInvocation) {
        val key = "tool-$callIdValue"
        val current = blocks[key] as? RenderBlock.ToolInvocation ?: return
        blocks[key] = transform(current)
    }

    private fun textKey(event: AgentEvent): String {
        val current = activeTextKey
        if (current != null) return current
        val created = "text-${event.turnId.value}-${blocks.size}"
        activeTextKey = created
        return created
    }

    private fun thinkingKey(event: AgentEvent): String {
        val current = activeThinkingKey
        if (current != null) return current
        val created = "think-${event.turnId.value}-${blocks.size}"
        activeThinkingKey = created
        return created
    }

    /** 正文被打断（插入了工具/通知/收尾）后，下一段正文中止复用旧块。 */
    private fun closeTextRun() {
        activeTextKey?.let { key ->
            (blocks[key] as? RenderBlock.AssistantText)?.let { blocks[key] = it.copy(streaming = false) }
        }
        activeThinkingKey?.let { key ->
            (blocks[key] as? RenderBlock.Thinking)?.let { blocks[key] = it.copy(streaming = false) }
        }
        activeTextKey = null
        activeThinkingKey = null
    }

    private fun summarize(call: ToolCall): String =
        call.arguments.entries.joinToString(", ") { (name, element) ->
            val raw = runCatching { element.jsonPrimitive.content }.getOrElse { element.toString() }
            "$name=${raw.truncate(48)}"
        }

    private fun labelOf(attachment: com.deepcode.core.model.Attachment): String = when (attachment) {
        is com.deepcode.core.model.Attachment.File -> attachment.path.substringAfterLast('/')
        is com.deepcode.core.model.Attachment.Image -> "图片"
        is com.deepcode.core.model.Attachment.Text -> attachment.title
    }

    private fun String.truncate(max: Int): String =
        if (length <= max) this else take(max) + "…"
}
