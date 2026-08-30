package com.agentide.core.agent

import com.agentide.core.agent.spi.ApprovalDecision
import com.agentide.core.agent.spi.ApprovalPolicyStore
import com.agentide.core.agent.spi.PermissionGate
import com.agentide.core.agent.spi.signature
import com.agentide.core.model.ApprovalScope
import com.agentide.core.model.RiskLevel
import com.agentide.core.model.ToolCall
import com.agentide.core.model.ToolSpec
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
            return ApprovalDecision.Approved(ApprovalScope.ALWAYS)
        }
        if (policyStore?.isAllowed(signature, ApprovalScope.SESSION) == true) {
            return ApprovalDecision.Approved(ApprovalScope.SESSION)
        }

        // 2) 只读操作默认不打断（可在设置里关掉）
        if (autoApproveReadOnly && spec.riskLevel == RiskLevel.READ_ONLY) {
            return ApprovalDecision.Approved(ApprovalScope.ONCE)
        }

        // 3) 挂起，等 UI 响应
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
        val cont = synchronized(lock) {
            pending.also {
                pending = null
                pendingCallValue = null
            }
        }
        return if (cont != null && cont.isActive) {
            cont.resume(decision)
            true
        } else {
            false
        }
    }
}
