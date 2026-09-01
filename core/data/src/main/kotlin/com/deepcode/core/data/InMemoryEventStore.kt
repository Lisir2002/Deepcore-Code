package com.deepcode.core.data

import com.deepcode.core.model.AgentEvent
import com.deepcode.core.model.SessionId
import com.deepcode.core.model.TurnStarted
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * 内存实现，用于 M0 跑通链路与单测。
 * 后续可替换为文件实现（kotlinx.serialization 追加写）或 Room 实现，接口不变。
 */
class InMemoryEventStore : EventStore {

    private val logs = ConcurrentHashMap<SessionId, MutableList<AgentEvent>>()
    private val bus = MutableSharedFlow<AgentEvent>(extraBufferCapacity = 256)
    private val mutex = Mutex()

    // 会话索引：与 SQLiteEventStore 的 sessions 表对应，列表页只读它、不重放事件流。
    private val index = ConcurrentHashMap<SessionId, SessionIndex>()
    private val sessions = MutableStateFlow<List<SessionIndex>>(emptyList())

    override suspend fun append(event: AgentEvent) {
        mutex.withLock {
            logs.getOrPut(event.sessionId) { mutableListOf() }.add(event)
            touchIndex(event)
        }
        bus.emit(event)
    }

    override suspend fun appendAll(events: List<AgentEvent>) {
        events.forEach { append(it) }
    }

    override suspend fun loadEvents(sessionId: SessionId): List<AgentEvent> =
        mutex.withLock { logs[sessionId]?.toList() ?: emptyList() }

    override fun observe(sessionId: SessionId): Flow<AgentEvent> =
        bus.filter { it.sessionId == sessionId }

    override fun observeSessions(): Flow<List<SessionIndex>> = sessions

    override suspend fun createSession(
        id: SessionId,
        title: String,
        at: Long,
    ) = mutex.withLock {
        index.putIfAbsent(id, SessionIndex(id = id, title = title, createdAt = at, updatedAt = at))
        emitIndex()
    }

    override suspend fun renameSession(
        sessionId: SessionId,
        title: String,
        at: Long,
    ) = mutex.withLock {
        index.computeIfPresent(sessionId) { _, row ->
            row.copy(title = title, updatedAt = at)
        }
        emitIndex()
    }

    override suspend fun clear(sessionId: SessionId) {
        mutex.withLock {
            logs.remove(sessionId)
            index.remove(sessionId)
            emitIndex()
        }
    }

    private fun touchIndex(event: AgentEvent) {
        val row = index[event.sessionId]
        if (row == null) {
            index[event.sessionId] = SessionIndex(
                id = event.sessionId,
                title = titleOf(event),
                createdAt = event.ts,
                updatedAt = event.ts,
            )
        } else {
            index[event.sessionId] = row.copy(
                title = row.title.ifBlank { titleOf(event) },
                updatedAt = event.ts,
            )
        }
        emitIndex()
    }

    /** 与 SQLiteEventStore.titleOf 对齐：首个非空 turn 输入首行即标题。 */
    private fun titleOf(event: AgentEvent): String = when (event) {
        is TurnStarted ->
            event.userInput.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(TITLE_MAX) ?: ""
        else -> ""
    }

    private fun emitIndex() {
        sessions.value = index.values.sortedByDescending { it.updatedAt }
    }

    private companion object {
        const val TITLE_MAX = 60
    }
}
