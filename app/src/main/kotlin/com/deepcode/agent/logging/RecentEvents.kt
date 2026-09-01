package com.deepcode.agent.logging

import com.deepcode.core.data.db.SqliteDatabase

/**
 * 导出包第 4 层：最近 200 条 Agent 事件（决策 D14）。
 *
 * 只取元数据（时间 / 事件类型 / 会话），**不取事件正文**——对话内容、
 * 工具入参等一律不导出，满足"正文脱敏（用元数据代替）"。
 */
suspend fun recentEventLines(db: SqliteDatabase): List<String> = db.read {
    db.rawQuery(
        sql = "SELECT ts, type, session_id FROM events ORDER BY seq DESC LIMIT 200",
    ) { cursor ->
        val ts = cursor.getLong(0) ?: 0L
        val type = cursor.getString(1) ?: "?"
        val session = cursor.getString(2) ?: "?"
        val time = java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date(ts))
        "$time  $type  session=$session"
    }.asReversed()
}
