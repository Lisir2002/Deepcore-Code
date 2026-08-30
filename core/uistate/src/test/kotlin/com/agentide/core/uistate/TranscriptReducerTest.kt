package com.agentide.core.uistate

import com.agentide.core.model.AgentEvent
import com.agentide.core.model.EventId
import com.agentide.core.model.MessageDelta
import com.agentide.core.model.RiskLevel
import com.agentide.core.model.SessionId
import com.agentide.core.model.ThinkingDelta
import com.agentide.core.model.ToolCall
import com.agentide.core.model.ToolCallApproved
import com.agentide.core.model.ToolCallDenied
import com.agentide.core.model.ToolCallId
import com.agentide.core.model.ToolCallProposed
import com.agentide.core.model.ToolCallSucceeded
import com.agentide.core.model.ToolKind
import com.agentide.core.model.ToolOutput
import com.agentide.core.model.ToolResult
import com.agentide.core.model.ToolSpec
import com.agentide.core.model.TurnCompleted
import com.agentide.core.model.TurnId
import com.agentide.core.model.TurnStarted
import com.agentide.core.model.Usage
import com.agentide.core.model.newEventId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TranscriptReducerTest {

    private val sessionId = SessionId("s1")
    private val turnId = TurnId("t1")
    private val callId = ToolCallId("c1")

    private val echoSpec = ToolSpec(
        name = "echo",
        description = "回显",
        kind = ToolKind.OTHER,
        riskLevel = RiskLevel.WRITE,
    )

    private val echoCall = ToolCall(callId, "echo")

    @Test
    fun `连续的文本增量合并成同一个渲染块`() {
        val reducer = TranscriptReducer()
        reducer.apply(turnStarted("你好"))
        reducer.apply(messageDelta("我"))
        reducer.apply(messageDelta("很好"))
        reducer.apply(messageDelta("，谢谢"))

        val blocks = reducer.snapshot()
        val text = blocks.filterIsInstance<RenderBlock.AssistantText>()
        assertEquals(1, text.size, "多个 delta 应当合并成一个块")
        assertEquals("我很好，谢谢", text.single().text)
    }

    @Test
    fun `工具调用会截断当前文本_后续文本另起一块`() {
        val reducer = TranscriptReducer()
        reducer.apply(turnStarted("帮我改文件"))
        reducer.apply(messageDelta("先看一下"))
        reducer.apply(toolProposed())
        reducer.apply(messageDelta("改完了"))

        val blocks = reducer.snapshot()
        val texts = blocks.filterIsInstance<RenderBlock.AssistantText>()
        assertEquals(2, texts.size, "工具前后的文本应当是两个块")
        assertEquals("先看一下", texts[0].text)
        assertEquals("改完了", texts[1].text)

        // 顺序必须是：用户消息 → 文本 → 工具 → 文本
        assertIs<RenderBlock.UserMessage>(blocks[0])
        assertIs<RenderBlock.AssistantText>(blocks[1])
        assertIs<RenderBlock.ToolInvocation>(blocks[2])
        assertIs<RenderBlock.AssistantText>(blocks[3])
    }

    @Test
    fun `工具状态随事件正确流转`() {
        val reducer = TranscriptReducer()
        reducer.apply(turnStarted("跑个工具"))
        reducer.apply(toolProposed())

        val tool = { reducer.snapshot().filterIsInstance<RenderBlock.ToolInvocation>().single() }

        // 写入类工具必须先等用户确认
        assertEquals(ToolVisualStatus.AWAITING_APPROVAL, tool().status)

        reducer.apply(
            ToolCallApproved(newEventId(), sessionId, turnId, 0L, echoCall, com.agentide.core.model.ApprovalScope.ONCE)
        )
        assertEquals(ToolVisualStatus.RUNNING, tool().status)

        reducer.apply(
            ToolCallSucceeded(
                newEventId(), sessionId, turnId, 0L,
                ToolResult(callId, ToolOutput.Text("done"), durationMs = 42),
            )
        )
        assertEquals(ToolVisualStatus.SUCCEEDED, tool().status)
        assertEquals(42L, tool().durationMs)
        assertEquals(ToolOutput.Text("done"), tool().output)
    }

    @Test
    fun `只读工具不需要等待确认_直接进入执行态`() {
        val reducer = TranscriptReducer()
        reducer.apply(turnStarted("读文件"))
        reducer.apply(
            ToolCallProposed(
                newEventId(), sessionId, turnId, 0L, echoCall,
                echoSpec.copy(name = "read_file", riskLevel = RiskLevel.READ_ONLY),
            )
        )

        val tool = reducer.snapshot().filterIsInstance<RenderBlock.ToolInvocation>().single()
        assertEquals(ToolVisualStatus.RUNNING, tool.status, "只读工具不应打断用户")
    }

    @Test
    fun `被拒绝的工具状态为 DENIED`() {
        val reducer = TranscriptReducer()
        reducer.apply(turnStarted("改东西"))
        reducer.apply(toolProposed())
        reducer.apply(ToolCallDenied(newEventId(), sessionId, turnId, 0L, echoCall, "用户拒绝"))

        val tool = reducer.snapshot().filterIsInstance<RenderBlock.ToolInvocation>().single()
        assertEquals(ToolVisualStatus.DENIED, tool.status)
    }

    @Test
    fun `回放历史事件与增量处理结果完全一致`() {
        val events: List<AgentEvent> = listOf(
            turnStarted("开始"),
            thinking("思考中"),
            messageDelta("结果"),
            messageDelta("如下"),
            toolProposed(),
            ToolCallSucceeded(
                newEventId(), sessionId, turnId, 0L,
                ToolResult(callId, ToolOutput.Text("ok")),
            ),
            TurnCompleted(
                newEventId(), sessionId, turnId, 100L,
                com.agentide.core.model.StopReason.END_TURN, Usage(10, 5), 1,
            ),
        )

        val incremental = TranscriptReducer().also { r -> events.forEach { r.apply(it) } }
        val replayed = TranscriptReducer().also { it.reset(events) }

        assertEquals(incremental.snapshot(), replayed.snapshot(),
            "进程被杀后重放事件，界面必须长得一模一样")
        assertTrue(replayed.snapshot().any { it is RenderBlock.TurnFooter })
    }

    // ─────────────────────────── 事件构造辅助 ───────────────────────────

    private fun turnStarted(text: String) = TurnStarted(newEventId(), sessionId, turnId, 0L, text)

    private fun thinking(text: String) = ThinkingDelta(newEventId(), sessionId, turnId, 0L, 0L, text)

    private fun messageDelta(text: String) = MessageDelta(newEventId(), sessionId, turnId, 0L, 0L, text)

    private fun toolProposed(): ToolCallProposed =
        ToolCallProposed(newEventId(), sessionId, turnId, 0L, echoCall, echoSpec)

    @Suppress("unused")
    private fun unusedEventId(): EventId = newEventId()
}
