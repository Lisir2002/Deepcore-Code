package com.deepcode.core.mcp

import com.deepcode.core.model.RiskLevel
import com.deepcode.core.model.ToolCallId
import com.deepcode.core.model.ToolError
import com.deepcode.core.model.ToolKind
import com.deepcode.core.model.ToolOrigin
import com.deepcode.core.model.ToolOutput
import com.deepcode.core.model.ToolResult
import com.deepcode.core.model.ToolSpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * MCP ↔ 本机工具模型的双向映射（纯函数，无副作用，便于单测）。
 *
 * 设计要点见 docs/TOOLS_SKILLS.md §4：
 *  - 工具命名空间 `server__tool`：避免跨 server 重名冲突，也让 UI/权限按来源分流；
 *  - 风险映射照抄规范"annotations 不可信"：未受信任 server 一律 NETWORK，
 *    只有用户显式标记 trusted 才允许按 hints 降档；
 *  - annotations 原样存档进 ToolSpec（仅展示，不参加裁决）。
 */
object McpToolBridge {

    /** SDK 类型 ↔ kotlinx JsonObject 互转用的 Json 实例。 */
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 把一个 MCP 工具定义映射成本机 [ToolSpec]。
     * [config.id] 作为命名空间前缀；风险等级由 [config.trusted] 与 annotations 共同决定。
     */
    fun toToolSpec(config: McpServerConfig, mcpTool: McpToolDef): ToolSpec {
        val namespacedName = "${config.id}__${mcpTool.name}"
        val annotationsJson: JsonObject? = mcpTool.annotations
            ?.let { json.encodeToJsonElement(McpToolAnnotations.serializer(), it) as JsonObject }

        return ToolSpec(
            name = namespacedName,
            description = mcpTool.description,
            kind = ToolKind.OTHER,
            riskLevel = mapRisk(mcpTool.annotations, config.trusted),
            // 完整 inputSchema 透传给模型层做 function calling（与 MCP inputSchema 同构）
            parameters = mcpTool.inputSchema,
            requiresWorkspace = false,
            origin = ToolOrigin.MCP,
            sourceId = config.id,
            title = mcpTool.title ?: mcpTool.annotations?.title,
            annotations = annotationsJson,
        )
    }

    /**
     * 风险映射（MCP annotations → 本机 RiskLevel）。
     *
     * - 未受信任：一律 NETWORK（规范要求把 annotations 当不可信，外发数据必须授权）；
     * - 受信任：readOnly→READ_ONLY、destructive→DESTRUCTIVE、idempotent 且非开放世界→WRITE，
     *   其余兜底 WRITE。
     */
    fun mapRisk(annotations: McpToolAnnotations?, trusted: Boolean): RiskLevel {
        if (!trusted) return RiskLevel.NETWORK
        if (annotations?.readOnlyHint == true) return RiskLevel.READ_ONLY
        if (annotations?.destructiveHint == true) return RiskLevel.DESTRUCTIVE
        if (annotations?.idempotentHint == true && annotations.openWorldHint != true) {
            return RiskLevel.WRITE
        }
        return RiskLevel.WRITE
    }

    /** MCP 调用结果 → 本机 [ToolResult]（含错误归一化）。 */
    fun toToolResult(callId: ToolCallId, result: McpCallToolResult): ToolResult {
        val isError = result.isError == true
        val output: ToolOutput = if (isError) {
            ToolOutput.Text(result.content.joinToString("\n") { blockToText(it) })
        } else {
            blocksToOutput(result.content)
        }
        val error = if (isError) {
            ToolError(
                code = "mcp_tool_error",
                message = result.content.joinToString("\n") { blockToText(it) },
                recoverable = false,
            )
        } else {
            null
        }
        return ToolResult(callId = callId, output = output, error = error)
    }

    // ───────────────────────── content block 渲染 ─────────────────────────

    private fun blocksToOutput(blocks: List<McpContent>): ToolOutput = when {
        blocks.isEmpty() -> ToolOutput.Empty
        blocks.size == 1 -> blockToOutput(blocks[0])
        else -> ToolOutput.Text(blocks.joinToString("\n---\n") { blockToText(it) })
    }

    private fun blockToOutput(block: McpContent): ToolOutput = when (block) {
        is McpTextContent -> ToolOutput.Text(block.text)
        is McpImageContent -> ToolOutput.Image(block.mimeType, block.data)
        is McpResourceLinkContent -> ToolOutput.ResourceLink(block.uri, block.name)
        is McpResourceContent -> ToolOutput.Text("[内嵌资源]")
    }

    private fun blockToText(block: McpContent): String = when (block) {
        is McpTextContent -> block.text
        is McpImageContent -> "[图片：${block.mimeType}]"
        is McpResourceLinkContent -> "[资源：${block.name ?: "未命名"} ${block.uri}]"
        is McpResourceContent -> "[内嵌资源]"
    }
}
