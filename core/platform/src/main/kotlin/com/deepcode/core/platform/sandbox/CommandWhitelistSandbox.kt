package com.deepcode.core.platform.sandbox

import com.deepcode.core.agent.spi.CommandRequest
import com.deepcode.core.agent.spi.CommandResult
import com.deepcode.core.agent.spi.Sandbox
import com.deepcode.core.agent.spi.SandboxCapabilities
import com.deepcode.core.logging.Log
import com.deepcode.core.logging.LogCategory
import com.deepcode.core.logging.LogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

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
            Log.log(
                LogLevel.WARN, LogCategory.SECURITY_PERMISSION, "Platform",
                "命令 ${request.command} 不在白名单内，已拒绝",
            )
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
            Log.log(
                LogLevel.ERROR, LogCategory.OPERATION_SANDBOX, "Platform",
                "无法启动命令 ${request.command}：${it.message}",
            )
            return@withContext CommandResult(127, "", "无法启动命令：${it.message}")
        }

        val maxTimeoutMs = capabilities.maxTimeoutMs
        val output = StringBuilder()
        var timedOut = false

        // 输出由一个后台线程持续读取；主线程用 process.waitFor(timeout) 施加**真正的超时**。
        // 这是刻意这样做的：若在"读一行检查一次耗时"里判断超时，readLine() 本身一旦阻塞
        // 就永远回不来，超时形同虚设（这正是旧实现的缺陷）。
        val reader = Thread {
            process.inputStream.bufferedReader().use { reader ->
                while (true) {
                    val line = if (Thread.currentThread().isInterrupted) null else reader.readLine()
                        ?: break
                    synchronized(output) { output.appendLine(line) }
                    request.onOutput?.invoke(line + "\n")
                    val tooLong = synchronized(output) { output.length > MAX_OUTPUT_CHARS }
                    if (tooLong) {
                        synchronized(output) { output.appendLine("（输出过长，已中止读取）") }
                        process.destroy()
                        break
                    }
                    if (request.isCancelled?.invoke() == true) {
                        process.destroy()
                        break
                    }
                }
            }
        }.apply {
            isDaemon = true
            start()
        }

        // 阻塞等待进程结束；超过 maxTimeoutMs 仍未退出则判定超时并杀掉。
        val exited = process.waitFor(maxTimeoutMs, TimeUnit.MILLISECONDS)
        if (!exited) {
            timedOut = true
            process.destroy()
            // 不给面子就先温和终止，5s 后仍不退就强杀，确保线程不泄漏。
            if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly()
        }
        // 要么进程已自然退出（流读到 EOF），要么已被销毁（流关闭），join 都能返回。
        reader.join()

        val exitCode = if (timedOut) {
            Log.log(
                LogLevel.WARN, LogCategory.OPERATION_SANDBOX, "Platform",
                "命令 ${request.command} 超过 ${maxTimeoutMs}ms 超时，已终止",
            )
            124 // 超时标准退出码
        } else {
            runCatching { process.exitValue() }.getOrDefault(-1)
        }

        Log.log(
            LogLevel.INFO, LogCategory.OPERATION_SANDBOX, "Platform",
            "命令 ${request.command} 退出码 $exitCode，耗时 ${System.currentTimeMillis() - startedAt}ms",
        )

        CommandResult(
            exitCode = exitCode,
            stdout = output.toString(),
            stderr = "",
            timedOut = timedOut,
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
