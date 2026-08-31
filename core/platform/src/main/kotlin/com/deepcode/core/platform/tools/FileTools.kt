package com.deepcode.core.platform.tools

import com.deepcode.core.agent.spi.Tool
import com.deepcode.core.agent.spi.ToolContext
import com.deepcode.core.agent.spi.arg
import com.deepcode.core.agent.spi.argBool
import com.deepcode.core.model.RiskLevel
import com.deepcode.core.model.ToolCall
import com.deepcode.core.model.ToolError
import com.deepcode.core.model.ToolKind
import com.deepcode.core.model.ToolOutput
import com.deepcode.core.model.ToolResult
import com.deepcode.core.model.ToolSpec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * M0 基础工具集。
 *
 * 演示"加功能"的正确姿势：加能力 = 实现 Tool + 注册进 ToolRegistry，
 * Runtime、事件模型、UI 全部不动。
 */
class ListFilesTool : Tool {

    override val spec: ToolSpec = ToolSpec(
        name = "list_files",
        description = "列出工作区中某个目录下的文件与子目录",
        kind = ToolKind.SEARCH,
        riskLevel = RiskLevel.READ_ONLY,
        parameters = schema(
            properties = mapOf(
                "path" to "相对工作区的目录路径，默认为根目录",
                "recursive" to "是否递归，true/false",
            ),
            required = emptyList(),
        ),
    )

    override suspend fun execute(context: ToolContext, call: ToolCall): ToolResult {
        val workspace = context.workspace ?: return noWorkspace(call)
        val path = call.arg("path") ?: "."
        return runCatching {
            val entries = workspace.list(path, call.argBool("recursive"))
            ToolResult(
                callId = call.id,
                output = ToolOutput.FileList(
                    root = workspace.rootPath(),
                    entries = entries.map {
                        ToolOutput.FileList.FileEntry(it.path, it.isDirectory, it.sizeBytes)
                    },
                ),
            )
        }.getOrElse { failure(call, "list_failed", it.message ?: "列目录失败") }
    }
}

class ReadFileTool : Tool {

    override val spec: ToolSpec = ToolSpec(
        name = "read_file",
        description = "读取工作区中的文件，超出大小会自动截断",
        kind = ToolKind.READ,
        riskLevel = RiskLevel.READ_ONLY,
        parameters = schema(
            properties = mapOf("path" to "相对工作区的文件路径"),
            required = listOf("path"),
        ),
    )

    override suspend fun execute(context: ToolContext, call: ToolCall): ToolResult {
        val workspace = context.workspace ?: return noWorkspace(call)
        val path = requireNotNull(call.arg("path")) { "缺少 path 参数" }
        return runCatching {
            val read = workspace.readText(path)
            if (!workspace.exists(path)) {
                return@runCatching failure(call, "not_found", "文件不存在：$path")
            }
            ToolResult(
                callId = call.id,
                output = ToolOutput.Text(
                    text = read.content,
                    language = read.language,
                    truncated = read.truncated,
                ),
            )
        }.getOrElse { failure(call, "read_failed", it.message ?: "读取失败") }
    }
}

class WriteFileTool : Tool {

    override val spec: ToolSpec = ToolSpec(
        name = "write_file",
        description = "写入或覆盖工作区中的文件",
        kind = ToolKind.WRITE,
        riskLevel = RiskLevel.WRITE,
        parameters = schema(
            properties = mapOf(
                "path" to "相对工作区的文件路径",
                "content" to "要写入的完整内容",
            ),
            required = listOf("path", "content"),
        ),
    )

    override suspend fun execute(context: ToolContext, call: ToolCall): ToolResult {
        val workspace = context.workspace ?: return noWorkspace(call)
        val path = requireNotNull(call.arg("path")) { "缺少 path 参数" }
        val content = call.arg("content") ?: ""
        return runCatching {
            val existed = workspace.exists(path)
            val before = if (existed) workspace.readText(path, Int.MAX_VALUE).content else ""
            workspace.writeText(path, content)
            ToolResult(
                callId = call.id,
                output = ToolOutput.Diff(
                    path = path,
                    unified = simpleDiff(before, content),
                    addedLines = content.lineCount(),
                    removedLines = before.lineCount(),
                ),
            )
        }.getOrElse { failure(call, "write_failed", it.message ?: "写入失败") }
    }
}

class RunCommandTool : Tool {

    override val spec: ToolSpec = ToolSpec(
        name = "run_command",
        description = "执行一条只读命令（白名单内）",
        kind = ToolKind.EXECUTE,
        riskLevel = RiskLevel.DESTRUCTIVE,
        parameters = schema(
            properties = mapOf(
                "command" to "命令名，如 ls / cat / pwd",
                "args" to "参数，空格分隔",
            ),
            required = listOf("command"),
        ),
        streamsOutput = true,
    )

    override suspend fun execute(context: ToolContext, call: ToolCall): ToolResult {
        // 注意：这里必须提前 return，不能用 `?:` 接 failure()。
        // failure() 返回 ToolResult，与 Sandbox 无共同父类型，Elvis 会把
        // sandbox 推断成 Any，导致后续 .execute / .stdout 全部 Unresolved。
        val sandbox = context.sandbox
            ?: return failure(call, "no_sandbox", "当前没有可用的命令执行后端")
        val command = requireNotNull(call.arg("command")) { "缺少 command 参数" }
        return runCatching {
            val result = sandbox.execute(
                com.deepcode.core.agent.spi.CommandRequest(
                    command = command,
                    args = call.arg("args")?.split(" ")?.filter { it.isNotBlank() } ?: emptyList(),
                    onOutput = { chunk -> context.emitProgress(chunk) },
                    isCancelled = { context.isCancelled() },
                )
            )
            if (result.isSuccess) {
                ToolResult(call.id, ToolOutput.Text(result.stdout.ifBlank { "（无输出）" }))
            } else {
                ToolResult(
                    callId = call.id,
                    output = ToolOutput.Text(result.stderr.ifBlank { result.stdout }),
                    error = ToolError("command_failed", result.stderr.ifBlank { "命令退出码 ${result.exitCode}" }),
                )
            }
        }.getOrElse { failure(call, "command_error", it.message ?: "命令执行异常") }
    }
}

// ─────────────────────────── 辅助 ───────────────────────────

internal fun noWorkspace(call: ToolCall): ToolResult =
    failure(call, "no_workspace", "尚未打开工作区")

internal fun failure(call: ToolCall, code: String, message: String): ToolResult =
    ToolResult(callId = call.id, output = ToolOutput.Empty, error = ToolError(code, message))

internal fun schema(properties: Map<String, String>, required: List<String>): JsonObject =
    buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            properties.forEach { (name, description) ->
                putJsonObject(name) {
                    put("type", "string")
                    put("description", description)
                }
            }
        }
        putJsonArray("required") { required.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
    }

private fun String.lineCount(): Int = lineSequence().count()

/** 极简 diff：M0 先给出可读的版本，后续可替换为真正的行级 diff 算法。 */
private fun simpleDiff(before: String, after: String): String = buildString {
    if (before.isBlank()) {
        after.lineSequence().forEach { appendLine("+ $it") }
    } else {
        before.lineSequence().forEach { appendLine("- $it") }
        after.lineSequence().forEach { appendLine("+ $it") }
    }
}.trimEnd()
