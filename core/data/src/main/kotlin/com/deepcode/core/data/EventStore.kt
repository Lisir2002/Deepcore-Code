package com.deepcode.core.data

import com.deepcode.core.model.AgentEvent
import com.deepcode.core.model.SessionId
import kotlinx.coroutines.flow.Flow

/**
 * 事件日志。
 *
 * 设计要点：**只追加（append-only），从不修改**。
 * 会话内容不存"最终状态"，而是存事件序列：
 *   • 进程被杀后重放即恢复，不用写任何额外的恢复逻辑
 *   • 顺便白拿了时间旅行、分支、审计、回放能力
 *   • 存储实现可以随时从内存换成文件/Room，上层无感
 */
interface EventStore {
    suspend fun append(event: AgentEvent)
    suspend fun appendAll(events: List<AgentEvent>)
    suspend fun loadEvents(sessionId: SessionId): List<AgentEvent>
    fun observe(sessionId: SessionId): Flow<AgentEvent>
    suspend fun clear(sessionId: SessionId)
}
