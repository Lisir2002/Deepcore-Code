package com.deepcode.core.agent

import com.deepcode.core.agent.spi.ApprovalDecision
import com.deepcode.core.agent.spi.ApprovalPolicyStore
import com.deepcode.core.agent.spi.PermissionGate
import com.deepcode.core.agent.spi.signature
import com.deepcode.core.logging.Log
import com.deepcode.core.logging.LogCategory
import com.deepcode.core.logging.LogLevel
import com.deepcode.core.model.ApprovalScope
import com.deepcode.core.model.RiskLevel
import com.deepcode.core.model.ToolCall
import com.deepcode.core.model.ToolSpec
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 与 UI 交互的权限门。
 *
 * 核心技巧：**主循环在请求权限时直接挂起**（suspendCancellableCoroutine），
 * 而不是轮询或状态机。用户在弹窗上点"允许"后，[respond] 恢复协程，
 * 主循环从原处继续往下跑。整个流程没有中间状态需要维护。
 */
class InteractivePermissionGate(
    private val policyStore: ApprovalPolicyStore? = null,
    private val autoApproveReadOnly: Boolean = true,
) : PermissionGate {

    private val lock = Any()
    private var pending: CancellableContinuation<ApprovalDecision>? = null

    fun hasPending(): Boolean = synchronized(lock) { pending != null }

    fun pendingCall(): ToolCall? = synchronized(lock) { pendingCallValue }

    @Volatile
    private var pendingCallValue: ToolCall? = null

    override suspend fun request(call: ToolCall, spec: ToolSpec): ApprovalDecision {
        val signature = call.signature()

        // 1) 已记住的策略直接放行，不打扰用户
        if (policyStore?.isAllowed(signature, ApprovalScope.ALWAYS) == true) {
            Log.log(LogLevel.DEBUG, LogCategory.SECURITY_PERMISSION, "AgentRuntime", "策略放行 ${call.name}（ALWAYS）")
            return ApprovalDecision.Approved(ApprovalScope.ALWAYS)
        }
        if (policyStore?.isAllowed(signature, ApprovalScope.SESSION) == true) {
            Log.log(LogLevel.DEBUG, LogCategory.SECURITY_PERMISSION, "AgentRuntime", "策略放行 ${call.name}（SESSION）")
            return ApprovalDecision.Approved(ApprovalScope.SESSION)
        }

        // 2) 只读操作默认不打断（可在设置里关掉）
        if (autoApproveReadOnly && spec.riskLevel == RiskLevel.READ_ONLY) {
            Log.log(LogLevel.DEBUG, LogCategory.SECURITY_PERMISSION, "AgentRuntime", "只读放行 ${call.name}（ONCE）")
            return ApprovalDecision.Approved(ApprovalScope.ONCE)
        }

        // 3) 挂起，等 UI 响应
        Log.log(LogLevel.DEBUG, LogCategory.SECURITY_PERMISSION, "AgentRuntime", "等待用户审批 ${call.name}（${spec.riskLevel}）")
        return suspendCancellableCoroutine { cont ->
            synchronized(lock) {
                pendingCallValue = call
                pending = cont
            }
            cont.invokeOnCancellation {
                synchronized(lock) {
                    if (pending === cont) {
                        pending = null
                        pendingCallValue = null
                    }
                }
            }
        }
    }

    /** UI 调用。返回是否真的唤醒了一个等待中的请求。 */
    fun respond(decision: ApprovalDecision): Boolean {
        // 在清空 pendingCallValue 之前先取出工具名，否则下面的审计日志永远读到 null
        var callName: String? = null
        val cont = synchronized(lock) {
            callName = pendingCallValue?.name
            pending.also {
                pending = null
                pendingCallValue = null
            }
        }
        return if (cont != null && cont.isActive) {
            Log.log(
                if (decision is ApprovalDecision.Approved) LogLevel.INFO else LogLevel.WARN,
                LogCategory.SECURITY_PERMISSION, "AgentRuntime",
                "用户${if (decision is ApprovalDecision.Approved) "批准" else "拒绝"} ${callName ?: "工具调用"}",
            )
            cont.resume(decision)
            true
        } else {
            false
        }
    }
}
