package com.agentide.core.platform.sandbox

import com.agentide.core.agent.spi.CommandRequest
import com.agentide.core.agent.spi.CommandResult
import com.agentide.core.agent.spi.Sandbox
import com.agentide.core.agent.spi.SandboxCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 命令白名单沙箱（M0 实现）。
 *
 * 这是"不内嵌 Linux 容器"路线下的命令执行方案：
 *   • 不打包 100MB 的 userland，APK 体积小、启动秒开
 *   • 只允许跑明确列出的只读命令，风险可控
 *   • 真需要 npm/clang 时，写一个 ProotSandbox 或 SshSandbox 换上来即可，
 *     上层代码一行不改——Sandbox 接口就是为此存在的
 */
class CommandWhitelistSandbox(
    private val allowed: Set<String> = DEFAULT_COMMANDS,
    private val binDir: File = File("/system/bin"),
) : Sandbox {

    override val id: String = "local-whitelist"

    override val capabilities: SandboxCapabilities = SandboxCapabilities(
        hasShell = false,
        allowedCommands = allowed,
        maxTimeoutMs = 60_000,
        isRemote = false,
        requiresBootstrap = false,
    )

    override suspend fun isReady(): Boolean = true

    override suspend fun execute(request: CommandRequest): CommandResult = withContext(Dispatchers.IO) {
        if (request.command !in allowed) {
            return@withContext CommandResult(
                exitCode = 126,
                stdout = "",
                stderr = "命令不在白名单内：${request.command}\n可用命令：${allowed.sorted().joinToString(", ")}",
            )
        }

        val startedAt = System.currentTimeMillis()
        val executable = binDir.resolve(request.command)
        val commandLine = buildList {
            add(executable.absolutePath)
            addAll(request.args)
        }

        val process = runCatching {
            ProcessBuilder(commandLine)
                .directory(request.workingDir?.let { File(it) })
                .redirectErrorStream(true)
                .start()
        }.getOrElse {
            return@withContext CommandResult(127, "", "无法启动命令：${it.message}")
        }

        val output = StringBuilder()
        process.inputStream.bufferedReader().use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                output.appendLine(line)
                request.onOutput?.invoke(line + "\n")
                if (output.length > MAX_OUTPUT_CHARS) {
                    output.appendLine("（输出过长，已中止读取）")
                    process.destroy()
                    break
                }
                if (request.isCancelled?.invoke() == true) {
                    process.destroy()
                    break
                }
            }
        }

        val finished = runCatching {
            process.waitFor()
        }.getOrDefault(-1)

        CommandResult(
            exitCode = finished,
            stdout = output.toString(),
            stderr = "",
            timedOut = false,
            durationMs = System.currentTimeMillis() - startedAt,
        )
    }

    companion object {
        /** 只读、无副作用的命令。写操作一律不放行。 */
        val DEFAULT_COMMANDS: Set<String> = setOf(
            "ls", "cat", "echo", "pwd", "uname", "id", "stat", "df", "ps", "date", "whoami",
        )

        private const val MAX_OUTPUT_CHARS = 64 * 1024
    }
}
