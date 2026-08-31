package com.deepcode.core.mcp

import com.deepcode.core.agent.spi.ToolContext
import com.deepcode.core.model.RiskLevel
import com.deepcode.core.model.SessionId
import com.deepcode.core.model.ToolCall
import com.deepcode.core.model.ToolCallId
import com.deepcode.core.model.ToolKind
import com.deepcode.core.model.ToolOrigin
import com.deepcode.core.model.ToolOutput
import com.deepcode.core.model.ToolSpec
import com.deepcode.core.model.TurnId
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class McpToolTest {

    /** 最小 ToolContext 实现（测试用，不依赖真实 Runtime）。 */
    private class FakeCtx : ToolContext {
        override val sessionId: SessionId = SessionId("s")
        override val turnId: TurnId = TurnId("t")
        override val callId: ToolCallId = ToolCallId("c")
        override val workspace = null
        override val sandbox = null
        override fun emitProgress(chunk: String) {}
        override fun isCancelled(): Boolean = false
    }

    @Test
    fun `execute 经 client 调用并桥接结果`() = runTest {
        val captured = mutableMapOf<String, String>()
        val client = object : McpClient {
            override val serverName = "srv"
            override suspend fun connect() {}
            override suspend fun listTools(): List<McpToolDef> = emptyList()
            override suspend fun callTool(name: String, arguments: JsonObject) =
                McpCallToolResult(content = listOf(McpTextContent("hi $name")), isError = false)
                    .also {
                        captured["name"] = name
                        captured["arg"] = arguments.toString()
                    }
            override fun setToolsChangedHandler(handler: () -> Unit) {}
            override suspend fun close() {}
        }
        val spec = ToolSpec(
            name = "srv__greet",
            description = "d",
            kind = ToolKind.OTHER,
            riskLevel = RiskLevel.READ_ONLY,
            parameters = buildJsonObject {},
            origin = ToolOrigin.MCP,
            sourceId = "srv",
        )
        val tool = McpTool(spec, client, "greet")
        val call = ToolCall(ToolCallId("c1"), "srv__greet", buildJsonObject { put("x", "y") })

        val res = tool.execute(FakeCtx(), call)

        assertEquals("greet", captured["name"])
        assertEquals("""{"x":"y"}""", captured["arg"])
        assertTrue(res.isSuccess)
        assertTrue(res.output is ToolOutput.Text)
        assertEquals("hi greet", (res.output as ToolOutput.Text).text)
    }
}
