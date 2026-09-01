package com.deepcode.agent.logging

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.deepcode.agent.BuildConfig
import com.deepcode.core.logging.Log
import com.deepcode.core.logging.LogEntry
import com.deepcode.core.logging.Redactor
import com.deepcode.core.mcp.McpServerConfig
import com.deepcode.core.mcp.McpTransport
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 日志导出（决策 D13–D15 / D25）。
 *
 * 四层导出包 → zip → share：
 *   1. 崩溃栈（最近一次崩溃现场，人类可读）
 *   2. 崩溃前上下文（RingBuffer 200 条 + app.log 尾部）
 *   3. 环境信息（设备 / App / 已登记模块 / MCP server 列表，URL 脱敏）
 *   4. 最近事件流（数据层最近 200 条，正文脱敏，由装配层注入取数函数）
 *
 * 另提供"立即同步到根目录"（D11，转调 [RollingFileSink.syncToRoot]）。
 */
class LogExporter(
    private val context: Context,
    private val rollingSink: RollingFileSink,
    private val mcpConfigs: () -> List<McpServerConfig>,
    private val eventLines: suspend () -> List<String>,
) {
    private val redactor = Redactor()

    /** 导出四层包并调起系统分享。 */
    suspend fun exportAndShare() {
        val zip = buildExportPackage()
        share(zip)
    }

    /** 授权后立即把私有日志补齐到根目录（决策 D11）。 */
    fun syncToRoot() = rollingSink.syncToRoot(context)

    private suspend fun buildExportPackage(): File {
        val logDir = LogPaths.privateLogDir(context)
        val exportDir = File(context.cacheDir, "logs").apply { mkdirs() }
        val zip = File(exportDir, "deepcore-logs-${System.currentTimeMillis()}.zip")
        ZipOutputStream(zip.outputStream().buffered()).use { zos ->
            fun addText(name: String, text: String) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(text.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            addText("00_崩溃栈.txt", layerStack(logDir))
            addText("01_上下文.txt", layerContext(logDir))
            addText("02_环境信息.txt", layerEnvironment())
            addText("03_事件流.txt", layerEvents())
            logDir.listFiles()?.sortedBy { it.name }?.forEach { f ->
                if (f.isFile) {
                    zos.putNextEntry(ZipEntry("logs/${f.name}"))
                    f.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
        return zip
    }

    private fun layerStack(logDir: File): String {
        val pending = CrashVault.lastCrashFile(context) ?: return "（无崩溃记录）\n"
        val f = File(logDir, pending)
        return if (f.exists()) f.readText() else "（崩溃文件缺失：$pending）\n"
    }

    private fun layerContext(logDir: File): String = buildString {
        val ring = Log.dumpRing()
        appendLine("内存环形缓冲（${ring.size} 条）")
        ring.forEach { appendLine(formatEntry(it)) }
        appendLine()
        appendLine("文件日志尾部（app.log 最近 200 行）：")
        val appLog = File(logDir, "app.log")
        if (appLog.exists()) {
            appLog.readLines().takeLast(200).forEach { appendLine(it) }
        } else {
            appendLine("(无文件日志)")
        }
    }

    private fun layerEnvironment(): String = buildString {
        appendLine("设备：${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        appendLine("Android：API ${android.os.Build.VERSION.SDK_INT}（${android.os.Build.VERSION.RELEASE}）")
        appendLine("App 版本：${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）")
        appendLine("构建类型：${BuildConfig.BUILD_TYPE}")
        appendLine()
        appendLine("已登记模块：")
        Log.modules.all().forEach { (m, p) -> appendLine("  $m → $p") }
        appendLine()
        appendLine("MCP Server（URL 已脱敏）：")
        mcpConfigs().forEach { c ->
            val url = when (val t = c.transport) {
                is McpTransport.Http -> t.url
                else -> "(未知传输)"
            }
            appendLine("  ${c.id}（${c.displayName}）trusted=${c.trusted} url=${redactor.redact(url)}")
        }
    }

    private suspend fun layerEvents(): String {
        val lines = eventLines()
        return if (lines.isEmpty()) "（无事件记录）\n" else lines.joinToString("\n") + "\n"
    }

    private fun formatEntry(entry: LogEntry): String =
        "${entry.ts} ${entry.lvl} ${entry.cat.displayName} [${entry.tag}] ${entry.msg}" +
            (entry.ex?.let { "\n  $it" } ?: "")

    private fun share(zip: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zip)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "导出崩溃日志").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
