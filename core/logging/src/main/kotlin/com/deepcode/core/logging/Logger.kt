package com.deepcode.core.logging

/**
 * 可供关键类构造注入的日志器（决策 D2：门面为主 + 关键类可选注入）。
 * 包装全局 [Log] 门面，绑定固定 tag。
 */
class Logger(private val tag: String) {
    fun v(msg: String, t: Throwable? = null) = Log.log(LogLevel.VERBOSE, LogCategory.SYSTEM_FRAMEWORK, tag, msg, t)
    fun d(msg: String, t: Throwable? = null) = Log.log(LogLevel.DEBUG, LogCategory.SYSTEM_FRAMEWORK, tag, msg, t)
    fun i(msg: String, t: Throwable? = null) = Log.log(LogLevel.INFO, LogCategory.SYSTEM_FRAMEWORK, tag, msg, t)
    fun w(msg: String, t: Throwable? = null) = Log.log(LogLevel.WARN, LogCategory.SYSTEM_FRAMEWORK, tag, msg, t)
    fun e(msg: String, t: Throwable? = null) = Log.log(LogLevel.ERROR, LogCategory.SYSTEM_FRAMEWORK, tag, msg, t)

    fun log(level: LogLevel, category: LogCategory, msg: String, t: Throwable? = null) =
        Log.log(level, category, tag, msg, t)
}
