package com.deepcode.core.logging

/**
 * 日志级别：严重程度维度（与 [LogCategory] 业务维度正交）。
 */
enum class LogLevel(val rank: Int) {
    VERBOSE(0),
    DEBUG(1),
    INFO(2),
    WARN(3),
    ERROR(4);

    fun isAtLeast(other: LogLevel): Boolean = rank >= other.rank
}
