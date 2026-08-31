package com.deepcode.core.mcp

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * MCP 线协议类型（最小子集，对齐 MCP 规范 2025-11-05）。
 *
 * 为什么自己定义而不是引官方 SDK：官方 Kotlin SDK 自 0.6+ 需 Kotlin 2.2+，
 * 与本项目锁定的 Kotlin 2.0.21 元数据不兼容。这里只用项目已有的
 * kotlinx-serialization 描述协议字段，零额外依赖，且字段与规范一一对应——
 * 换官方 SDK 时这些类型可整体删掉，桥接代码改为消费 SDK 类型即可。
 */

/** tools/list 返回的工具定义（协议层，区别于同包的桥接工具类 [McpTool]）。 */
@Serializable
data class McpToolDef(
    val name: String,
    val description: String = "",
    /** JSON Schema（与 ToolSpec.parameters 同构）。 */
    val inputSchema: JsonObject = JsonObject(emptyMap()),
    val annotations: McpToolAnnotations? = null,
    val title: String? = null,
)

/** MCP 工具的能力提示。规范要求客户端必须视为不可信（见 docs/TOOLS_SKILLS.md §4）。 */
@Serializable
data class McpToolAnnotations(
    val title: String? = null,
    val readOnlyHint: Boolean? = null,
    val destructiveHint: Boolean? = null,
    val idempotentHint: Boolean? = null,
    val openWorldHint: Boolean? = null,
)

@Serializable
data class McpListToolsResult(
    val tools: List<McpToolDef> = emptyList(),
    val nextCursor: String? = null,
)

/**
 * tools/call 返回的内容块。用 "type" 字段作多态判别符（@JsonClassDiscriminator），
 * 与 MCP 线上的 type 取值（text/image/resource/resource_link）一致。
 */
@Serializable(McpContentSerializer::class)
sealed interface McpContent

object McpContentSerializer : JsonContentPolymorphicSerializer<McpContent>(McpContent::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<McpContent> =
        when (val type = element.jsonObject["type"]?.toString()?.trim('"')) {
            "image" -> McpImageContent.serializer()
            "resource" -> McpResourceContent.serializer()
            "resource_link" -> McpResourceLinkContent.serializer()
            else -> McpTextContent.serializer()
        }
}

@Serializable
@SerialName("text")
data class McpTextContent(val text: String) : McpContent

@Serializable
@SerialName("image")
data class McpImageContent(val data: String, val mimeType: String) : McpContent

/** resource：内嵌在结果里的资源内容（MCP 2025-11-05 用 resource 包裹）。 */
@Serializable
@SerialName("resource")
data class McpResourceContent(val resource: McpResourceLink) : McpContent

/** resource_link：指向某资源的链接（不在结果内联内容）。 */
@Serializable
@SerialName("resource_link")
data class McpResourceLinkContent(
    val uri: String,
    val name: String? = null,
    val description: String? = null,
    val mimeType: String? = null,
) : McpContent

@Serializable
data class McpResourceLink(
    val uri: String,
    val name: String? = null,
    val description: String? = null,
    val mimeType: String? = null,
    val title: String? = null,
)

/** tools/call 的返回结果。 */
@Serializable
data class McpCallToolResult(
    val content: List<McpContent> = emptyList(),
    val isError: Boolean? = null,
    val structuredContent: JsonObject? = null,
)

/** JSON-RPC 2.0 错误对象。 */
@Serializable
data class McpJsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null,
)
