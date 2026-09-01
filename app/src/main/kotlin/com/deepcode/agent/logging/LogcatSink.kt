package com.deepcode.agent.logging

import android.util.Log as AndroidLog
import com.deepcode.core.logging.LogEntry
import com.deepcode.core.logging.LogLevel
import com.deepcode.core.logging.LogSink

/**
 * logcat 输出（决策 D23）：仅 debug 构建装配，release 不装。
 *
 * 消息带分类前缀（`SECURITY.PERMISSION | ...`），方便 logcat 里一眼分辨。
 * tag 受 Android 长度限制（≤23），超长截断。
 */
class LogcatSink : LogSink {

    override fun log(entry: LogEntry) {
        val tag = if (entry.tag.length > 23) entry.tag.take(23) else entry.tag
        val msg = buildString {
            append(entry.cat.displayName)
            append(" | ")
            append(entry.msg)
            entry.ex?.let { append('\n').append(it) }
        }
        when (entry.lvl) {
            LogLevel.VERBOSE -> AndroidLog.v(tag, msg)
            LogLevel.DEBUG -> AndroidLog.d(tag, msg)
            LogLevel.INFO -> AndroidLog.i(tag, msg)
            LogLevel.WARN -> AndroidLog.w(tag, msg)
            LogLevel.ERROR -> AndroidLog.e(tag, msg)
        }
    }
}
