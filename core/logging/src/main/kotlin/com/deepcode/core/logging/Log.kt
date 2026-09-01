package com.deepcode.core.logging

import kotlinx.datetime.Clock
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 全局日志门面（决策 D1–D3）。
 *
 * - 任何模块 `Log.i("Tag", "...")` 直接调用。
 * - 输出目标 = plant 的 [LogSink] 链（app 层装配）。
 * - 写入前统一过 [Redactor] 脱敏。
 * - 线程安全；sink 异常被吞掉，不影响业务。
 */
object Log {
    @Volatile
    private var minLevel: LogLevel = LogLevel.VERBOSE

    private val sinks = CopyOnWriteArrayList<LogSink>()
    private val registry = ModuleRegistry()
    private val redactor = Redactor()
    private val ring = RingBuffer()

    /** 装配输出目标（可重复调用，多个 sink 并存）。 */
    fun plant(sink: LogSink) {
        sinks.add(sink)
    }

    /** 设置全局最低级别；低于该级别不派发（logcat 常用）。 */
    fun setMinLevel(level: LogLevel) {
        minLevel = level
    }

    /** 模块登记表（集中登记，见 ModuleRegistry）。 */
    val modules: ModuleRegistry get() = registry

    /** 崩溃时读取内存环形缓冲（最近 200 条）。 */
    fun dumpRing(): List<LogEntry> = ring.dump()

    fun v(tag: String, msg: String, t: Throwable? = null) = log(LogLevel.VERBOSE, LogCategory.SYSTEM_FRAMEWORK, tag, msg, t)
    fun d(tag: String, msg: String, t: Throwable? = null) = log(LogLevel.DEBUG, LogCategory.SYSTEM_FRAMEWORK, tag, msg, t)
    fun i(tag: String, msg: String, t: Throwable? = null) = log(LogLevel.INFO, LogCategory.SYSTEM_FRAMEWORK, tag, msg, t)
    fun w(tag: String, msg: String, t: Throwable? = null) = log(LogLevel.WARN, LogCategory.SYSTEM_FRAMEWORK, tag, msg, t)
    fun e(tag: String, msg: String, t: Throwable? = null) = log(LogLevel.ERROR, LogCategory.SYSTEM_FRAMEWORK, tag, msg, t)

    /** 带分类的日志入口。 */
    fun log(level: LogLevel, category: LogCategory, tag: String, msg: String, t: Throwable? = null) {
        if (level.rank < minLevel.rank) return
        val entry = LogEntry(
            ts = Clock.System.now().toString(),
            lvl = level,
            cat = category,
            tag = tag,
            msg = redactor.redact(msg) ?: "",
            thr = Thread.currentThread().name,
            ex = t?.stackTraceToString()?.let(redactor::redact),
        )
        ring.append(entry)
        for (sink in sinks) {
            try {
                sink.log(entry)
            } catch (_: Exception) {
                // sink 异常不影响业务与其它 sink
            }
        }
    }

    /** 冲刷所有 sink（崩溃前调用，尽力而为）。 */
    fun flush() {
        for (sink in sinks) {
            try {
                sink.flush()
            } catch (_: Exception) {
            }
        }
    }

    /** 仅供测试：清空装配状态。 */
    internal fun resetForTest() {
        sinks.clear()
        minLevel = LogLevel.VERBOSE
        ring.clear()
    }
}
