package com.deepcode.core.mcp

import com.deepcode.core.agent.spi.Tool
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class McpServerManagerTest {

    private companion object {
        val EMPTY_JSON = JsonObject(emptyMap())
        fun mcpTool(name: String): McpToolDef = McpToolDef(name, "desc", EMPTY_JSON, null, "T")
        fun config(id: String) = McpServerConfig(id, id, McpTransport.Http("https://x/$id"))
    }

    private fun fake(name: String, toolsProvider: () -> List<McpToolDef>): FakeMcpClient =
        FakeMcpClient(name, toolsProvider)

    @Test
    fun `connectAll 桥接所有 server 工具_单点失败不传染`() = runTest {
        val srvA = fake("a") { listOf(mcpTool("t1"), mcpTool("t2")) }
        val srvB = fake("b") { listOf(mcpTool("t3")) }
        val failing = object : McpClient {
            override val serverName = "bad"
            override suspend fun connect() = error("boom")
            override suspend fun listTools(): List<McpToolDef> = emptyList()
            override suspend fun callTool(name: String, arguments: JsonObject) = TODO()
            override fun setToolsChangedHandler(handler: () -> Unit) {}
            override suspend fun close() {}
        }
        val manager = McpServerManager(
            configs = listOf(config("a"), config("b"), config("bad")),
            clientFactory = { cfg ->
                when (cfg.id) {
                    "a" -> srvA; "b" -> srvB; else -> failing
                }
            },
            scope = this,
        )

        manager.connectAll()

        val tools = manager.tools()
        assertEquals(3, tools.size)
        assertTrue(srvA.connected && srvB.connected)
        assertTrue(tools.any { it.spec.name == "a__t1" })
        assertTrue(tools.any { it.spec.name == "b__t3" })
        // 失败的 server 状态被记录，但不影响其它；state 含全部配置（含 bad）
        assertNotNull(manager.state["bad"]?.error)
        assertEquals(3, manager.state.size) // a, b, bad 三个配置都在状态表里

        manager.disconnectAll()
        assertTrue(srvA.closed)
    }

    @Test
    fun `listChanged 触发后重新拉取清单`() = runTest {
        var version = 1
        val srv = fake("a") { if (version == 1) listOf(mcpTool("old")) else listOf(mcpTool("new")) }
        val manager = McpServerManager(listOf(config("a")), { srv }, scope = this)

        manager.connectAll()
        assertEquals(1, manager.tools().size)
        assertTrue(manager.tools().any { it.spec.name == "a__old" })

        version = 2
        srv.fireChanged()
        runCurrent() // 刷新在 scope.launch 内，推进测试调度器

        assertEquals(1, manager.tools().size)
        assertTrue(manager.tools().any { it.spec.name == "a__new" })
    }

    private class FakeMcpClient(
        override val serverName: String,
        private val toolsProvider: () -> List<McpToolDef>,
    ) : McpClient {
        var connected = false
        var closed = false
        private var handler: (() -> Unit)? = null

        override suspend fun connect() { connected = true }
        override suspend fun listTools(): List<McpToolDef> = toolsProvider()
        override suspend fun callTool(name: String, arguments: JsonObject) =
            McpCallToolResult(content = listOf(McpTextContent("ok:$name")), isError = false)

        override fun setToolsChangedHandler(handler: () -> Unit) { this.handler = handler }
        override suspend fun close() { closed = true }

        fun fireChanged() = handler?.invoke()
    }
}
