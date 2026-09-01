package com.deepcode.core.platform.workspace

import com.deepcode.core.agent.spi.Entry
import com.deepcode.core.agent.spi.FileRead
import com.deepcode.core.agent.spi.FileStat
import com.deepcode.core.agent.spi.Workspace
import com.deepcode.core.logging.Log
import com.deepcode.core.logging.LogCategory
import com.deepcode.core.logging.LogLevel
import com.deepcode.core.model.WorkspaceRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 本地目录工作区（M0 实现）。
 *
 * 后续要支持 SAF 授权树或远端 SSH，只需再写一个 Workspace 实现，
 * 上层（Runtime、工具、UI）一行都不用改——这就是一开始把 Workspace 做成接口的价值。
 */
class LocalDirWorkspace(
    override val ref: WorkspaceRef,
) : Workspace {

    private val root: File = when (ref) {
        is WorkspaceRef.LocalDir -> File(ref.absolutePath)
        is WorkspaceRef.GitRepo -> File(ref.absolutePath)
        else -> error("LocalDirWorkspace 不支持的引用类型：$ref")
    }.also { it.mkdirs() }

    override suspend fun exists(path: String): Boolean = withContext(Dispatchers.IO) {
        resolve(path).exists()
    }

    override suspend fun readText(path: String, maxBytes: Int): FileRead = withContext(Dispatchers.IO) {
        val file = resolve(path)
        if (!file.exists()) {
            return@withContext FileRead("", false, 0, languageOf(file.name))
        }
        val totalBytes = file.length()
        val content = if (totalBytes > maxBytes) {
            // 手机上读大文件会 OOM，宁可截断也不能崩
            file.bufferedReader().use { reader ->
                val buffer = CharArray(maxBytes)
                val read = reader.read(buffer)
                String(buffer, 0, read.coerceAtLeast(0))
            }
        } else {
            file.readText()
        }
        FileRead(
            content = content,
            truncated = totalBytes > maxBytes,
            totalBytes = totalBytes,
            language = languageOf(file.name),
        )
    }

    override suspend fun writeText(path: String, content: String, createParentDirs: Boolean) =
        withContext(Dispatchers.IO) {
            val file = resolve(path)
            if (createParentDirs) file.parentFile?.mkdirs()
            file.writeText(content)
            Log.log(
                LogLevel.INFO, LogCategory.OPERATION_DATA, "Platform",
                "写入文件 $path（${content.length} 字符）",
            )
        }

    override suspend fun delete(path: String): Boolean = withContext(Dispatchers.IO) {
        resolve(path).deleteRecursively().also {
            Log.log(LogLevel.INFO, LogCategory.OPERATION_DATA, "Platform", "删除路径 $path")
        }
    }

    override suspend fun list(path: String, recursive: Boolean): List<Entry> = withContext(Dispatchers.IO) {
        val dir = resolve(path)
        if (!dir.isDirectory) return@withContext emptyList()
        val files = if (recursive) dir.walkTopDown().toList() else (dir.listFiles()?.toList() ?: emptyList())
        files.map { file ->
            Entry(
                path = file.absolutePath,
                isDirectory = file.isDirectory,
                sizeBytes = if (file.isFile) file.length() else null,
                modifiedAt = file.lastModified(),
            )
        }
    }

    override suspend fun stat(path: String): FileStat = withContext(Dispatchers.IO) {
        val file = resolve(path)
        FileStat(
            path = file.absolutePath,
            exists = file.exists(),
            isDirectory = file.isDirectory,
            sizeBytes = if (file.isFile) file.length() else null,
            modifiedAt = if (file.exists()) file.lastModified() else null,
        )
    }

    override suspend fun rootPath(): String = root.absolutePath

    /** 路径越界防护：杜绝 Agent 被诱导读写工作区之外的文件。 */
    private fun resolve(path: String): File {
        val target = if (File(path).isAbsolute) File(path) else File(root, path)
        // canonicalPath 失败（如 I/O 权限问题）时不得回退到绝对路径继续——
        // 符号链接 + 异常回退的组合会让越界检查被绕过，这里直接拒绝。
        val canonical = target.canonicalPath
        val canonicalRoot = root.canonicalPath
        // 边界判断用「root 本身或其子路径」，避免 /foo/default 匹配上 /foo/default-evil。
        val inside = canonical == canonicalRoot ||
            canonical.startsWith(canonicalRoot + File.separator)
        if (!inside) {
            Log.log(
                LogLevel.WARN, LogCategory.SECURITY_INTEGRITY, "Platform",
                "拒绝访问工作区之外的路径：$path",
            )
            throw SecurityException("拒绝访问工作区之外的路径：$path")
        }
        // 返回规范化后的 File，而不是原始 target：后续所有读写都基于同一个
        // 已通过校验的路径，防止符号链接在检查后被二次解析到工作区外。
        return File(canonical)
    }

    private fun languageOf(name: String): String? = when (name.substringAfterLast('.', "")) {
        "kt", "kts" -> "kotlin"
        "java" -> "java"
        "py" -> "python"
        "js", "ts", "tsx", "jsx" -> "javascript"
        "json" -> "json"
        "md" -> "markdown"
        "sh" -> "shell"
        "gradle" -> "groovy"
        "xml" -> "xml"
        "yml", "yaml" -> "yaml"
        else -> null
    }
}
