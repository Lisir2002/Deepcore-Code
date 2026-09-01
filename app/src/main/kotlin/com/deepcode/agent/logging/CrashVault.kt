package com.deepcode.agent.logging

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import com.deepcode.core.logging.Log
import com.deepcode.core.logging.LogCategory
import com.deepcode.core.logging.LogEntry
import com.deepcode.core.logging.LogLevel
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 崩溃捕获 + ANR watchdog + 崩溃标记（决策 D13 / D24 / D25）。
 *
 * 必须在 [Application.onCreate] **第一行**调用 [install]（super 之前），
 * 确保签名校验等启动早期崩溃也能留下现场。
 *
 * 捕获动作：
 *   · 写 `crash-<ts>.log`（人类可读：异常类型 + 完整堆栈 + 线程）
 *   · 写 `crash-<ts>-context.txt`（RingBuffer 200 条 + app.log 尾部）
 *   · 记崩溃标记（SharedPreferences，供下次启动弹窗，D13）
 *   · 进门面记 SECURITY.CRASH_CAUSE（D7）并 flush sinks
 *   · 调用原 handler（默认终止进程）
 *
 * ANR watchdog（D24，一期轻量）：后台线程每秒检查主线程心跳，
 * 主线程阻塞超过 [ANR_TIMEOUT_MS] 即 dump 主线程堆栈。
 */
class CrashVault private constructor(
    private val appContext: Context,
    private val prefs: SharedPreferences,
) {

    private var previousHandler: Thread.UncaughtExceptionHandler? = null
    private val anrWatchdog = AnrWatchdog(timeoutMs = ANR_TIMEOUT_MS) { elapsed -> onAnr(elapsed) }

    private val crashDir: File get() = LogPaths.privateLogDir(appContext)

    companion object {
        private const val PREFS = "deepcore_crash"
        private const val KEY_PENDING = "pending_crash_file"
        private const val ANR_TIMEOUT_MS = 5_000L
        private const val RING_TAIL_LINES = 200

        @Volatile
        private var instance: CrashVault? = null

        /** 安装崩溃捕获。重复调用幂等。 */
        fun install(context: Context): CrashVault {
            instance?.let { return it }
            val appCtx = context.applicationContext
            val vault = CrashVault(
                appContext = appCtx,
                prefs = appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
            )
            instance = vault
            return vault.start()
        }

        /** 是否有未确认的崩溃标记（下次启动弹窗用，D13）。 */
        fun hasPendingCrash(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_PENDING, null) != null

        /** 最近一次崩溃文件相对名（弹窗展示用）。 */
        fun lastCrashFile(context: Context): String? =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_PENDING, null)

        /** 消费崩溃标记（弹窗处理后调用）。 */
        @SuppressLint("ApplySharedPref")
        fun consumePendingCrash(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY_PENDING).apply()
        }
    }

    fun start(): CrashVault {
        val current = Thread.getDefaultUncaughtExceptionHandler()
        if (current is CrashHandler) return this // 幂等
        previousHandler = current
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler())
        anrWatchdog.start()
        return this
    }

    // ─────────────── 崩溃捕获 ───────────────

    private inner class CrashHandler : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            try {
                capture(thread, throwable)
            } finally {
                anrWatchdog.stop()
                previousHandler?.uncaughtException(thread, throwable)
                    ?: Process.killProcess(Process.myPid())
            }
        }
    }

    @SuppressLint("ApplySharedPref")
    private fun capture(thread: Thread, throwable: Throwable) {
        val ts = timestamp()
        val crashFile = File(crashDir, "crash-$ts.log")
        val contextFile = File(crashDir, "crash-$ts-context.txt")
        try {
            crashFile.writeText(buildCrashReport(thread, throwable))
            contextFile.writeText(buildContextReport())
        } catch (_: Exception) {
            // 写崩溃文件失败不阻塞后续，尽力而为
        }
        // 进门面（SECURITY.CRASH_CAUSE → 镜像进 danger.log）并冲刷 sinks
        Log.log(
            LogLevel.ERROR,
            LogCategory.SECURITY_CRASH_CAUSE,
            "CrashVault",
            "崩溃：${throwable.javaClass.simpleName}: ${throwable.message}",
            throwable,
        )
        Log.flush()
        // 标记必须同步落盘：进程即将终止
        prefs.edit().putString(KEY_PENDING, crashFile.name).commit()
    }

    private fun buildCrashReport(thread: Thread, throwable: Throwable): String = buildString {
        appendLine("DeepCore-Code 崩溃现场")
        appendLine("时间：${timestamp()}")
        appendLine("线程：${thread.name}")
        appendLine("异常：${throwable.javaClass.name}: ${throwable.message}")
        appendLine("----------------------------------------")
        appendLine("堆栈：")
        appendLine(throwable.stackTraceToString())
        appendLine("----------------------------------------")
        appendLine("设备：${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        appendLine("Android：API ${android.os.Build.VERSION.SDK_INT}（${android.os.Build.VERSION.RELEASE}）")
        appendLine("App：${com.deepcode.agent.BuildConfig.VERSION_NAME}（${com.deepcode.agent.BuildConfig.VERSION_CODE}）/${com.deepcode.agent.BuildConfig.BUILD_TYPE}")
    }

    private fun buildContextReport(): String = buildString {
        val ring = Log.dumpRing()
        appendLine("崩溃前上下文（RingBuffer ${ring.size} 条）")
        appendLine("----------------------------------------")
        ring.forEach { appendLine(formatEntry(it)) }
        appendLine("----------------------------------------")
        appendLine("文件日志尾部（app.log 最近 $RING_TAIL_LINES 行）：")
        append(tailOf(File(crashDir, "app.log")))
    }

    private fun formatEntry(entry: LogEntry): String =
        "${entry.ts} ${entry.lvl} ${entry.cat.displayName} [${entry.tag}] ${entry.msg}" +
            (entry.ex?.let { "\n  $it" } ?: "")

    private fun tailOf(file: File, lines: Int = RING_TAIL_LINES): String {
        if (!file.exists()) return "(无文件日志)\n"
        return try {
            file.readLines().takeLast(lines).joinToString("\n") + "\n"
        } catch (_: Exception) {
            "(读取失败)\n"
        }
    }

    // ─────────────── ANR watchdog ───────────────

    private fun onAnr(elapsed: Long) {
        val ts = timestamp()
        val mainThread = Looper.getMainLooper().thread
        val anrFile = File(crashDir, "crash-$ts-anr.log")
        try {
            anrFile.writeText(buildAnrReport(elapsed, mainThread))
        } catch (_: Exception) {
        }
        Log.log(
            LogLevel.ERROR,
            LogCategory.SECURITY_CRASH_CAUSE,
            "CrashVault",
            "ANR 检测：主线程阻塞 ${elapsed}ms",
            null,
        )
        Log.flush()
    }

    private fun buildAnrReport(elapsed: Long, thread: Thread): String = buildString {
        appendLine("ANR 检测（主线程阻塞 ${elapsed}ms）")
        appendLine("时间：${timestamp()}")
        appendLine("----------------------------------------")
        appendLine("主线程堆栈：")
        thread.stackTrace.forEach { appendLine("\tat $it") }
    }

    private fun timestamp(): String =
        java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())

    /**
     * 轻量主线程心跳 watchdog：后台线程每秒检查一次心跳时间戳。
     * 主线程被阻塞时心跳不会更新，超时即触发 [onAnr]。
     */
    private class AnrWatchdog(
        private val timeoutMs: Long,
        private val onAnr: (Long) -> Unit,
    ) {
        private val mainHandler = android.os.Handler(Looper.getMainLooper())
        private val worker = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "anr-watchdog").apply { isDaemon = true }
        }
        @Volatile private var lastBeat = SystemClock.uptimeMillis()
        @Volatile private var started = false

        private val heartbeat = object : Runnable {
            override fun run() {
                lastBeat = SystemClock.uptimeMillis()
                if (started) mainHandler.postDelayed(this, POLL_MS)
            }
        }

        private val checker = object : Runnable {
            override fun run() {
                val elapsed = SystemClock.uptimeMillis() - lastBeat
                if (elapsed >= timeoutMs) onAnr(elapsed)
                if (started) worker.schedule(this, POLL_MS, TimeUnit.MILLISECONDS)
            }
        }

        fun start() {
            if (started) return
            started = true
            mainHandler.post(heartbeat)
            worker.schedule(checker, POLL_MS, TimeUnit.MILLISECONDS)
        }

        fun stop() {
            started = false
            mainHandler.removeCallbacks(heartbeat)
            worker.shutdownNow()
        }

        private companion object {
            const val POLL_MS = 1_000L
        }
    }
}
