package com.agentide.core.agent

import com.agentide.core.agent.spi.ApprovalDecision
import com.agentide.core.agent.spi.ApprovalPolicyStore
import com.agentide.core.agent.spi.CompletionChunk
import com.agentide.core.agent.spi.CompletionRequest
import com.agentide.core.agent.spi.ContextPolicy
import com.agentide.core.agent.spi.LlmMessage
import com.agentide.core.agent.spi.LlmRole
import com.agentide.core.agent.spi.ModelProvider
import com.agentide.core.agent.spi.PermissionGate
import com.agentide.core.agent.spi.Sandbox
import com.agentide.core.agent.spi.Tool
import com.agentide.core.agent.spi.ToolContext
import com.agentide.core.agent.spi.ToolRegistry
import com.agentide.core.agent.spi.Workspace
import com.agentide.core.agent.spi.estimateTokens
import com.agentide.core.agent.spi.signature
import com.agentide.core.data.EventStore
import com.agentide.core.model.AgentError
import com.agentide.core.model.AgentEvent
import com.agentide.core.model.ApprovalScope
import com.agentide.core.model.Attachment
import com.agentide.core.model.ErrorCode
import com.agentide.core.model.EventId
import com.agentide.core.model.MessageDelta
import com.agentide.core.model.ModelRef
import com.agentide.core.model.SessionId
import com.agentide.core.model.StopReason
import com.agentide.core.model.ThinkingDelta
import com.agentide.core.model.ToolCall
import com.agentide.core.model.ToolCallApproved
import com.agentide.core.model.ToolCallDenied
import com.agentide.core.model.ToolCallFailed
import com.agentide.core.model.ToolCallId
import com.agentide.core.model.ToolCallProposed
import com.agentide.core.model.ToolCallStarted
import com.agentide.core.model.ToolCallSucceeded
import com.agentide.core.model.ToolError
import com.agentide.core.model.ToolOutput
import com.agentide.core.model.ToolOutputDelta
import com.agentide.core.model.ToolResult
import com.agentide.core.model.TurnCancelled
import com.agentide.core.model.TurnCompleted
import com.agentide.core.model.TurnFailed
import com.agentide.core.model.TurnId
import com.agentide.core.model.TurnStarted
import com.agentide.core.model.Usage
import com.agentide.core.model.newEventId
import com.agentide.core.model.newTurnId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/**
 * 默认 Agent 主循环。
 *
 * 循环骨架：
 *   组装上下文 → 调模型（流式）→ 收工具调用 → 逐个过权限门 → 执行 → 结果回灌 → 再来
 *
 * 这个类里**没有任何 Android 代码**，因此可以在 JVM 上直接跑测试。
 * 这也是把 Runtime 放在纯 Kotlin 模块的最大理由：Agent 逻辑是这个 App
 * 里最容易出错、最需要单测的部分，它必须能脱离模拟器验证。
 */
class DefaultAgentRuntime(
    override val sessionId: SessionId,
    private val provider: ModelProvider,
    private val modelRef: ModelRef,
    private val toolRegistry: ToolRegistry,
    private val workspace: Workspace?,
    private val sandbox: Sandbox?,
    private val eventStore: EventStore,
    private val contextPolicy: ContextPolicy,
    private val scope: CoroutineScope,
    private val config: AgentConfig = AgentConfig(),
    private val policyStore: ApprovalPolicyStore? = null,
    private val gate: PermissionGate = InteractivePermissionGate(policyStore, config.autoApproveReadOnly),
    /** 项目记忆（CLAUDE.md 等）注入口。 */
    private val systemPromptProvider: (suspend () -> String?)? = null,
) : AgentRuntime {

    private val _events = MutableSharedFlow<AgentEvent>(
        replay = 0,
        extraBufferCapacity = 256,
    )

    private var turnJob: Job? = null

    override fun events(): Flow<AgentEvent> = _events

    override suspend fun history(): List<AgentEvent> = eventStore.loadEvents(sessionId)

    override fun isRunning(): Boolean = turnJob?.isActive == true

    override suspend fun submit(userInput: String, attachments: List<Attachment>): TurnId {
        turnJob?.cancel()
        val turnId = newTurnId()
        turnJob = scope.launch { runTurn(turnId, userInput, attachments) }
        return turnId
    }

    override suspend fun respondToApproval(
        call: ToolCall,
        approved: Boolean,
        scope: ApprovalScope,
        reason: String?,
    ) {
        val decision = if (approved) {
            if (scope != ApprovalScope.ONCE) policyStore?.remember(call.signature(), scope)
            ApprovalDecision.Approved(scope)
        } else {
            ApprovalDecision.Denied(reason)
        }
        if (gate is InteractivePermissionGate) gate.respond(decision)
    }

    override suspend fun cancel(reason: String?) {
        turnJob?.cancel(CancellationException(reason ?: "user"))
        turnJob = null
    }

    // ─────────────────────────── 主循环 ───────────────────────────

    private suspend fun runTurn(turnId: TurnId, userInput: String, attachments: List<Attachment>) {
        var totalUsage = Usage()
        var iteration = 0
        var seq = 0L

        try {
            emit(turnId) { id, ts -> TurnStarted(id, sessionId, turnId, ts, userInput, attachments) }

            // 上下文从事件日志现算，不另存一份
            val pastEvents = eventStore.loadEvents(sessionId).filter { it.turnId != turnId }
            val messages = ArrayList(TranscriptReconstructor.reconstruct(pastEvents))
            messages.add(LlmMessage(LlmRole.USER, userInput))

            while (iteration < config.maxIterations) {
                iteration++
                coroutineContext.ensureActive()

                // 上下文水位检查：手机上这是生死线，不是优化
                if (contextPolicy.shouldCompact(estimateTokens(messages), config.contextWindowTokens)) {
                    val outcome = contextPolicy.compact(messages, provider, modelRef)
                    messages.clear()
                    messages.addAll(outcome.messages)
                    emit(turnId) { id, ts ->
                        com.agentide.core.model.ContextCompacted(
                            id, sessionId, turnId, ts,
                            outcome.tokensBefore, outcome.tokensAfter, outcome.summary,
                        )
                    }
                }

                val assistantText = StringBuilder()
                val pendingCalls = ArrayList<ToolCall>()

                provider.stream(
                    CompletionRequest(
                        modelRef = modelRef,
                        messages = messages.toList(),
                        system = buildSystemPrompt(),
                        tools = toolRegistry.specs(),
                        maxTokens = config.maxOutputTokens,
                        temperature = config.temperature,
                        enablePromptCaching = config.enablePromptCaching,
                        turnId = turnId,
                    )
                ).collect { chunk ->
                    when (chunk) {
                        is CompletionChunk.Thinking ->
                            emit(turnId) { id, ts -> ThinkingDelta(id, sessionId, turnId, ts, seq++, chunk.text) }

                        is CompletionChunk.Text -> {
                            assistantText.append(chunk.text)
                            emit(turnId) { id, ts -> MessageDelta(id, sessionId, turnId, ts, seq++, chunk.text) }
                        }

                        is CompletionChunk.ToolCalls -> pendingCalls.addAll(chunk.calls)

                        is CompletionChunk.UsageUpdate -> totalUsage = accumulate(totalUsage, chunk.usage)

                        is CompletionChunk.Done -> Unit

                        is CompletionChunk.Error -> throw ProviderException(chunk.message, chunk.retryable)
                    }
                }

                // 没有工具调用 = 这一轮说完了
                if (pendingCalls.isEmpty()) {
                    emit(turnId) { id, ts ->
                        TurnCompleted(id, sessionId, turnId, ts, StopReason.END_TURN, totalUsage, iteration)
                    }
                    return
                }

                messages.add(
                    LlmMessage(
                        role = LlmRole.ASSISTANT,
                        content = assistantText.toString(),
                        toolCalls = pendingCalls.toList(),
                    )
                )

                for (call in pendingCalls) {
                    coroutineContext.ensureActive()
                    executeOneTool(turnId, call, messages)
                }
            }

            emit(turnId) { id, ts ->
                TurnCompleted(id, sessionId, turnId, ts, StopReason.MAX_TURNS, totalUsage, iteration)
            }
        } catch (cancelled: CancellationException) {
            emit(turnId) { id, ts -> TurnCancelled(id, sessionId, turnId, ts, cancelled.message ?: "user") }
        } catch (t: Throwable) {
            emit(turnId) { id, ts ->
                TurnFailed(
                    id, sessionId, turnId, ts,
                    AgentError(
                        code = mapErrorCode(t),
                        message = t.message ?: "未知错误",
                        retryable = (t as? ProviderException)?.retryable ?: false,
                        detail = t.stackTraceToString().take(600),
                    ),
                )
            }
        } finally {
            turnJob = null
        }
    }

    private suspend fun executeOneTool(
        turnId: TurnId,
        call: ToolCall,
        messages: MutableList<LlmMessage>,
    ) {
        val tool: Tool? = toolRegistry[call.name]

        if (tool == null) {
            val error = ToolError("tool_not_found", "未知工具：${call.name}。可用工具：${toolRegistry.specs().joinToString { it.name }}", false)
            emit(turnId) { id, ts -> ToolCallFailed(id, sessionId, turnId, ts, call.id, error) }
            messages.add(LlmMessage(LlmRole.TOOL, "未知工具：${call.name}", call.id.value, call.name))
            return
        }

        if (tool.spec.requiresWorkspace && workspace == null) {
            val error = ToolError("no_workspace", "尚未打开工作区，无法使用 ${call.name}", false)
            emit(turnId) { id, ts -> ToolCallFailed(id, sessionId, turnId, ts, call.id, error) }
            messages.add(LlmMessage(LlmRole.TOOL, "尚未打开工作区", call.id.value, call.name))
            return
        }

        // 1) 提议（此时还没执行，UI 可以展示将要做什么）
        emit(turnId) { id, ts -> ToolCallProposed(id, sessionId, turnId, ts, call, tool.spec) }

        // 2) 过权限门。这里会挂起，直到用户在弹窗上做出选择。
        when (val decision = gate.request(call, tool.spec)) {
            is ApprovalDecision.Denied -> {
                emit(turnId) { id, ts -> ToolCallDenied(id, sessionId, turnId, ts, call, decision.reason) }
                messages.add(
                    LlmMessage(
                        LlmRole.TOOL,
                        "用户拒绝了这次调用${decision.reason?.let { "：$it" } ?: "。请改用其他方式，或先向用户说明你的意图。"}",
                        call.id.value,
                        call.name,
                    )
                )
            }

            is ApprovalDecision.Approved -> {
                emit(turnId) { id, ts -> ToolCallApproved(id, sessionId, turnId, ts, call, decision.scope) }
                emit(turnId) { id, ts -> ToolCallStarted(id, sessionId, turnId, ts, call) }

                val result = runToolWithProgress(turnId, call, tool)
                if (result.isSuccess) {
                    emit(turnId) { id, ts -> ToolCallSucceeded(id, sessionId, turnId, ts, result) }
                } else {
                    emit(turnId) { id, ts -> ToolCallFailed(id, sessionId, turnId, ts, call.id, result.error!!) }
                }
                messages.add(
                    LlmMessage(LlmRole.TOOL, renderForLlm(result), call.id.value, call.name)
                )
            }
        }
    }

    /**
     * 执行工具并把流式输出转成事件。
     * 输出通过 Channel 缓冲，由一个伴随协程转成 ToolOutputDelta 事件——
     * 这样工具的 emitProgress 保持同步调用，不必是 suspend。
     */
    private suspend fun runToolWithProgress(turnId: TurnId, call: ToolCall, tool: Tool): ToolResult {
        val progress = Channel<String>(Channel.UNLIMITED)
        val drainJob = scope.launch {
            progress.consumeAsFlow().collect { chunk ->
                emit(turnId) { id, ts -> ToolOutputDelta(id, sessionId, turnId, ts, call.id, chunk) }
            }
        }

        val context = DefaultToolContext(
            sessionId = sessionId,
            turnId = turnId,
            callId = call.id,
            workspace = workspace,
            sandbox = sandbox,
            progress = progress,
            isActive = { turnJob?.isActive != false },
        )

        val startedAt = System.currentTimeMillis()
        return try {
            tool.execute(context, call).copy(durationMs = System.currentTimeMillis() - startedAt)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            ToolResult(
                callId = call.id,
                output = ToolOutput.Empty,
                error = ToolError("tool_exception", t.message ?: "工具抛出异常", false),
                durationMs = System.currentTimeMillis() - startedAt,
            )
        } finally {
            // 注意顺序：先 close 让 consumeAsFlow 自然结束，再 join 等它排空。
            // 如果这里直接 cancel()，执行很快的工具其流式输出会被吞掉——
            // 表现为"命令输出偶尔少几行"这类极难复现的 bug。
            progress.close()
            runCatching { drainJob.join() }
        }
    }

    private suspend fun buildSystemPrompt(): String = buildString {
        append(config.systemPrompt)
        systemPromptProvider?.invoke()?.takeIf { it.isNotBlank() }?.let {
            appendLine()
            appendLine()
            append(it)
        }
    }

    private suspend fun emit(turnId: TurnId, make: (id: EventId, ts: Long) -> AgentEvent) {
        val event = make(newEventId(), System.currentTimeMillis())
        eventStore.append(event)
        _events.emit(event)
    }

    private fun accumulate(a: Usage, b: Usage) = Usage(
        inputTokens = a.inputTokens + b.inputTokens,
        outputTokens = a.outputTokens + b.outputTokens,
        cacheReadTokens = a.cacheReadTokens + b.cacheReadTokens,
        cacheCreationTokens = a.cacheCreationTokens + b.cacheCreationTokens,
    )

    private fun mapErrorCode(t: Throwable): ErrorCode = when (t) {
        is ProviderException -> if (t.retryable) ErrorCode.RATE_LIMIT else ErrorCode.PROVIDER
        is java.net.UnknownHostException, is java.net.SocketTimeoutException -> ErrorCode.NETWORK
        is SecurityException -> ErrorCode.PERMISSION_DENIED
        else -> ErrorCode.INTERNAL
    }
}

class ProviderException(message: String, val retryable: Boolean) : RuntimeException(message)

private class DefaultToolContext(
    override val sessionId: SessionId,
    override val turnId: TurnId,
    override val callId: ToolCallId,
    override val workspace: Workspace?,
    override val sandbox: Sandbox?,
    private val progress: Channel<String>,
    private val isActive: () -> Boolean,
) : ToolContext {
    override fun emitProgress(chunk: String) {
        progress.trySend(chunk)
    }

    override fun isCancelled(): Boolean = !isActive()
}
