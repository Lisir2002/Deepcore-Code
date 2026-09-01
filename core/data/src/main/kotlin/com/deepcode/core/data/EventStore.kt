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

    // ─────────── 会话索引（会话列表页数据源） ───────────
    // 会话列表页不需要全量重放事件流，只看索引即可；会话内容仍在事件流里。
    // 新建/重命名/删除都是元数据操作，与事件写入分属不同事务边界。

    /** 观测全部会话，按最近更新排序；首次订阅立即发射当前快照。 */
    fun observeSessions(): Flow<List<SessionIndex>>

    /** 预创建会话（用户点「新建对话」但尚未发消息时）。标题留空，首个 turn 到来后自动填充。 */
    suspend fun createSession(
        id: SessionId,
        title: String = "",
        at: Long = System.currentTimeMillis(),
    )

    /** 重命名会话。 */
    suspend fun renameSession(
        sessionId: SessionId,
        title: String,
        at: Long = System.currentTimeMillis(),
    )
}
