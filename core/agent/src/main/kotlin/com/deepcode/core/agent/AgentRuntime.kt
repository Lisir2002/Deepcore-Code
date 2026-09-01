package com.deepcode.core.agent

import com.deepcode.core.model.AgentEvent
import com.deepcode.core.model.ApprovalScope
import com.deepcode.core.model.Attachment
import com.deepcode.core.model.SessionId
import com.deepcode.core.model.ToolCall
import com.deepcode.core.model.TurnId
import kotlinx.coroutines.flow.Flow

data class AgentConfig(
    /** 单个 turn 内最多几轮 LLM↔工具循环，防止跑飞烧钱。 */
    val maxIterations: Int = 24,
    val contextWindowTokens: Int = 200_000,
    val maxOutputTokens: Int = 8192,
    val temperature: Double? = null,
    /** 只读工具是否自动放行。关掉就变成"每一步都问"，适合演示权限流。 */
    val autoApproveReadOnly: Boolean = true,
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val enablePromptCaching: Boolean = true,
)

val DEFAULT_SYSTEM_PROMPT: String = """
你是运行在 Android 设备上的编程助手。约束：
1. 优先用工具获取真实信息，不要凭记忆猜测代码库内容。
2. 修改前先读取确认，不要凭空写文件。
3. 命令输出很长时先自己提炼，不要把原始输出整段贴给用户。
4. 回答用中文，代码注释保持原有语言风格。
""".trimIndent()

/**
 * Agent 运行时的对外契约。
 *
 * UI 只跟这四个方法打交道 + 订阅一条事件流。
 * 这套接口小到可以整个替换实现（同步/异步、本地/远端），这也是留给未来的口子。
 */
interface AgentRuntime {

    val sessionId: SessionId

    /** 实时事件流。UI 订阅它渲染，存储层订阅它落盘。 */
    fun events(): Flow<AgentEvent>

    /** 从事件日志重建完整历史（冷启动恢复用）。 */
    suspend fun history(): List<AgentEvent>

    /** 发起一轮对话。立即返回 turnId，执行在后台进行。 */
    suspend fun submit(userInput: String, attachments: List<Attachment> = emptyList()): TurnId

    /** 用户在权限弹窗上的选择，用来唤醒挂起中的主循环。 */
    suspend fun respondToApproval(
        call: ToolCall,
        approved: Boolean,
        scope: ApprovalScope = ApprovalScope.ONCE,
        reason: String? = null,
    )

    suspend fun cancel(reason: String? = null)

    /** 当前是否有 turn 在跑。 */
    fun isRunning(): Boolean
}

/**
 * 按会话 ID 创建 Agent 运行时。
 *
 * 多会话：会话列表页同时存在多个会话，每个会话对应一个 runtime。
 * DI 层把「构造一个 runtime 所需的全部依赖」打包进工厂，UI 只传 sessionId。
 */
fun interface AgentRuntimeFactory {
    fun create(sessionId: SessionId): AgentRuntime
}
