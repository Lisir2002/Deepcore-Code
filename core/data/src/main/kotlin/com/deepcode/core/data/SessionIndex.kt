package com.deepcode.core.data

import com.deepcode.core.model.SessionId

/**
 * 会话索引行。
 *
 * 会话列表页要的是"最近改过哪些会话、标题是什么"，为此去全量重放事件流太贵；
 * 这里只存索引，真正的会话内容仍然在 events 表里（事件流是唯一契约，见 ARCHITECTURE.md）。
 */
data class SessionIndex(
    val id: SessionId,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
)
