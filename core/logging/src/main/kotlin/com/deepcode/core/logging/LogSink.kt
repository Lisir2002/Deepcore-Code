package com.deepcode.core.logging

/**
 * 输出目标（可插拔）。未来接远程上报 = 新增一个 Sink 并 plant。
 */
interface LogSink {
    /** 接收一条日志。实现必须线程安全，不得抛出未捕获异常。 */
    fun log(entry: LogEntry)

    /** 冲刷缓冲（崩溃时调用）。 */
    fun flush() {}
}
