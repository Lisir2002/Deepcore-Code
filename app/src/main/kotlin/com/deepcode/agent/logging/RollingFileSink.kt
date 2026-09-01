package com.deepcode.agent.logging

import android.content.Context
import com.deepcode.core.logging.LogEntry
import com.deepcode.core.logging.LogGroup
import com.deepcode.core.logging.LogSink
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter

/**
 * 文件日志输出（决策 D6 / D8–D12 / D21 / D25）。
 *
 * - 私有目录 `filesDir/logs` + 公共根目录 `/sdcard/deepcorefile/logs` **实时双写**
 * - 主文件 `app.log`（JSON 行，全量）+ 危险镜像 `danger.log`（仅 SECURITY 类，D6）
 * - 滚动策略 `1MB × 5`：`app.log` → `app.log.1` … `app.log.5`（D21）
 * - 根目录顶层 `README.txt` 说明用途（D8）
 * - 未授权根目录权限 → 跳过根目录只写私有，日志不丢（D10 降级）
 *
 * 线程安全：所有写入在 [lock] 内串行；[flush] 供崩溃前冲刷。
 */
class RollingFileSink(
    context: Context,
    private val json: Json = Json { encodeDefaults = true },
    private val maxFileBytes: Long = MAX_FILE_BYTES,
    private val maxBackup: Int = MAX_BACKUP,
) : LogSink {

    private val appContext = context.applicationContext
    private val privateDir = LogPaths.privateLogDir(appContext).apply { mkdirs() }

    private val lock = Any()
    private val files = LinkedHashMap<String, LogFile>()

    init {
        if (StoragePermission.canWriteRoot(appContext)) {
            writeReadmeLocked()
        }
    }

    override fun log(entry: LogEntry) {
        val line = json.encodeToString(LogEntry.serializer(), entry) + "\n"
        synchronized(lock) {
            logFile(privateDir, APP_LOG).append(line)
            if (entry.cat.group == LogGroup.SECURITY) {
                logFile(privateDir, DANGER_LOG).append(line)
            }
            if (StoragePermission.canWriteRoot(appContext)) {
                logFile(LogPaths.rootLogDir(), APP_LOG).append(line)
                if (entry.cat.group == LogGroup.SECURITY) {
                    logFile(LogPaths.rootLogDir(), DANGER_LOG).append(line)
                }
            }
        }
    }

    override fun flush() {
        synchronized(lock) {
            files.values.forEach { it.flush() }
        }
    }

    fun close() {
        synchronized(lock) {
            files.values.forEach { it.close() }
            files.clear()
        }
    }

    /** 授权后手动把私有日志补齐同步到根目录（决策 D11）。 */
    fun syncToRoot(context: Context) {
        val appCtx = context.applicationContext
        if (!StoragePermission.canWriteRoot(appCtx)) return
        synchronized(lock) {
            try {
                val rootLogs = LogPaths.rootLogDir()
                rootLogs.mkdirs()
                writeReadmeLocked()
                privateDir.listFiles()?.forEach { src ->
                    if (src.isFile) src.copyTo(File(rootLogs, src.name), overwrite = true)
                }
            } catch (_: Exception) {
                // 同步失败不影响业务；下次授权后仍可重试
            }
        }
    }

    private fun writeReadmeLocked() {
        try {
            val base = LogPaths.rootBaseDir()
            base.mkdirs()
            LogPaths.rootReadme().writeText(README_CONTENT)
        } catch (_: Exception) {
        }
    }

    private fun logFile(dir: File, name: String): LogFile =
        files.getOrPut("${dir.absolutePath}/$name") {
            LogFile(dir, name, maxFileBytes, maxBackup)
        }

    private companion object {
        const val APP_LOG = "app.log"
        const val DANGER_LOG = "danger.log"
        const val MAX_FILE_BYTES = 1L * 1024 * 1024
        const val MAX_BACKUP = 5

        val README_CONTENT = """
            DeepCore-Code 日志目录
            ========================

            本目录由 DeepCore-Code 自动导出，用于调试与问题反馈。

            目录结构：
              logs/app.log                   全部运行日志（JSON 行，1MB × 5 滚动）
              logs/danger.log                危险/安全事件镜像（权限、越界、崩溃等）
              logs/crash-*.log               崩溃现场（人类可读）
              logs/crash-*-context.txt       崩溃前上下文（最近日志 + 事件流）

            反馈时请把整个 logs 目录打包发送给开发者，并附上复现步骤。
        """.trimIndent() + "\n"
    }

    /** 单个文件的追加 + 滚动。 */
    private class LogFile(
        val dir: File,
        val name: String,
        private val maxBytes: Long,
        private val maxBackup: Int,
    ) {
        private var pw: PrintWriter? = null
        private var size: Long = 0

        fun append(line: String) {
            ensureOpen()
            val bytes = line.toByteArray(Charsets.UTF_8).size
            if (size + bytes > maxBytes) {
                pw?.close()
                pw = null
                roll()
                ensureOpen()
                size = 0
            }
            pw!!.append(line)
            pw!!.flush()
            size += bytes
        }

        fun flush() {
            pw?.flush()
        }

        fun close() {
            pw?.close()
            pw = null
        }

        private fun ensureOpen() {
            if (pw == null) {
                dir.mkdirs()
                val f = File(dir, name)
                pw = PrintWriter(FileWriter(f, true))
                size = f.length()
            }
        }

        private fun roll() {
            File(dir, "$name.$maxBackup").delete()
            for (i in maxBackup - 1 downTo 1) {
                val from = File(dir, "$name.$i")
                if (from.exists()) from.renameTo(File(dir, "$name.${i + 1}"))
            }
            val cur = File(dir, name)
            if (cur.exists()) cur.renameTo(File(dir, "$name.1"))
        }
    }
}
