package com.deepcode.core.agent

import com.deepcode.core.agent.spi.ApprovalDecision
import com.deepcode.core.agent.spi.ApprovalPolicyStore
import com.deepcode.core.agent.spi.CompletionChunk
import com.deepcode.core.agent.spi.CompletionRequest
import com.deepcode.core.agent.spi.ContextPolicy
import com.deepcode.core.agent.spi.LlmMessage
import com.deepcode.core.agent.spi.LlmRole
import com.deepcode.core.agent.spi.ModelProvider
import com.deepcode.core.agent.spi.PermissionGate
import com.deepcode.core.agent.spi.Sandbox
import com.deepcode.core.agent.spi.Tool
import com.deepcode.core.agent.spi.ToolContext
import com.deepcode.core.agent.spi.ToolRegistry
import com.deepcode.core.agent.spi.Workspace
import com.deepcode.core.agent.spi.estimateTokens
import com.deepcode.core.agent.spi.signature
import com.deepcode.core.data.EventStore
import com.deepcode.core.logging.Log
import com.deepcode.core.logging.LogCategory
import com.deepcode.core.logging.LogLevel
import com.deepcode.core.model.AgentError
import com.deepcode.core.model.AgentEvent
import com.deepcode.core.model.ApprovalScope
import com.deepcode.core.model.Attachment
import com.deepcode.core.model.ErrorCode
import com.deepcode.core.model.EventId
import com.deepcode.core.model.MessageDelta
import com.deepcode.core.model.ModelRef
import com.deepcode.core.model.SessionId
import com.deepcode.core.model.StopReason
import com.deepcode.core.model.ThinkingDelta
import com.deepcode.core.model.ToolCall
import com.deepcode.core.model.ToolCallApproved
import com.deepcode.core.model.ToolCallDenied
import com.deepcode.core.model.ToolCallFailed
import com.deepcode.core.model.ToolCallId
import com.deepcode.core.model.ToolCallProposed
import com.deepcode.core.model.ToolCallStarted
import com.deepcode.core.model.ToolCallSucceeded
import com.deepcode.core.model.ToolError
import com.deepcode.core.model.ToolOutput
import com.deepcode.core.model.ToolOutputDelta
import com.deepcode.core.model.ToolResult
import com.deepcode.core.model.TurnCancelled
import com.deepcode.core.model.TurnCompleted
import com.deepcode.core.model.TurnFailed
import com.deepcode.core.model.TurnId
import com.deepcode.core.model.TurnStarted
import com.deepcode.core.model.Usage
import com.deepcode.core.model.newEventId
import com.deepcode.core.model.newTurnId
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
    /** 技能 L1 段注入口（见 docs/TOOLS_SKILLS.md §5/§6）。由上层把扫描结果渲染成文本后传入，
     *  本类只负责把它追加进 system prompt——加载与解析不在主循环里。 */
    private val skillSectionProvider: (suspend () -> String?)? = null,
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
        val decision = if (approved) ApprovalDecision.Approved(scope) else ApprovalDecision.Denied(reason)
        // 只有当真·唤醒了一个等待中的审批（respond 返回 true）才持久化策略。
        // 否则——用户点的是早已失效/被取消的弹窗，或 turn 已终止——不能拿一个
        // 从未真正执行的调用把 ALWAYS/SESSION 写进策略库（一旦错写，会长期自动放行）。
        if (gate is InteractivePermissionGate && gate.respond(decision)) {
            if (approved && scope != ApprovalScope.ONCE) {
                policyStore?.remember(call.signature(), scope)
            }
        }
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
            Log.log(LogLevel.INFO, LogCategory.OPERATION_AGENT, "AgentRuntime", "turn ${turnId.value} 开始，输入 ${userInput.take(80)}")

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
                    Log.log(
                        LogLevel.INFO, LogCategory.OPERATION_AGENT, "AgentRuntime",
                        "turn ${turnId.value} 上下文压缩 ${outcome.tokensBefore} → ${outcome.tokensAfter} tokens",
                    )
                    emit(turnId) { id, ts ->
                        com.deepcode.core.model.ContextCompacted(
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
                    Log.log(
                        LogLevel.INFO, LogCategory.OPERATION_AGENT, "AgentRuntime",
                        "turn ${turnId.value} 完成（END_TURN），迭代 $iteration，用量 in=${totalUsage.inputTokens} out=${totalUsage.outputTokens}",
                    )
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

            Log.log(
                LogLevel.WARN, LogCategory.OPERATION_AGENT, "AgentRuntime",
                "turn ${turnId.value} 达到迭代上限 ${config.maxIterations}（MAX_TURNS）",
            )
            emit(turnId) { id, ts ->
                TurnCompleted(id, sessionId, turnId, ts, StopReason.MAX_TURNS, totalUsage, iteration)
            }
        } catch (cancelled: CancellationException) {
            Log.log(LogLevel.INFO, LogCategory.OPERATION_AGENT, "AgentRuntime", "turn ${turnId.value} 被取消：${cancelled.message ?: "user"}")
            emit(turnId) { id, ts -> TurnCancelled(id, sessionId, turnId, ts, cancelled.message ?: "user") }
        } catch (t: Throwable) {
            Log.log(
                LogLevel.ERROR, LogCategory.ERROR_EXCEPTION, "AgentRuntime",
                "turn ${turnId.value} 失败：${t.message ?: t::class.simpleName}", t,
            )
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
            Log.log(LogLevel.ERROR, LogCategory.ERROR_FAILURE, "AgentRuntime", "turn ${turnId.value} 未知工具 ${call.name}")
            emit(turnId) { id, ts -> ToolCallFailed(id, sessionId, turnId, ts, call.id, error) }
            messages.add(LlmMessage(LlmRole.TOOL, "未知工具：${call.name}", call.id.value, call.name))
            return
        }

        if (tool.spec.requiresWorkspace && workspace == null) {
            val error = ToolError("no_workspace", "尚未打开工作区，无法使用 ${call.name}", false)
            Log.log(LogLevel.ERROR, LogCategory.ERROR_FAILURE, "AgentRuntime", "turn ${turnId.value} 工具 ${call.name} 需要工作区但未打开")
            emit(turnId) { id, ts -> ToolCallFailed(id, sessionId, turnId, ts, call.id, error) }
            messages.add(LlmMessage(LlmRole.TOOL, "尚未打开工作区", call.id.value, call.name))
            return
        }

        // 1) 提议（此时还没执行，UI 可以展示将要做什么）
        emit(turnId) { id, ts -> ToolCallProposed(id, sessionId, turnId, ts, call, tool.spec) }

        // 2) 过权限门。这里会挂起，直到用户在弹窗上做出选择。
        when (val decision = gate.request(call, tool.spec)) {
            is ApprovalDecision.Denied -> {
                Log.log(
                    LogLevel.INFO, LogCategory.SECURITY_PERMISSION, "AgentRuntime",
                    "turn ${turnId.value} 工具 ${call.name} 被用户拒绝${decision.reason?.let { "：$it" } ?: ""}",
                )
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
                Log.log(
                    LogLevel.INFO, LogCategory.OPERATION_AGENT, "AgentRuntime",
                    "turn ${turnId.value} 工具 ${call.name} 已批准（${decision.scope}），开始执行",
                )
                emit(turnId) { id, ts -> ToolCallApproved(id, sessionId, turnId, ts, call, decision.scope) }
                emit(turnId) { id, ts -> ToolCallStarted(id, sessionId, turnId, ts, call) }

                val result = runToolWithProgress(turnId, call, tool)
                if (result.isSuccess) {
                    Log.log(
                        LogLevel.INFO, LogCategory.OPERATION_AGENT, "AgentRuntime",
                        "turn ${turnId.value} 工具 ${call.name} 成功（${result.durationMs}ms）",
                    )
                    emit(turnId) { id, ts -> ToolCallSucceeded(id, sessionId, turnId, ts, result) }
                } else {
                    Log.log(
                        LogLevel.ERROR, LogCategory.ERROR_FAILURE, "AgentRuntime",
                        "turn ${turnId.value} 工具 ${call.name} 失败：${result.error?.message ?: "unknown"}",
                    )
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
        // 技能 L1 段（已装 skill 的 name + description）。模型据此决定何时加载 SKILL.md（L2）。
        skillSectionProvider?.invoke()?.takeIf { it.isNotBlank() }?.let {
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
