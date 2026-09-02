package com.deepcode.core.mcp

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 用 okhttp MockWebServer 真·起一个本地 HTTP 端点，验证 [HttpJsonRpcMcpClient]
 * 的 JSON-RPC 握手（initialize → initialized → tools/list → tools/call）与
 * 普通 JSON / SSE 两种响应形态的解析。传输层是真实 OkHttp，仅 server 被 mock。
 */
class HttpJsonRpcMcpClientTest {

    private lateinit var server: MockWebServer
    private lateinit var baseUrl: String

    @BeforeTest
    fun setup() {
        server = MockWebServer()
        server.start()
        baseUrl = server.url("/mcp").toString()
    }

    @AfterTest
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun `initialize+tools list+call 全程握手并正确解析`() = runBlocking {
        // 1) initialize
        server.enqueue(
            jsonResponse(
                """{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-11-05","capabilities":{},"serverInfo":{"name":"srv","version":"1"}}}""",
            ),
        )
        // 2) notifications/initialized（202 + 空 body，不应解析）
        server.enqueue(MockResponse().setResponseCode(202).setBody(""))
        // 3) tools/list
        server.enqueue(
            jsonResponse(
                """{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"greet","description":"say hi","inputSchema":{"type":"object","properties":{}}}]}}""",
            ),
        )
        // 4) tools/call
        server.enqueue(
            jsonResponse(
                """{"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"hi from server"}]}}""",
            ),
        )

        val client = HttpJsonRpcMcpClient("srv", baseUrl)
        client.connect()
        val tools = client.listTools()
        assertEquals(1, tools.size)
        assertEquals("greet", tools[0].name)

        val res = client.callTool("greet", buildJsonObject { put("name", "x") })
        assertEquals(1, res.content.size)
        assertTrue(res.content[0] is McpTextContent)
        assertEquals("hi from server", (res.content[0] as McpTextContent).text)

        client.close()
    }

    @Test
    fun `SSE 响应抽取 data 行正确解析`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-11-05","capabilities":{},"serverInfo":{"name":"srv","version":"1"}}}""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(202).setBody(""))
        // SSE 格式：event + data
        val sse =
            "event: message\r\ndata: {\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"tools\":[{\"name\":\"a\",\"description\":\"d\",\"inputSchema\":{\"type\":\"object\"}}]}}\r\n\r\n"
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/event-stream").setBody(sse),
        )

        val client = HttpJsonRpcMcpClient("srv", baseUrl)
        client.connect()
        val tools = client.listTools()
        assertEquals(1, tools.size)
        assertEquals("a", tools[0].name)
        client.close()
    }

    @Test
    fun `非法协议或缺 host 的 URL 在构造期被拒绝`() {
        val bad = listOf(
            "file:///etc/passwd",
            "content://com.deepcode.secret",
            "javascript://x",
            "ftp://example.com/mcp",
            "https://", // 缺 host
            "     ",    // 空白
        )
        bad.forEach { url ->
            assertFailsWith<IllegalArgumentException> { HttpJsonRpcMcpClient("srv", url) }
        }
    }

    @Test
    fun `合法的 http https URL 可构造`() {
        listOf("http://127.0.0.1:8080/mcp", "https://mcp.example.com/sse")
            .forEach { url -> HttpJsonRpcMcpClient("srv", url) }
    }

    private fun jsonResponse(body: String): MockResponse =
        MockResponse().setHeader("Content-Type", "application/json").setBody(body)
}
