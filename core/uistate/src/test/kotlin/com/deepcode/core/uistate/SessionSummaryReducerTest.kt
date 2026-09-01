package com.deepcode.core.uistate

import com.deepcode.core.model.AgentEvent
import com.deepcode.core.model.EventId
import com.deepcode.core.model.MessageDelta
import com.deepcode.core.model.RiskLevel
import com.deepcode.core.model.SessionId
import com.deepcode.core.model.SessionStatus
import com.deepcode.core.model.ToolCall
import com.deepcode.core.model.ToolCallProposed
import com.deepcode.core.model.ToolCallSucceeded
import com.deepcode.core.model.ToolCallId
import com.deepcode.core.model.ToolKind
import com.deepcode.core.model.ToolOutput
import com.deepcode.core.model.ToolResult
import com.deepcode.core.model.ToolSpec
import com.deepcode.core.model.TurnCompleted
import com.deepcode.core.model.TurnFailed
import com.deepcode.core.model.TurnId
import com.deepcode.core.model.TurnStarted
import com.deepcode.core.model.Usage
import com.deepcode.core.model.newEventId
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionSummaryReducerTest {

    private val sessionId = SessionId("s1")
    private val turnId = TurnId("t1")

    @Test
    fun `空事件流给出空摘要与空闲状态`() {
        val summary = SessionSummaryReducer.reduce(emptyList())
        assertEquals("", summary.title)
        assertEquals("", summary.preview)
        assertEquals(SessionStatus.IDLE, summary.status)
    }

    @Test
    fun `标题取首个用户输入首行_预览取助手正文`() {
        val summary = SessionSummaryReducer.reduce(
            listOf(
                turnStarted("修复登录闪退\n第二条"),
                messageDelta("正在分析"),
                messageDelta("崩溃日志…"),
            )
        )
        assertEquals("修复登录闪退", summary.title)
        assertEquals("正在分析崩溃日志…", summary.preview)
        assertEquals(SessionStatus.RUNNING, summary.status)
    }

    @Test
    fun `进行中的 turn 判定为运行中`() {
        val summary = SessionSummaryReducer.reduce(listOf(turnStarted("跑起来")))
        assertEquals(SessionStatus.RUNNING, summary.status)
    }

    @Test
    fun `停在权限确认上判定为待授权`() {
        val summary = SessionSummaryReducer.reduce(listOf(turnStarted("改文件"), toolProposed()))
        assertEquals(SessionStatus.AWAITING_APPROVAL, summary.status)
    }

    @Test
    fun `权限裁决后回到运行中`() {
        val summary = SessionSummaryReducer.reduce(
            listOf(turnStarted("改文件"), toolProposed(), toolSucceeded())
        )
        assertEquals(SessionStatus.RUNNING, summary.status)
    }

    @Test
    fun `turn 完成回到空闲`() {
        val summary = SessionSummaryReducer.reduce(
            listOf(turnStarted("你好"), messageDelta("在的"), turnCompleted())
        )
        assertEquals(SessionStatus.IDLE, summary.status)
    }

    @Test
    fun `turn 失败判定为失败`() {
        val summary = SessionSummaryReducer.reduce(listOf(turnStarted("跑"), turnFailed()))
        assertEquals(SessionStatus.FAILED, summary.status)
    }

    @Test
    fun `无助手正文时预览回落到用户输入`() {
        val summary = SessionSummaryReducer.reduce(listOf(turnStarted("只问一句")))
        assertEquals("只问一句", summary.preview)
    }

    // ─────────── helpers ───────────

    private fun turnStarted(text: String) = TurnStarted(newEventId(), sessionId, turnId, 0L, text)

    private fun messageDelta(text: String) = MessageDelta(newEventId(), sessionId, turnId, 0L, 0L, text)

    private val echoSpec = ToolSpec(
        name = "echo",
        description = "回显",
        kind = ToolKind.OTHER,
        riskLevel = RiskLevel.WRITE,
    )

    private val echoCall = ToolCall(ToolCallId("c1"), "echo")

    private fun toolProposed(): ToolCallProposed =
        ToolCallProposed(newEventId(), sessionId, turnId, 0L, echoCall, echoSpec)

    private fun toolSucceeded(): ToolCallSucceeded =
        ToolCallSucceeded(
            newEventId(), sessionId, turnId, 0L,
            ToolResult(echoCall.id, ToolOutput.Text("ok")),
        )

    private fun turnCompleted() = TurnCompleted(
        newEventId(), sessionId, turnId, 0L,
        stopReason = com.deepcode.core.model.StopReason.END_TURN,
        usage = Usage(),
        iterations = 1,
    )

    private fun turnFailed() = TurnFailed(
        newEventId(), sessionId, turnId, 0L,
        error = com.deepcode.core.model.AgentError(
            code = com.deepcode.core.model.ErrorCode.INTERNAL,
            message = "boom",
        ),
    )
}
