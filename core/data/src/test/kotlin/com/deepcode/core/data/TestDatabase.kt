package com.deepcode.core.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.deepcode.core.data.db.SqliteDatabase
import com.deepcode.core.data.db.TableModule
import com.deepcode.core.data.db.createSqliteDatabase
import com.deepcode.core.model.AgentEvent
import com.deepcode.core.model.EventId
import com.deepcode.core.model.MessageDelta
import com.deepcode.core.model.SessionId
import com.deepcode.core.model.StopReason
import com.deepcode.core.model.ToolCallId
import com.deepcode.core.model.ToolOutput
import com.deepcode.core.model.ToolResult
import com.deepcode.core.model.ToolCallSucceeded
import com.deepcode.core.model.TurnCompleted
import com.deepcode.core.model.TurnId
import com.deepcode.core.model.TurnStarted
import com.deepcode.core.model.Usage
import kotlinx.coroutines.Dispatchers

/**
 * 测试基建：JVM 内存库。
 *
 * 数据层选 SQLDelight 的核心理由就在这——建表、迁移链、DAO 全都能在 JVM 上秒级跑完，
 * CI 不需要模拟器。IO 上下文用 Unconfined 让 Flow 与事务在测试中完全同步可断言。
 */
internal fun testDatabase(
    driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY),
    modules: List<TableModule> = emptyList(),
): SqliteDatabase = createSqliteDatabase(driver, modules, io = Dispatchers.Unconfined)

internal fun SqlDriver.userVersion(): Long = executeQuery(
    identifier = null,
    sql = "PRAGMA user_version",
    mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L) },
    parameters = 0,
).value

internal fun SqlDriver.tableNames(): Set<String> = executeQuery(
    identifier = null,
    sql = "SELECT name FROM sqlite_master WHERE type = 'table'",
    mapper = { cursor ->
        QueryResult.Value(buildSet { while (cursor.next().value) add(cursor.getString(0)!!) })
    },
    parameters = 0,
).value

// ─────────────────────────── 事件工厂 ───────────────────────────

internal fun turnStarted(
    sessionId: String = "s1",
    turnId: String = "t1",
    ts: Long = 1L,
    input: String = "帮我看下这个项目",
): AgentEvent = TurnStarted(
    id = EventId("evt-$sessionId-$turnId-$ts"),
    sessionId = SessionId(sessionId),
    turnId = TurnId(turnId),
    ts = ts,
    userInput = input,
)

internal fun messageDelta(
    sessionId: String = "s1",
    turnId: String = "t1",
    ts: Long = 2L,
    seq: Long = 0,
    text: String = "好的",
): AgentEvent = MessageDelta(
    id = EventId("evt-$sessionId-$turnId-$ts"),
    sessionId = SessionId(sessionId),
    turnId = TurnId(turnId),
    ts = ts,
    seq = seq,
    text = text,
)

internal fun toolSucceeded(
    sessionId: String = "s1",
    turnId: String = "t1",
    ts: Long = 3L,
): AgentEvent = ToolCallSucceeded(
    id = EventId("evt-$sessionId-$turnId-$ts"),
    sessionId = SessionId(sessionId),
    turnId = TurnId(turnId),
    ts = ts,
    result = ToolResult(
        callId = ToolCallId("call-1"),
        output = ToolOutput.Diff(path = "app/Main.kt", unified = "@@ -1 +1 @@\n-old\n+new", addedLines = 1, removedLines = 1),
        durationMs = 12,
    ),
)

internal fun turnCompleted(
    sessionId: String = "s1",
    turnId: String = "t1",
    ts: Long = 4L,
): AgentEvent = TurnCompleted(
    id = EventId("evt-$sessionId-$turnId-$ts"),
    sessionId = SessionId(sessionId),
    turnId = TurnId(turnId),
    ts = ts,
    stopReason = StopReason.END_TURN,
    usage = Usage(inputTokens = 10, outputTokens = 20),
    iterations = 2,
)
