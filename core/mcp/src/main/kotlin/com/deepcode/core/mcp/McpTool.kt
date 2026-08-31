package com.deepcode.core.mcp

import com.deepcode.core.agent.spi.Tool
import com.deepcode.core.agent.spi.ToolContext
import com.deepcode.core.model.ToolCall
import com.deepcode.core.model.ToolResult
import com.deepcode.core.model.ToolSpec

/**
 * 经 MCP 桥接的工具。
 *
 * 在 Runtime 眼里它就是普通 [Tool]——execute 时把调用转交远端 [McpClient]，
 * 结果由 [McpToolBridge] 归一化成 [ToolResult]。spec 里的命名空间与风险等级
 * 已由 [McpToolBridge.toToolSpec] 预先算好，这里不再做策略判断。
 */
class McpTool(
    override val spec: ToolSpec,
    private val client: McpClient,
    /** MCP server 侧原始工具名（不含命名空间），callTool 用它寻址。 */
    private val mcpName: String,
) : Tool {

    override suspend fun execute(context: ToolContext, call: ToolCall): ToolResult {
        val result = client.callTool(mcpName, call.arguments)
        return McpToolBridge.toToolResult(call.id, result)
    }
}
