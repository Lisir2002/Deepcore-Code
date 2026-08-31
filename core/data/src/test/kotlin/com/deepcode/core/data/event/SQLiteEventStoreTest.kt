package com.deepcode.core.data.event

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.deepcode.core.data.SessionIndex
import com.deepcode.core.data.messageDelta
import com.deepcode.core.data.testDatabase
import com.deepcode.core.data.toolSucceeded
import com.deepcode.core.data.turnCompleted
import com.deepcode.core.data.turnStarted
import com.deepcode.core.model.AgentEvent
import com.deepcode.core.model.SessionId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SQLiteEventStoreTest {

    @Test
    fun `append 后 loadEvents 按 seq 顺序完整往返`() = runTest {
        val store = SQLiteEventStore(testDatabase())
        val events = listOf(turnStarted(ts = 10), messageDelta(ts = 11), toolSucceeded(ts = 12), turnCompleted(ts = 13))

        store.appendAll(events)

        assertEquals(events, store.loadEvents(SessionId("s1")))
    }

    @Test
    fun `多态事件往返后结构完全保真`() = runTest {
        val store = SQLiteEventStore(testDatabase())
        val toolEvent = toolSucceeded(ts = 7)

        store.append(toolEvent)

        val restored = store.loadEvents(SessionId("s1")).single()
        assertEquals(toolEvent, restored)
        val output = (restored as com.deepcode.core.model.ToolCallSucceeded).result.output
        assertTrue(output is com.deepcode.core.model.ToolOutput.Diff)
        assertEquals("app/Main.kt", output.path)
        assertEquals(12L, restored.result.durationMs)
    }

    @Test
    fun `不同会话的事件互相隔离`() = runTest {
        val store = SQLiteEventStore(testDatabase())
        store.append(turnStarted(sessionId = "s1", ts = 1))
        store.append(turnStarted(sessionId = "s1", ts = 2))
        store.append(turnStarted(sessionId = "s2", ts = 3))

        assertEquals(2, store.loadEvents(SessionId("s1")).size)
        assertEquals(1, store.loadEvents(SessionId("s2")).size)
        assertTrue(store.loadEvents(SessionId("s3")).isEmpty())
    }

    @Test
    fun `type 列存的是 SerialName 判别值而非 Kotlin 类名（R8 安全）`() = runTest {
        val db = testDatabase()
        val store = SQLiteEventStore(db)

        store.append(turnStarted(ts = 5))
        store.append(toolSucceeded(ts = 6))

        val types = db.read { db.database.eventsQueries.eventsForSession("s1").executeAsList().map { it.type } }
        assertEquals(listOf("turn_started", "tool_call_succeeded"), types)
    }

    @Test
    fun `append 同时维护会话索引，created_at 固定 updated_at 跟随最新事件`() = runTest {
        val db = testDatabase()
        val store = SQLiteEventStore(db)

        store.append(turnStarted(ts = 100, input = "第一行\n第二行"))
        store.append(messageDelta(ts = 200))

        val row = db.read { db.database.sessionsQueries.selectSession("s1").executeAsOne() }
        assertEquals("第一行", row.title)   // 取 userInput 首个非空行
        assertEquals(100L, row.created_at)  // 首装即定，后续事件不改
        assertEquals(200L, row.updated_at)  // 跟随最新事件 ts
    }

    @Test
    fun `事务内抛错整批回滚`() = runTest {
        val db = testDatabase()
        val store = SQLiteEventStore(db)
        store.append(turnStarted(ts = 1))

        assertFailsWith<IllegalStateException> {
            db.transaction {
                db.database.eventsQueries.insertEvent(
                    session_id = "s1", turn_id = "t9", ts = 2, type = "x", payload = "{}",
                )
                error("boom")
            }
        }

        assertEquals(1L, db.read { db.database.eventsQueries.countEvents().executeAsOne() })
    }

    @Test
    fun `clear 同时删掉事件与会话索引`() = runTest {
        val db = testDatabase()
        val store = SQLiteEventStore(db)
        store.append(turnStarted(ts = 1))
        store.append(turnStarted(sessionId = "s2", ts = 2))

        store.clear(SessionId("s1"))

        assertTrue(store.loadEvents(SessionId("s1")).isEmpty())
        assertNull(db.read { db.database.sessionsQueries.selectSession("s1").executeAsOneOrNull() })
        assertEquals(1, store.loadEvents(SessionId("s2")).size)  // 别的会话不受影响
    }

    @Test
    fun `observe 只推送订阅之后新增的事件，不回放历史`() = runTest {
        val db = testDatabase()
        val store = SQLiteEventStore(db)
        store.append(turnStarted(ts = 1))   // 订阅之前的历史

        val collected = mutableListOf<AgentEvent>()
        val job = launch { store.observe(SessionId("s1")).collect { collected += it } }
        runCurrent()                        // 让订阅真正建立基线

        val first = messageDelta(ts = 2)
        val second = messageDelta(ts = 3)
        store.append(first)
        runCurrent()
        store.append(second)
        runCurrent()

        assertEquals(listOf(first, second), collected)
        job.cancel()
    }

    @Test
    fun `observe 按会话过滤，别的会话事件不会串台`() = runTest {
        val db = testDatabase()
        val store = SQLiteEventStore(db)

        val s1Events = mutableListOf<AgentEvent>()
        val job = launch { store.observe(SessionId("s1")).collect { s1Events += it } }
        runCurrent()

        store.append(turnStarted(sessionId = "s2", ts = 1))
        runCurrent()
        store.append(turnStarted(sessionId = "s1", ts = 2))
        runCurrent()

        assertEquals(1, s1Events.size)
        assertEquals(SessionId("s1"), s1Events.single().sessionId)
        job.cancel()
    }

    @Test
    fun `observeSessions 按更新时间倒序，且不需要重放事件流`() = runTest {
        val db = testDatabase()
        val store = SQLiteEventStore(db)

        val updates = mutableListOf<List<SessionIndex>>()
        val job = launch { store.observeSessions().collect { updates += it } }
        runCurrent()

        store.append(turnStarted(sessionId = "a", ts = 10, input = "会话 A"))
        runCurrent()
        store.append(turnStarted(sessionId = "b", ts = 20, input = "会话 B"))
        runCurrent()

        val latest = updates.last()
        assertEquals(listOf("b", "a"), latest.map { it.id.value })
        assertEquals("会话 B", latest.first().title)
        assertEquals(20L, latest.first().updatedAt)
        job.cancel()
    }

    @Test
    fun `renameSession 改标题并推进 updated_at`() = runTest {
        val db = testDatabase()
        val store = SQLiteEventStore(db)
        store.append(turnStarted(ts = 10, input = "原标题"))

        store.renameSession(SessionId("s1"), "新标题", at = 999)

        val row = db.read { db.database.sessionsQueries.selectSession("s1").executeAsOne() }
        assertEquals("新标题", row.title)
        assertEquals(999L, row.updated_at)
        assertEquals(10L, row.created_at)
    }

    @Test
    fun `重开数据库后事件仍在（真正落盘，不是内存态）`() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SQLiteEventStore(testDatabase(driver)).append(turnStarted(ts = 1))

        // 同一个 driver 重新打开：走的是"升级/常开"分支
        val reopened = SQLiteEventStore(testDatabase(driver))

        assertEquals(listOf(turnStarted(ts = 1)), reopened.loadEvents(SessionId("s1")))
    }
}
