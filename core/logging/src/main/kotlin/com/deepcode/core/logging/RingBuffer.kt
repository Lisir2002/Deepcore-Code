package com.deepcode.core.logging

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 内存环形缓冲（崩溃前上下文，默认 200 条，决策 D25）。
 * 线程安全；崩溃时 dump 出最近 [capacity] 条日志。
 */
class RingBuffer(
    private val capacity: Int = 200,
) {
    private val lock = ReentrantLock()
    private val entries = ArrayDeque<LogEntry>()

    fun append(entry: LogEntry) = lock.withLock {
        if (entries.size >= capacity) entries.removeFirst()
        entries.addLast(entry)
    }

    /** 按时间序返回全部缓冲条目。 */
    fun dump(): List<LogEntry> = lock.withLock { entries.toList() }

    fun clear() = lock.withLock { entries.clear() }

    val size: Int get() = lock.withLock { entries.size }
}
