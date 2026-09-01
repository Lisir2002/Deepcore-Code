package com.deepcode.core.logging

import kotlinx.serialization.Serializable

/**
 * 结构化日志条目，序列化为 JSON 行（docs/LOGGING_SYSTEM_DESIGN.md §11）。
 *
 * @param ts ISO-8601 时间戳（UTC）
 * @param lvl 级别
 * @param cat 分类（大类.子类）
 * @param tag 模块登记前缀（ModuleRegistry）
 * @param msg 消息（已脱敏）
 * @param thr 线程名
 * @param ex 异常堆栈（已脱敏），可选
 */
@Serializable
data class LogEntry(
    val ts: String,
    val lvl: LogLevel,
    val cat: LogCategory,
    val tag: String,
    val msg: String,
    val thr: String = "",
    val ex: String? = null,
) {
    val categoryKey: String get() = cat.name
}
