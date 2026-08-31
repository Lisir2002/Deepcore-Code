package com.deepcode.core.agent

import com.deepcode.core.agent.spi.LlmMessage
import com.deepcode.core.agent.spi.LlmRole
import com.deepcode.core.model.AgentEvent
import com.deepcode.core.model.MessageDelta
import com.deepcode.core.model.SubAgentEvent
import com.deepcode.core.model.ThinkingDelta
import com.deepcode.core.model.ToolCall
import com.deepcode.core.model.ToolCallDenied
import com.deepcode.core.model.ToolCallFailed
import com.deepcode.core.model.ToolCallId
import com.deepcode.core.model.ToolCallProposed
import com.deepcode.core.model.ToolCallStarted
import com.deepcode.core.model.ToolCallSucceeded
import com.deepcode.core.model.ToolOutput
import com.deepcode.core.model.ToolResult
import com.deepcode.core.model.TurnStarted

/**
 * 从事件日志重建 LLM 消息列表。
 *
 * 这是"事件即真相"的兑现点：上下文不另存一份，每次都从事件流现算。
 * 好处是上下文永远不会和存储里的会话内容不一致——它们本来就是同一份东西。
 */
object TranscriptReconstructor {

    fun reconstruct(events: List<AgentEvent>): List<LlmMessage> {
        val out = ArrayList<LlmMessage>()
        val assistantText = StringBuilder()
        val pendingCalls = ArrayList<ToolCall>()
        val callNames = HashMap<ToolCallId, String>()

        fun flushAssistant() {
            if (assistantText.isNotBlank() || pendingCalls.isNotEmpty()) {
                out.add(
                    LlmMessage(
                        role = LlmRole.ASSISTANT,
                        content = assistantText.toString(),
                        toolCalls = pendingCalls.toList(),
                    )
                )
            }
            assistantText.clear()
            pendingCalls.clear()
        }

        fun toolMessage(callId: ToolCallId, content: String) {
            flushAssistant()
            out.add(
                LlmMessage(
                    role = LlmRole.TOOL,
                    content = content,
                    toolCallId = callId.value,
                    toolName = callNames[callId],
                )
            )
        }

        for (event in events) {
            when (event) {
                is TurnStarted -> {
                    flushAssistant()
                    val text = buildString {
                        append(event.userInput)
                        if (event.attachments.isNotEmpty()) {
                            appendLine()
                            event.attachments.forEach { appendLine("[附件] $it") }
                        }
                    }
                    out.add(LlmMessage(LlmRole.USER, text))
                }

                // 思考过程不回灌给模型：省 token，也避免模型模仿自己的碎碎念
                is ThinkingDelta -> Unit

                is MessageDelta -> assistantText.append(event.text)

                is ToolCallProposed -> {
                    callNames[event.call.id] = event.call.name
                    pendingCalls.add(event.call)
                }

                is ToolCallStarted -> Unit

                is ToolCallSucceeded -> toolMessage(event.result.callId, renderForLlm(event.result))

                is ToolCallFailed -> toolMessage(
                    event.callId,
                    "工具执行失败：${event.error.code} ${event.error.message}",
                )

                is ToolCallDenied -> toolMessage(
                    event.call.id,
                    "用户拒绝了这次工具调用${event.reason?.let { "：$it" } ?: "。请换一种方式，或向用户说明你打算做什么。"}",
                )

                is SubAgentEvent -> Unit // M0 不回灌子 Agent 过程，只保留其结果

                else -> Unit
            }
        }
        flushAssistant()
        return out
    }
}

/** 把工具产物压成喂给模型的文本。UI 那边另有渲染器，各走各的路。 */
internal fun renderForLlm(result: ToolResult): String {
    val body = when (val output = result.output) {
        is ToolOutput.Text ->
            output.text + if (output.truncated) "\n（输出已截断）" else ""

        is ToolOutput.Diff ->
            "已修改 ${output.path}（+${output.addedLines}/-${output.removedLines}）\n${output.unified}"

        is ToolOutput.FileList ->
            if (output.entries.isEmpty()) "（空目录）"
            else output.entries.joinToString("\n") {
                "${if (it.isDirectory) "dir " else "file"} ${it.path}"
            }

        is ToolOutput.SearchHits ->
            if (output.hits.isEmpty()) "没有匹配结果：${output.query}"
            else output.hits.joinToString("\n") { "${it.path}:${it.line}: ${it.snippet}" }

        is ToolOutput.KeyValues ->
            output.pairs.joinToString("\n") { "${it.first}: ${it.second}" }

        is ToolOutput.Image ->
            "[图片结果：${output.mimeType}，base64 ${output.base64.length} 字符]"

        is ToolOutput.ResourceLink ->
            "[资源链接：${output.name ?: "未命名"} — ${output.uri}]"

        is ToolOutput.Structured ->
            "[结构化结果]\n${output.json}"

        ToolOutput.Empty -> "（无输出）"
    }
    return result.error?.let { "$body\n\n错误：${it.code} ${it.message}" } ?: body
}
