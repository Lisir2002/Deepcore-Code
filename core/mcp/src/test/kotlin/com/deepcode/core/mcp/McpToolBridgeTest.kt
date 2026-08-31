package com.deepcode.core.mcp

import com.deepcode.core.model.RiskLevel
import com.deepcode.core.model.ToolCallId
import com.deepcode.core.model.ToolKind
import com.deepcode.core.model.ToolOrigin
import com.deepcode.core.model.ToolOutput
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class McpToolBridgeTest {

    private companion object {
        val EMPTY_JSON = JsonObject(emptyMap())
    }

    private fun mcpTool(
        name: String,
        annotations: McpToolAnnotations? = null,
        description: String = "desc $name",
    ): McpToolDef = McpToolDef(
        name = name,
        description = description,
        inputSchema = EMPTY_JSON,
        annotations = annotations,
        title = "T $name",
    )

    private fun config(id: String, trusted: Boolean = false) = McpServerConfig(
        id = id,
        displayName = id,
        transport = McpTransport.Http("https://example.com/$id"),
        trusted = trusted,
    )

    // ───────────── 命名空间 / 来源 ─────────────

    @Test
    fun `工具名带 server 命名空间_origin 为 MCP`() {
        val spec = McpToolBridge.toToolSpec(config("fs"), mcpTool("read"))
        assertEquals("fs__read", spec.name)
        assertEquals(ToolOrigin.MCP, spec.origin)
        assertEquals("fs", spec.sourceId)
        assertEquals(ToolKind.OTHER, spec.kind)
        assertFalse(spec.requiresWorkspace)
    }

    // ───────────── 风险映射 ─────────────

    @Test
    fun `未受信任一律 NETWORK`() {
        assertEquals(
            RiskLevel.NETWORK,
            McpToolBridge.mapRisk(
                McpToolAnnotations(null, true, true, true, true),
                trusted = false,
            ),
        )
    }

    @Test
    fun `受信任_readOnly 降为 READ_ONLY`() {
        assertEquals(
            RiskLevel.READ_ONLY,
            McpToolBridge.mapRisk(McpToolAnnotations(readOnlyHint = true), trusted = true),
        )
    }

    @Test
    fun `受信任_destructive 升为 DESTRUCTIVE`() {
        assertEquals(
            RiskLevel.DESTRUCTIVE,
            McpToolBridge.mapRisk(McpToolAnnotations(destructiveHint = true), trusted = true),
        )
    }

    @Test
    fun `受信任_默认兜底 WRITE`() {
        assertEquals(RiskLevel.WRITE, McpToolBridge.mapRisk(null, trusted = true))
    }

    @Test
    fun `annotations 原样存档进 spec`() {
        val ann = McpToolAnnotations("我的工具", readOnlyHint = true)
        val spec = McpToolBridge.toToolSpec(config("fs", trusted = true), mcpTool("r", ann))
        assertEquals(RiskLevel.READ_ONLY, spec.riskLevel)
        assertNotNull(spec.annotations, "annotations 应原样存档")
        assertTrue(spec.annotations!!.contains("readOnlyHint"))
    }

    // ───────────── 结果映射 ─────────────

    private fun result(blocks: List<McpContent>, isError: Boolean = false) =
        McpToolBridge.toToolResult(
            ToolCallId("c1"),
            McpCallToolResult(content = blocks, isError = isError, structuredContent = null),
        )

    @Test
    fun `文本结果映射为 ToolOutput_Text`() {
        val r = result(listOf(McpTextContent("hello")))
        assertTrue(r.isSuccess)
        assertTrue(r.output is ToolOutput.Text)
        assertEquals("hello", (r.output as ToolOutput.Text).text)
    }

    @Test
    fun `图片结果映射为 ToolOutput_Image`() {
        val r = result(listOf(McpImageContent("ZGF0YQ==", "image/png")))
        assertTrue(r.output is ToolOutput.Image)
        assertEquals("image/png", (r.output as ToolOutput.Image).mimeType)
    }

    @Test
    fun `资源链接映射为 ToolOutput_ResourceLink`() {
        val r = result(
            listOf(
                McpResourceLinkContent(
                    uri = "file:///a.txt",
                    name = "a",
                    mimeType = "text/plain",
                ),
            ),
        )
        assertTrue(r.output is ToolOutput.ResourceLink)
        assertEquals("file:///a.txt", (r.output as ToolOutput.ResourceLink).uri)
    }

    @Test
    fun `错误结果带 ToolError`() {
        val r = result(listOf(McpTextContent("boom")), isError = true)
        assertFalse(r.isSuccess)
        assertNotNull(r.error)
        assertEquals("boom", (r.output as ToolOutput.Text).text)
    }
}
