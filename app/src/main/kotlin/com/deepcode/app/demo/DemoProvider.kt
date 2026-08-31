package com.deepcode.app.demo

import com.deepcode.core.agent.spi.CompletionChunk
import com.deepcode.core.agent.spi.CompletionRequest
import com.deepcode.core.agent.spi.LlmRole
import com.deepcode.core.agent.spi.ModelProvider
import com.deepcode.core.agent.spi.StopReasonRaw
import com.deepcode.core.model.ToolCall
import com.deepcode.core.model.ToolSpec
import com.deepcode.core.model.Usage
import com.deepcode.core.model.newToolCallId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 演示用模型（M0 脚手架）。
 *
 * 它不联网，只按固定剧本产生"思考 → 文本 → 工具调用"的流式输出，
 * 目的是在没有接真实模型之前，就能验证整条链路：
 *   流式渲染 / 工具调用 / 权限确认 / 结果回灌 / 上下文拼接 / 事件落盘
 *
 * 接真实模型时，实现同一个 ModelProvider 接口替换掉即可。
 */
class DemoProvider : ModelProvider {

    override val id: String = "demo"
    override val displayName: String = "演示模型"

    private var round = 0

    override fun stream(request: CompletionRequest): Flow<CompletionChunk> = flow {
        round++
        val lastUserInput = request.messages.lastOrNull { it.role == LlmRole.USER }?.content.orEmpty()

        if (round % 2 == 1 && request.tools.isNotEmpty()) {
            emit(CompletionChunk.Thinking("用户说的是：$lastUserInput\n我先调用工具确认现状，再决定下一步。"))
            delay(250)

            "好的，我先看一下工作区的情况。".forEach { char ->
                emit(CompletionChunk.Text(char.toString()))
                delay(22)
            }

            val target = request.tools.first()
            emit(
                CompletionChunk.ToolCalls(
                    listOf(
                        ToolCall(
                            id = newToolCallId(),
                            name = target.name,
                            arguments = demoArguments(target),
                        )
                    )
                )
            )
            emit(CompletionChunk.UsageUpdate(Usage(inputTokens = 150, outputTokens = 60)))
        } else {
            "工具执行完了。这是演示模型的收尾回复——接入真实模型后，这里就是模型的真实输出。"
                .forEach { char ->
                    emit(CompletionChunk.Text(char.toString()))
                    delay(15)
                }
            emit(CompletionChunk.UsageUpdate(Usage(inputTokens = 90, outputTokens = 40)))
        }

        emit(CompletionChunk.Done(StopReasonRaw.END_TURN))
    }

    private fun demoArguments(spec: ToolSpec): JsonObject {
        val args = when (spec.name) {
            "list_files" -> mapOf("path" to ".", "recursive" to "false")
            "read_file" -> mapOf("path" to "README.md")
            "write_file" -> mapOf("path" to "notes.txt", "content" to "由演示 Agent 写入")
            "run_command" -> mapOf("command" to "ls", "args" to "-la")
            else -> emptyMap()
        }
        return JsonObject(args.mapValues { JsonPrimitive(it.value) })
    }
}
