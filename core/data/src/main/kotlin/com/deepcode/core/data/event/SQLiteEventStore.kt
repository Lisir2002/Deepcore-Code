package com.deepcode.core.data.event

import com.deepcode.core.data.EventStore
import com.deepcode.core.data.SessionIndex
import com.deepcode.core.data.db.SqliteDatabase
import com.deepcode.core.logging.Log
import com.deepcode.core.logging.LogCategory
import com.deepcode.core.logging.LogLevel
import com.deepcode.core.model.AgentEvent
import com.deepcode.core.model.SessionId
import com.deepcode.core.model.TurnStarted
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * 事件日志的 SQLite 实现（schema v1）。
 *
 * 与 [com.deepcode.core.data.InMemoryEventStore] 行为对齐，可原地替换：
 *   • [append] 内部把"写 events 行 + 维护 sessions 索引"放进**同一个事务**
 *   • [observe] 只推送订阅之后新增的事件（历史请用 [loadEvents] 回放）
 *
 * 表结构见 `Events.sq`，设计决策见 DATA_LAYER.md。
 */
class SQLiteEventStore(
    private val db: SqliteDatabase,
    private val codec: EventCodec = EventCodec,
) : EventStore {

    override suspend fun append(event: AgentEvent) = appendAll(listOf(event))

    override suspend fun appendAll(events: List<AgentEvent>) {
        if (events.isEmpty()) return
        val startedAt = System.currentTimeMillis()
        db.transaction {
            val eventsQueries = db.database.eventsQueries
            val sessionsQueries = db.database.sessionsQueries
            events.forEach { event ->
                val encoded = codec.encode(event)
                eventsQueries.insertEvent(
                    session_id = event.sessionId.value,
                    turn_id = event.turnId.value,
                    ts = event.ts,
                    type = encoded.type,
                    payload = encoded.payload,
                )
                // 会话索引与事件流同事务提交：两者永不脱节（DATA_LAYER.md 决策 #5）
                sessionsQueries.insertSession(
                    id = event.sessionId.value,
                    title = titleOf(event),
                    created_at = event.ts,
                    updated_at = event.ts,
                )
                sessionsQueries.touchSession(updated_at = event.ts, id = event.sessionId.value)
            }
        }
        Log.log(
            LogLevel.DEBUG, LogCategory.OPERATION_DATA, "DataStore",
            "写 events 表 ${events.size} 行，耗时 ${System.currentTimeMillis() - startedAt}ms",
        )
    }

    override suspend fun loadEvents(sessionId: SessionId): List<AgentEvent> = db.read {
        db.database.eventsQueries
            .eventsForSession(sessionId.value)
            .executeAsList()
            .map { row -> codec.decode(row.payload) }
    }.also { Log.log(LogLevel.DEBUG, LogCategory.OPERATION_DATA, "DataStore", "读 events 表 ${it.size} 行（session ${sessionId.value}）") }

    override fun observe(sessionId: SessionId): Flow<AgentEvent> = flow {
        var cursor = NO_BASELINE
        db.observe(db.database.eventsQueries.eventsForSession(sessionId.value))
            .map { rows -> rows.map { row -> row.seq to row.payload } }
            .collect { all ->
                if (cursor == NO_BASELINE) {
                    // 首帧只建立基线，不回放历史：与 InMemoryEventStore 语义一致，
                    // 历史由调用方显式 loadEvents 决定要不要重放。
                    cursor = all.lastOrNull()?.first ?: 0L
                    return@collect
                }
                // seq 全局单调递增（AUTOINCREMENT），游标即"已推送到哪里"
                val fresh = all.filter { (seq, _) -> seq > cursor }
                if (fresh.isNotEmpty()) cursor = fresh.maxOf { it.first }
                fresh.forEach { (_, payload) -> emit(codec.decode(payload)) }
            }
    }

    override suspend fun clear(sessionId: SessionId) = db.transaction {
        db.database.eventsQueries.deleteEventsForSession(sessionId.value)
        db.database.sessionsQueries.deleteSession(sessionId.value)
    }

    // ─────────────── 会话索引（M1 会话列表页前置能力） ───────────────

    /** 观测全部会话，按最近更新排序；不重放事件流。 */
    fun observeSessions(): Flow<List<SessionIndex>> =
        db.observe(db.database.sessionsQueries.allSessions())
            .map { rows ->
                rows.map { row ->
                    SessionIndex(
                        id = SessionId(row.id),
                        title = row.title,
                        createdAt = row.created_at,
                        updatedAt = row.updated_at,
                    )
                }
            }

    suspend fun renameSession(
        sessionId: SessionId,
        title: String,
        at: Long = System.currentTimeMillis(),
    ) = db.transaction {
        db.database.sessionsQueries.renameSession(title = title, updated_at = at, id = sessionId.value)
    }

    private fun titleOf(event: AgentEvent): String = when (event) {
        is TurnStarted ->
            event.userInput.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(TITLE_MAX) ?: ""
        else -> ""
    }

    private companion object {
        const val TITLE_MAX = 60
        const val NO_BASELINE = -1L
    }
}
