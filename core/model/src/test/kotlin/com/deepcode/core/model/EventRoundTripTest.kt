package com.deepcode.core.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * core:model 事件与工具契约的序列化往返测试。
 *
 * 这些类型是整条链路（落盘 SQLite events.payload → 重启重放恢复会话）的协议契约，
 * 一旦破坏，老版本写的库 = 新版本读不了。用与 core:data 的 [EventCodec] 一致的
 * `Json { ignoreUnknownKeys = true }` 配置做往返，确保"编码→解码→等值"。
 */
class EventRoundTripTest {

    /** 与 core:data EventCodec 保持一致的 Json 配置。 */
    private val json: Json = Json { ignoreUnknownKeys = true }

    private fun roundTrip(event: AgentEvent): AgentEvent =
        json.decodeFromString<AgentEvent>(json.encodeToString<AgentEvent>(event))

    // ───────────────────────── 轮次生命周期 ─────────────────────────

    @Test
    fun `TurnStarted 含附件 往返等值`() {
        val event = TurnStarted(
            id = EventId("evt-1"), sessionId = SessionId("ses-1"), turnId = TurnId("turn-1"), ts = 1000L,
            userInput = "看看 README",
            attachments = listOf(
                Attachment.File("a/b.txt", "text/plain"),
                Attachment.Text("标题", "内容"),
            ),
        )
        assertEquals(event, roundTrip(event))
        assertIs<TurnStarted>(roundTrip(event))
    }

    @Test
    fun `TurnCompleted 含用量与停止原因 往返等值`() {
        val event = TurnCompleted(
            id = EventId("evt-2"), sessionId = SessionId("ses-1"), turnId = TurnId("turn-1"), ts = 2000L,
            stopReason = StopReason.END_TURN,
            usage = Usage(inputTokens = 100, outputTokens = 40, cacheReadTokens = 30, cacheCreationTokens = 10),
            iterations = 3,
        )
        assertEquals(event, roundTrip(event))
    }

    @Test
    fun `TurnFailed 错误信息 往返等值`() {
        val event = TurnFailed(
            id = EventId("evt-3"), sessionId = SessionId("ses-1"), turnId = TurnId("turn-1"), ts = 3000L,
            error = AgentError(ErrorCode.PROVIDER, "连接超时", retryable = true, detail = "stack trace"),
        )
        assertEquals(event, roundTrip(event))
    }

    // ───────────────────────── 流式增量 ─────────────────────────

    @Test
    fun `MessageDelta 与 ThinkingDelta 往返等值`() {
        assertEquals(
            MessageDelta(EventId("evt-4"), SessionId("ses-1"), TurnId("turn-1"), 4000L, 7L, "你好"),
            roundTrip(MessageDelta(EventId("evt-4"), SessionId("ses-1"), TurnId("turn-1"), 4000L, 7L, "你好")),
        )
        assertEquals(
            ThinkingDelta(EventId("evt-5"), SessionId("ses-1"), TurnId("turn-1"), 4001L, 0L, "思考…"),
            roundTrip(ThinkingDelta(EventId("evt-5"), SessionId("ses-1"), TurnId("turn-1"), 4001L, 0L, "思考…")),
        )
    }

    // ───────────────────────── 工具调用全链路 ─────────────────────────

    @Test
    fun `ToolCallStarted 带参数 往返等值`() {
        val event = ToolCallStarted(
            id = EventId("evt-6"), sessionId = SessionId("ses-1"), turnId = TurnId("turn-1"), ts = 5000L,
            call = ToolCall(ToolCallId("call-1"), "write_file", buildJsonObject {
                put("path", "notes.txt")
                put("content", "hi")
            }),
        )
        assertEquals(event, roundTrip(event))
        assertSameTypes(event, roundTrip(event))
    }

    @Test
    fun `ToolCallSucceeded 的 Text 与 Diff 产物往返等值`() {
        val textEvent = ToolCallSucceeded(
            EventId("evt-7"), SessionId("ses-1"), TurnId("turn-1"), 6000L,
            ToolResult(ToolCallId("call-1"), ToolOutput.Text("done", language = "kotlin"), durationMs = 12L),
        )
        assertEquals(textEvent, roundTrip(textEvent))

        val diffEvent = ToolCallSucceeded(
            EventId("evt-8"), SessionId("ses-1"), TurnId("turn-1"), 6001L,
            ToolResult(
                ToolCallId("call-2"),
                ToolOutput.Diff(path = "Main.kt", unified = "…", addedLines = 4, removedLines = 1),
                durationMs = 8L,
            ),
        )
        assertEquals(diffEvent, roundTrip(diffEvent))
    }

    @Test
    fun `ToolSpec 与 ToolCall 往返等值`() {
        val spec = ToolSpec(
            name = "read_file",
            description = "读取文件",
            kind = ToolKind.READ,
            riskLevel = RiskLevel.READ_ONLY,
            parameters = buildJsonObject { put("path", "string") },
            origin = ToolOrigin.BUILTIN,
            streamsOutput = false,
        )
        val jsonSpec = json.encodeToString<ToolSpec>(spec)
        assertEquals(spec, json.decodeFromString<ToolSpec>(jsonSpec))

        val call = ToolCall(ToolCallId("call-3"), "list_files", buildJsonObject { put("path", ".") })
        val jsonCall = json.encodeToString<ToolCall>(call)
        assertEquals(call, json.decodeFromString<ToolCall>(jsonCall))
    }

    // ───────────────────────── 判别契约 ─────────────────────────

    @Test
    fun `事件判别字段使用稳定的 SerialName 而非类名`() {
        // 防止有人把多态判别改成类名——R8 混淆后类名会变，落盘 type 就废了。
        val event: AgentEvent = TurnStarted(EventId("evt-9"), SessionId("ses-1"), TurnId("turn-1"), 9000L, "hi")
        val payload = json.encodeToString<AgentEvent>(event)
        assertEquals("turn_started", (json.parseToJsonElement(payload) as JsonObject)["type"]?.let { it.toString().trim('"') })
    }

    private fun assertSameTypes(expected: AgentEvent, actual: AgentEvent) {
        assertIs<ToolCallStarted>(actual)
        assertEquals(expected, actual)
    }
}