package com.agentide.core.agent

import com.agentide.core.agent.spi.CompletionChunk
import com.agentide.core.agent.spi.CompletionRequest
import com.agentide.core.agent.spi.ContextPolicy
import com.agentide.core.agent.spi.DefaultToolRegistry
import com.agentide.core.agent.spi.LlmMessage
import com.agentide.core.agent.spi.LlmRole
import com.agentide.core.agent.spi.ModelProvider
import com.agentide.core.agent.spi.Tool
import com.agentide.core.agent.spi.ToolContext
import com.agentide.core.agent.spi.arg
import com.agentide.core.data.InMemoryEventStore
import com.agentide.core.model.AgentEvent
import com.agentide.core.model.ModelRef
import com.agentide.core.model.RiskLevel
import com.agentide.core.model.SessionId
import com.agentide.core.model.ToolCall
import com.agentide.core.model.ToolCallDenied
import com.agentide.core.model.ToolCallId
import com.agentide.core.model.ToolCallSucceeded
import com.agentide.core.model.ToolKind
import com.agentide.core.model.ToolOutput
import com.agentide.core.model.ToolResult
import com.agentide.core.model.ToolSpec
import com.agentide.core.model.TurnCompleted
import com.agentide.core.model.TurnFailed
import com.agentide.core.model.TurnStarted
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DefaultAgentRuntimeTest {

    // ─────────────────────────── 测试替身 ───────────────────────────

    /** 按脚本返回 chunk 的假模型，并记录每次收到的请求。 */
    private class ScriptedProvider(
        private val script: List<List<CompletionChunk>>,
    ) : ModelProvider {
        override val id = "fake"
        override val displayName = "Fake Provider"
        val requests = mutableListOf<CompletionRequest>()

        override fun stream(request: CompletionRequest): Flow<CompletionChunk> = flow {
            requests.add(request)
            val index = requests.size - 1
            script.getOrElse(index) { emptyList() }.forEach { emit(it) }
        }
    }

    private class EchoTool : Tool {
        override val spec = ToolSpec(
            name = "echo",
            description = "回显一段文本",
            kind = ToolKind.OTHER,
            riskLevel = RiskLevel.WRITE,
            requiresWorkspace = false,
            streamsOutput = true,
        )

        var executed = false

        override suspend fun execute(context: ToolContext, call: ToolCall): ToolResult {
            executed = true
            val text = call.arg("text").orEmpty()
            context.emitProgress("echo> $text")
            return ToolResult(call.id, ToolOutput.Text(text))
        }
    }

    private object NoCompaction : ContextPolicy {
        override fun shouldCompact(estimatedTokens: Int, contextWindowTokens: Int) = false
        override suspend fun compact(
            messages: List<LlmMessage>,
            provider: ModelProvider,
            modelRef: ModelRef,
        ) = com.agentide.core.agent.spi.CompactionOutcome(messages, "", 0, 0)
    }

    private fun echoCall(id: String = "c1", text: String = "hi") = ToolCall(
        id = ToolCallId(id),
        name = "echo",
        arguments = JsonObject(mapOf("text" to JsonPrimitive(text))),
    )

    // ─────────────────────────── 用例 ───────────────────────────

    @Test
    fun `完整链路_文本_工具调用_权限通过_结果回灌_直到结束`() = runTest {
        val provider = ScriptedProvider(
            listOf(
                listOf(
                    CompletionChunk.Text("我先查一下。"),
                    CompletionChunk.ToolCalls(listOf(echoCall())),
                ),
                listOf(CompletionChunk.Text("查完了，结果是 hi。")),
            )
        )
        val tool = EchoTool()
        val store = InMemoryEventStore()
        val runtime = buildRuntime(provider, tool, store, autoApprove = false)

        val seen = mutableListOf<AgentEvent>()
        val collectJob = launch {
            runtime.events().collect { event ->
                seen.add(event)
                if (event is com.agentide.core.model.ToolCallProposed) {
                    runtime.respondToApproval(event.call, approved = true)
                }
            }
        }

        runtime.submit("帮我 echo 一下")

        awaitTerminal(seen)
        collectJob.cancel()

        // 1) 正常结束，没走错误分支
        val terminal = seen.filterIsInstance<TurnCompleted>().singleOrNull()
        assertNotNull(terminal, "应当以 TurnCompleted 结束")
        assertEquals(com.agentide.core.model.StopReason.END_TURN, terminal.stopReason)

        // 2) 权限通过后工具确实执行了，并产出成功事件
        assertTrue(tool.executed, "工具应当被执行")
        assertTrue(seen.any { it is ToolCallSucceeded }, "应当有 ToolCallSucceeded")

        // 3) 流式输出增量被转成事件
        assertTrue(
            seen.filterIsInstance<com.agentide.core.model.ToolOutputDelta>().isNotEmpty(),
            "应当有 ToolOutputDelta",
        )

        // 4) 工具结果回灌进了模型的第二次请求
        assertEquals(2, provider.requests.size, "模型应被调用两次")
        val secondRequest = provider.requests[1]
        val toolMessage = secondRequest.messages.last { it.role == LlmRole.TOOL }
        assertContains(toolMessage.content, "hi")

        // 5) 事件全部落盘——这是进程被杀后能恢复的前提
        val persisted = store.loadEvents(SessionId("s1"))
        assertTrue(persisted.isNotEmpty(), "事件应当落盘")
        assertTrue(persisted.any { it is TurnStarted }, "落盘内容应包含 TurnStarted")
        assertTrue(persisted.any { it is ToolCallSucceeded }, "落盘内容应包含 ToolCallSucceeded")
    }

    @Test
    fun `用户拒绝时_工具不执行_且模型收到拒绝说明`() = runTest {
        val provider = ScriptedProvider(
            listOf(
                listOf(CompletionChunk.ToolCalls(listOf(echoCall()))),
                listOf(CompletionChunk.Text("好的，那我不调用了。")),
            )
        )
        val tool = EchoTool()
        val store = InMemoryEventStore()
        val runtime = buildRuntime(provider, tool, store, autoApprove = false)

        val seen = mutableListOf<AgentEvent>()
        val collectJob = launch {
            runtime.events().collect { event ->
                seen.add(event)
                if (event is com.agentide.core.model.ToolCallProposed) {
                    runtime.respondToApproval(event.call, approved = false, reason = "我不想让你改文件")
                }
            }
        }

        runtime.submit("改点东西")
        awaitTerminal(seen)
        collectJob.cancel()

        // 工具绝不能被执行
        assertTrue(!tool.executed, "被拒绝的工具不应执行")
        assertTrue(seen.any { it is ToolCallDenied }, "应当有 ToolCallDenied")
        assertTrue(seen.none { it is ToolCallSucceeded }, "不应有 ToolCallSucceeded")

        // 模型应当知道被拒绝了，而不是一脸懵
        val secondRequest = provider.requests[1]
        val toolMessage = secondRequest.messages.last { it.role == LlmRole.TOOL }
        assertContains(toolMessage.content, "拒绝")
    }

    @Test
    fun `只读工具在默认策略下自动放行_不打断用户`() = runTest {
        val provider = ScriptedProvider(
            listOf(
                listOf(CompletionChunk.ToolCalls(listOf(echoCall()))),
                listOf(CompletionChunk.Text("完成")),
            )
        )
        val tool = object : Tool {
            override val spec = ToolSpec("echo", "回显", ToolKind.OTHER, RiskLevel.READ_ONLY, requiresWorkspace = false)
            override suspend fun execute(context: ToolContext, call: ToolCall) =
                ToolResult(call.id, ToolOutput.Text(call.arg("text").orEmpty()))
        }
        val store = InMemoryEventStore()
        val runtime = buildRuntime(provider, tool, store, autoApprove = true)

        val seen = mutableListOf<AgentEvent>()
        val collectJob = launch { runtime.events().collect { seen.add(it) } }

        runtime.submit("读点东西")
        awaitTerminal(seen)
        collectJob.cancel()

        // 没有任何人去响应权限，任务依然跑完了 —— 说明只读确实自动放行了
        assertTrue(seen.any { it is ToolCallSucceeded })
        assertTrue(seen.none { it is ToolCallDenied })
    }

    @Test
    fun `模型报错时_产出 TurnFailed 而不是崩溃`() = runTest {
        val provider = ScriptedProvider(
            listOf(listOf(CompletionChunk.Error("上游 500", retryable = true)))
        )
        val tool = EchoTool()
        val store = InMemoryEventStore()
        val runtime = buildRuntime(provider, tool, store)

        val seen = mutableListOf<AgentEvent>()
        val collectJob = launch { runtime.events().collect { seen.add(it) } }

        runtime.submit("随便")
        awaitTerminal(seen)
        collectJob.cancel()

        val failure = seen.filterIsInstance<TurnFailed>().singleOrNull()
        assertNotNull(failure, "应当产出 TurnFailed")
        assertEquals(com.agentide.core.model.ErrorCode.RATE_LIMIT, failure.error.code)
        assertTrue(failure.error.retryable)
    }

    // ─────────────────────────── 辅助 ───────────────────────────

    private fun TestScope.buildRuntime(
        provider: ModelProvider,
        tool: Tool,
        store: InMemoryEventStore,
        autoApprove: Boolean = true,
    ): DefaultAgentRuntime {
        val registry = DefaultToolRegistry().apply { register(tool) }
        return DefaultAgentRuntime(
            sessionId = SessionId("s1"),
            provider = provider,
            modelRef = ModelRef("fake", "fake-1"),
            toolRegistry = registry,
            workspace = null,
            sandbox = null,
            eventStore = store,
            contextPolicy = NoCompaction,
            scope = this,
            config = AgentConfig(autoApproveReadOnly = autoApprove, maxIterations = 4),
        )
    }

    private suspend fun awaitTerminal(seen: List<AgentEvent>) {
        withTimeoutOrNull(5_000) {
            while (seen.none {
                    it is TurnCompleted || it is TurnFailed ||
                        it is com.agentide.core.model.TurnCancelled
                }
            ) delay(5)
        }
    }
}
