package com.deepcode.core.agent.spi

import com.deepcode.core.model.WorkspaceRef

/**
 * 文件读写抽象。
 *
 * Android 上"用户代码在哪"有多种形态：App 私有目录、SAF 授权树、Git 仓库、
 * 远端 SSH。上层一律通过本接口访问，**换存储位置不影响任何业务代码**。
 *
 * 第一版实现是纯 Kotlin 的本地目录实现；要接 SAF 或 SSH，新增一个实现即可。
 */
interface Workspace {
    val ref: WorkspaceRef

    suspend fun exists(path: String): Boolean

    suspend fun readText(path: String, maxBytes: Int = DEFAULT_MAX_FILE_BYTES): FileRead

    suspend fun writeText(path: String, content: String, createParentDirs: Boolean = true)

    suspend fun delete(path: String): Boolean

    suspend fun list(path: String, recursive: Boolean = false): List<Entry>

    suspend fun stat(path: String): FileStat?

    /** 工作区根路径，用于把绝对路径裁剪成相对路径展示。 */
    suspend fun rootPath(): String

    companion object {
        /** 手机上内存金贵，默认读文件上限 256 KB，超出截断并标记。 */
        const val DEFAULT_MAX_FILE_BYTES = 256 * 1024
    }
}

data class FileRead(
    val content: String,
    val truncated: Boolean,
    val totalBytes: Long,
    /** 由扩展名推断，供 UI 做语法高亮。 */
    val language: String? = null,
)

data class Entry(
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long? = null,
    val modifiedAt: Long? = null,
)

data class FileStat(
    val path: String,
    val exists: Boolean,
    val isDirectory: Boolean,
    val sizeBytes: Long? = null,
    val modifiedAt: Long? = null,
)

// ═════════════════════════════════════════════════════════════════════════════

/**
 * 命令执行抽象。
 *
 * 这是"是否内嵌 Linux 容器"那个决策的隔离带：
 *   • [LocalSandbox]  —— 纯 Kotlin，只跑命令白名单，零依赖、秒开
 *   • ProotSandbox    —— 内嵌 Linux userland，能跑 npm/clang/git（后续加）
 *   • SshSandbox      —— 远端机器（后续加）
 *
 * 三者对上层完全同构。今天用 Local，明天换 Proot，Runtime 与 UI 一行不改。
 */
interface Sandbox {
    val id: String
    val capabilities: SandboxCapabilities

    suspend fun isReady(): Boolean

    suspend fun execute(request: CommandRequest): CommandResult
}

data class SandboxCapabilities(
    /** 是否具备通用 shell。false 时只能跑白名单命令。 */
    val hasShell: Boolean = false,
    /** 白名单命令（hasShell=false 时生效）。 */
    val allowedCommands: Set<String> = emptySet(),
    val maxTimeoutMs: Long = 120_000,
    val isRemote: Boolean = false,
    /** 是否需要预热（如 proot 解压），用于 UI 显示准备进度。 */
    val requiresBootstrap: Boolean = false,
)

data class CommandRequest(
    val command: String,
    val args: List<String> = emptyList(),
    val workingDir: String? = null,
    val env: Map<String, String> = emptyMap(),
    val timeoutMs: Long = 30_000,
    /** 实时输出回调，接到 ToolContext.emitProgress 上。 */
    val onOutput: ((chunk: String) -> Unit)? = null,
    /** 取消检查，接到 ToolContext.isCancelled 上。 */
    val isCancelled: (() -> Boolean)? = null,
)

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean = false,
    val durationMs: Long = 0,
) {
    val isSuccess: Boolean get() = exitCode == 0 && !timedOut
}
