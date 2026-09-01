package com.deepcode.core.uistate

import com.deepcode.core.model.AgentEvent
import com.deepcode.core.model.MessageDelta
import com.deepcode.core.model.ModelRef
import com.deepcode.core.model.SessionStatus
import com.deepcode.core.model.ToolCallApproved
import com.deepcode.core.model.ToolCallDenied
import com.deepcode.core.model.ToolCallFailed
import com.deepcode.core.model.ToolCallProposed
import com.deepcode.core.model.ToolCallStarted
import com.deepcode.core.model.ToolCallSucceeded
import com.deepcode.core.model.TurnCancelled
import com.deepcode.core.model.TurnCompleted
import com.deepcode.core.model.TurnFailed
import com.deepcode.core.model.TurnStarted

/**
 * 事件流 → 会话列表摘要 的归约器（纯 Kotlin，JVM 可测）。
 *
 * 会话列表页不可能为每个会话全量重放事件流再逐条渲染，它只需要三样东西：
 *   • 标题：首个非空用户输入的首行（与存储层写入 sessions 索引的标题同源）
 *   • 预览：最近一轮的助手正文（MessageDelta 累加），无正文则回落到最近一次用户输入
 *   • 状态角标：运行中 / 待授权 / 失败 / 空闲，由最后一个「未终结」事件推断
 *
 * 铁律：不维护任何可变中间状态（无 ViewModel、无 Compose），纯输入 → 纯输出。
 */
data class SessionSummary(
    val title: String,
    val preview: String,
    val status: SessionStatus,
    val modelRef: ModelRef? = null,
)

object SessionSummaryReducer {

    private const val MAX = 80

    fun reduce(events: List<AgentEvent>): SessionSummary {
        var title = ""
        var preview = ""
        var lastUserInput = ""
        var status = SessionStatus.IDLE

        events.forEach { event ->
            when (event) {
                is TurnStarted -> {
                    val firstLine = event.userInput.lineSequence()
                        .firstOrNull { it.isNotBlank() }?.trim().orEmpty()
                    if (title.isBlank() && firstLine.isNotBlank()) title = firstLine.take(MAX)
                    if (firstLine.isNotBlank()) lastUserInput = firstLine.take(MAX)
                    preview = "" // 新一轮开始，清掉上一轮助手正文
                    status = SessionStatus.RUNNING
                }

                is MessageDelta -> preview += event.text

                is ToolCallProposed -> status = SessionStatus.AWAITING_APPROVAL

                // 权限门裁决后回到执行中；工具失败不终结 turn，仍算运行。
                is ToolCallApproved, is ToolCallDenied, is ToolCallStarted,
                is ToolCallSucceeded, is ToolCallFailed -> status = SessionStatus.RUNNING

                is TurnCompleted, is TurnCancelled -> status = SessionStatus.IDLE

                is TurnFailed -> status = SessionStatus.FAILED

                else -> Unit
            }
        }

        val finalPreview = preview.trim().take(MAX).ifBlank { lastUserInput }
        return SessionSummary(
            title = title,
            preview = finalPreview,
            status = status,
            // modelRef 暂无事件来源（主会话不发射 model 事件），留 null，UI 据此隐藏角标。
            modelRef = null,
        )
    }
}
