package com.deepcode.core.agent.spi

import com.deepcode.core.model.SessionId
import com.deepcode.core.model.ToolCall
import com.deepcode.core.model.ToolCallId
import com.deepcode.core.model.ToolResult
import com.deepcode.core.model.ToolSpec
import com.deepcode.core.model.TurnId
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * 工具的执行环境。Runtime 每次调用时构造，工具通过它拿到工作区与沙箱。
 *
 * 注意工具**拿不到** PermissionGate —— 权限不是工具自己能决定的事。
 */
interface ToolContext {
    val sessionId: SessionId
    val turnId: TurnId
    val callId: ToolCallId
    val workspace: Workspace?
    val sandbox: Sandbox?

    /** 输出流式进度（命令实时 stdout、下载进度…）。UI 会增量追加到该调用下。 */
    fun emitProgress(chunk: String)

    /** 用户在 UI 点了停止。长任务应当在循环里检查并提前退出。 */
    fun isCancelled(): Boolean
}

/**
 * 一个能力单元。
 *
 * 加新功能 = 实现这个接口 + 注册。这就是给未来留的口子：
 * 无论是 MCP 工具、设备能力（相机/剪贴板）、还是子 Agent 委托，
 * 在 Runtime 眼里都是同一个 Tool。
 */
interface Tool {
    val spec: ToolSpec
    suspend fun execute(context: ToolContext, call: ToolCall): ToolResult
}

interface ToolRegistry {
    fun register(tool: Tool)
    fun unregister(name: String)
    operator fun get(name: String): Tool?
    fun all(): List<Tool>
    fun specs(): List<ToolSpec>
}

class DefaultToolRegistry : ToolRegistry {
    private val tools = LinkedHashMap<String, Tool>()

    override fun register(tool: Tool) {
        tools[tool.spec.name] = tool
    }

    override fun unregister(name: String) {
        tools.remove(name)
    }

    override fun get(name: String): Tool? = tools[name]

    override fun all(): List<Tool> = tools.values.toList()

    override fun specs(): List<ToolSpec> = tools.values.map { it.spec }
}

// ───────────────────────── 参数读取辅助 ─────────────────────────

fun ToolCall.arg(key: String): String? = arguments[key]?.jsonPrimitive?.contentOrNull

fun ToolCall.argOrElse(key: String, fallback: String): String = arg(key) ?: fallback

fun ToolCall.argOrThrow(key: String): String =
    requireNotNull(arg(key)) { "工具 ${name} 缺少必填参数：$key" }

fun ToolCall.argInt(key: String, fallback: Int): Int = arg(key)?.toIntOrNull() ?: fallback

fun ToolCall.argBool(key: String, fallback: Boolean = false): Boolean =
    arg(key)?.toBooleanStrictOrNull() ?: fallback

fun ToolCall.rawArguments(): JsonObject = arguments

fun ToolCall.argument(key: String): JsonElement? = arguments[key]
