package com.agentide.core.agent.spi

import com.agentide.core.model.WorkspaceRef

/**
 * 项目级记忆（等价 CLAUDE.md / AGENTS.md）。
 * 在每次 turn 开始时注入 system prompt，让 Agent 记住这个项目的约定。
 */
interface ProjectMemory {
    suspend fun load(workspaceRef: WorkspaceRef?): String?
    suspend fun save(workspaceRef: WorkspaceRef?, content: String): Boolean
}

/** 跨会话长期记忆。M0 只定义接口，实现可后续接向量库或纯文件。 */
interface LongTermMemory {
    suspend fun recall(query: String, limit: Int = 8): List<MemoryItem>
    suspend fun remember(item: MemoryItem)
    suspend fun forget(id: String)
}

data class MemoryItem(
    val id: String,
    val content: String,
    /** 作用域：global / project:<id> */
    val scope: String,
    val createdAt: Long,
)
