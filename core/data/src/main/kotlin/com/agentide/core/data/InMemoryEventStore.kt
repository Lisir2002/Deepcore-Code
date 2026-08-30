package com.agentide.core.data

import com.agentide.core.model.AgentEvent
import com.agentide.core.model.SessionId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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

    override suspend fun append(event: AgentEvent) {
        mutex.withLock {
            logs.getOrPut(event.sessionId) { mutableListOf() }.add(event)
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

    override suspend fun clear(sessionId: SessionId) {
        mutex.withLock { logs.remove(sessionId) }
    }
}
