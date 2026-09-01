package com.deepcode.core.uistate

import com.deepcode.core.model.RiskLevel
import com.deepcode.core.model.ToolCall
import com.deepcode.core.model.ToolCallId
import com.deepcode.core.model.ToolKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * §6.8.3 执行组聚组算法的纯 Kotlin 单测（M0 铁律：聚合逻辑在 core:uistate，离开 Compose）。
 */
class AppBlockGroupReducerTest {

    @Test
    fun `空列表原样返回`() {
        assertTrue(AppBlockGroupReducer.group(emptyList()).isEmpty())
    }

    @Test
    fun `连续 thinking 与 tool 聚为一组_其余块截断分组`() {
        val blocks = listOf(
            user("u"),
            thinking("think-a"),
            tool("tool-a"),
            tool("tool-b"),
            text("正文"),
            thinking("think-b"),
            tool("tool-c"),
            footer(),
        )

        val grouped = AppBlockGroupReducer.group(blocks)

        assertEquals(5, grouped.size, "1 用户 + 1 组(3) + 1 正文 + 1 组(2) + 1 收尾 = 5 个大块")
        assertIs<RenderBlock.UserMessage>(grouped[0])
        assertIs<RenderBlock.Group>(grouped[1])
        assertEquals(3, (grouped[1] as RenderBlock.Group).blocks.size, "thinking+tool+tool 应聚为 3 步组")
        assertIs<RenderBlock.AssistantText>(grouped[2])
        assertIs<RenderBlock.Group>(grouped[3])
        assertEquals(2, (grouped[3] as RenderBlock.Group).blocks.size)
        assertIs<RenderBlock.TurnFooter>(grouped[4])
    }

    @Test
    fun `正文块打断组_不进入组内`() {
        val grouped = AppBlockGroupReducer.group(listOf(tool("a"), text("中"), tool("b")))

        assertEquals(3, grouped.size, "正文夹在中间应各自成块，不合并成一组")
        assertIs<RenderBlock.ToolInvocation>(grouped[0])
        assertIs<RenderBlock.AssistantText>(grouped[1])
        assertIs<RenderBlock.ToolInvocation>(grouped[2])
    }

    @Test
    fun `单个 dangling 工具块不包组_保持原样直通`() {
        val grouped = AppBlockGroupReducer.group(listOf(user("u"), tool("a")))

        assertEquals(2, grouped.size)
        assertIs<RenderBlock.UserMessage>(grouped[0])
        assertIs<RenderBlock.ToolInvocation>(grouped[1], "单工具不应套无意义的组壳")
    }

    @Test
    fun `组 key 稳定_由首子块 key 派生`() {
        val grouped = AppBlockGroupReducer.group(listOf(thinking("t1"), tool("a")))
        val group = grouped.single() as RenderBlock.Group
        assertEquals("group-${group.blocks.first().key}", group.key)
    }

    // ─────────────────────────── 构造辅助 ───────────────────────────

    private fun user(text: String) = RenderBlock.UserMessage(key = "user-$text", text = text)
    private fun thinking(text: String) = RenderBlock.Thinking(key = "think-$text", text = text, streaming = false)
    private fun text(text: String) = RenderBlock.AssistantText(key = "text-$text", text = text, streaming = false)
    private fun tool(name: String) = RenderBlock.ToolInvocation(
        key = "tool-$name",
        call = ToolCall(ToolCallId(name), name),
        toolName = name,
        kind = ToolKind.OTHER,
        risk = RiskLevel.READ_ONLY,
        status = ToolVisualStatus.SUCCEEDED,
        argumentsSummary = "",
    )
    private fun footer() = RenderBlock.TurnFooter(key = "footer", inputTokens = 0, outputTokens = 0, iterations = 0, durationMs = 0)
}