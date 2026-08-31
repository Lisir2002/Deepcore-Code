package com.deepcode.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * 工具的风险等级。
 *
 * 关键设计：**风险等级由工具声明，由 PermissionGate 统一裁决**。
 * 绝不允许工具内部自己弹确认框——散落各处的 `if (confirm)` 写着写着就漏了。
 */
@Serializable
enum class RiskLevel {
    /** 只读，永不改变状态。默认放行。 */
    @SerialName("read_only") READ_ONLY,

    /** 可撤销的写入（写文件、编辑）。首次确认，可"本次会话始终允许"。 */
    @SerialName("write") WRITE,

    /** 不可逆或影响范围大（删除、git push、强制覆盖）。每次都要确认。 */
    @SerialName("destructive") DESTRUCTIVE,

    /** 触网（WebFetch、WebSearch）。涉及数据外发，单独授权。 */
    @SerialName("network") NETWORK,

    /** 触碰设备敏感能力（相机、位置、剪贴板、无障碍）。需系统权限 + 用户确认。 */
    @SerialName("privileged") PRIVILEGED,
}

/** 工具产物的形态。UI 按这个类型选渲染器，而不是靠工具名硬编码 if/else。 */
@Serializable
enum class ToolKind {
    @SerialName("read") READ,
    @SerialName("write") WRITE,
    @SerialName("search") SEARCH,
    @SerialName("execute") EXECUTE,
    @SerialName("git") GIT,
    @SerialName("web") WEB,
    @SerialName("device") DEVICE,
    @SerialName("delegate") DELEGATE,
    @SerialName("other") OTHER,
}

/**
 * 工具来源。
 *
 * 对齐 MCP 的互操作模型（见 docs/TOOLS_SKILLS.md §4）：
 * 本地内置工具与外部 MCP Server 桥接来的工具在 Runtime 眼里同构，
 * 仅用 origin 标记来源供 UI / 排序 / 权限策略区分。
 */
@Serializable
enum class ToolOrigin {
    /** 随 App 内置的工具（本地能力实现）。 */
    @SerialName("builtin") BUILTIN,

    /** 经 MCP Client 桥接的外部工具（来自某个 MCP Server）。 */
    @SerialName("mcp") MCP,
}

/**
 * 工具声明。由各工具模块注册进 ToolRegistry，Runtime 只认这个结构。
 *
 * 字段对齐 MCP Tool（name/title/description/inputSchema/annotations）：
 * - [parameters] 与 MCP 的 inputSchema 同构（JSON Schema），透传给模型层做 function calling；
 * - [annotations] 是 MCP server 声明的能力提示，**视为不可信**（规范要求），
 *   仅存档展示，风险裁决只认本地 [RiskLevel] + PermissionGate。
 */
@Serializable
data class ToolSpec(
    val name: String,
    val description: String,
    val kind: ToolKind,
    val riskLevel: RiskLevel,
    /** JSON Schema，交给 LLM 做 function calling。与 MCP inputSchema 同构。 */
    val parameters: JsonObject = JsonObject(emptyMap()),
    /** 是否需要工作区已就绪。 */
    val requiresWorkspace: Boolean = true,
    /** 是否支持流式输出增量（如 shell 的实时 stdout）。 */
    val streamsOutput: Boolean = false,
    /** 人类可读标题（MCP tool.title）。 */
    val title: String? = null,
    /** 工具来源。 */
    val origin: ToolOrigin = ToolOrigin.BUILTIN,
    /** 来源标识：MCP 工具为 server 名，本地工具为 null。 */
    val sourceId: String? = null,
    /** MCP annotations 原样存档（readOnlyHint/destructiveHint/idempotentHint/openWorldHint/title）。 */
    val annotations: JsonObject? = null,
)

/** LLM 发起的一次工具调用请求。 */
@Serializable
data class ToolCall(
    val id: ToolCallId,
    val name: String,
    val arguments: JsonObject = JsonObject(emptyMap()),
)

/**
 * 工具的结构化产物。
 *
 * 为什么不用一个 String 了事：因为 UI 要按产物类型渲染（Diff 要有并排高亮、
 * 文件列表要能点开、命令输出要等宽滚动条）。如果这里退化成 String，
 * UI 层就不得不 `when (tool.name)` 硬编码，新加一个工具就要改一遍所有页面
 * —— 这正是"UI 越写越乱"的根源。
 */
@Serializable
sealed interface ToolOutput {

    @Serializable
    @SerialName("text")
    data class Text(
        val text: String,
        val language: String? = null,
        val truncated: Boolean = false,
    ) : ToolOutput

    @Serializable
    @SerialName("diff")
    data class Diff(
        val path: String,
        val unified: String,
        val addedLines: Int = 0,
        val removedLines: Int = 0,
    ) : ToolOutput

    @Serializable
    @SerialName("file_list")
    data class FileList(
        val root: String,
        val entries: List<FileEntry>,
    ) : ToolOutput {
        @Serializable
        data class FileEntry(
            val path: String,
            val isDirectory: Boolean,
            val sizeBytes: Long? = null,
        )
    }

    @Serializable
    @SerialName("search_hits")
    data class SearchHits(
        val query: String,
        val hits: List<Hit>,
        val truncated: Boolean = false,
    ) : ToolOutput {
        @Serializable
        data class Hit(
            val path: String,
            val line: Int,
            val column: Int = 0,
            val snippet: String,
        )
    }

    @Serializable
    @SerialName("key_values")
    data class KeyValues(
        val pairs: List<Pair<String, String>>,
    ) : ToolOutput

    /** 图片产物（MCP content type=image）。 */
    @Serializable
    @SerialName("image")
    data class Image(
        val mimeType: String,
        val base64: String,
    ) : ToolOutput

    /** 资源链接产物（MCP content type=resource_link）。 */
    @Serializable
    @SerialName("resource_link")
    data class ResourceLink(
        val uri: String,
        val name: String? = null,
    ) : ToolOutput

    /**
     * 结构化 JSON 产物（MCP structuredContent）。
     * 模型回灌时直接序列化为 JSON 文本；UI 可按需做键值视图。
     */
    @Serializable
    @SerialName("structured")
    data class Structured(
        val json: JsonObject,
    ) : ToolOutput

    @Serializable
    @SerialName("empty")
    data object Empty : ToolOutput
}

@Serializable
data class ToolError(
    val code: String,
    val message: String,
    /** 该错误对 LLM 是否可恢复（比如路径写错 vs 权限不足）。 */
    val recoverable: Boolean = true,
)

/** 工具执行结果。成功与失败都是"结果"，不抛异常穿越 Agent 循环。 */
@Serializable
data class ToolResult(
    val callId: ToolCallId,
    val output: ToolOutput,
    val error: ToolError? = null,
    val durationMs: Long = 0,
) {
    val isSuccess: Boolean get() = error == null
}
