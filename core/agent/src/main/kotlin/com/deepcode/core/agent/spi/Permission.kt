package com.deepcode.core.agent.spi

import com.deepcode.core.model.ApprovalScope
import com.deepcode.core.model.ToolCall
import com.deepcode.core.model.ToolSpec

sealed interface ApprovalDecision {
    data class Approved(val scope: ApprovalScope) : ApprovalDecision
    data class Denied(val reason: String? = null) : ApprovalDecision
}

/**
 * 权限裁决门。
 *
 * 这是安全模型的唯一入口：工具只声明 [ToolSpec.riskLevel]，
 * 由这里决定是放行、询问还是拒绝。
 *
 * 为什么必须集中：把 `if (confirm)` 写在每个工具内部，随着工具变多必然漏。
 * 集中之后，审计、策略持久化、YOLO 模式、企业策略都是改一处。
 */
interface PermissionGate {
    suspend fun request(call: ToolCall, spec: ToolSpec): ApprovalDecision
}

/** 已放行的策略持久化（"本次会话始终允许" / "永久允许"）。 */
interface ApprovalPolicyStore {
    suspend fun isAllowed(signature: String, scope: ApprovalScope): Boolean
    suspend fun remember(signature: String, scope: ApprovalScope)
    suspend fun forget(signature: String)
}

/**
 * 调用签名：工具名 + 参数键集合（不含值）。
 *
 * 不含值是刻意的——用户勾"始终允许写文件"时，意图是允许这个动作，
 * 而不是允许写某一个具体路径。
 */
fun ToolCall.signature(): String =
    if (arguments.isEmpty()) name
    else "$name(${arguments.keys.sorted().joinToString(",")})"

/** 测试与 YOLO 模式用：全部放行。 */
object AllowAllGate : PermissionGate {
    override suspend fun request(call: ToolCall, spec: ToolSpec): ApprovalDecision =
        ApprovalDecision.Approved(ApprovalScope.SESSION)
}

/** 测试与只读诊断用：只放行只读，其余拒绝。 */
object ReadOnlyGate : PermissionGate {
    override suspend fun request(call: ToolCall, spec: ToolSpec): ApprovalDecision =
        if (spec.riskLevel == com.deepcode.core.model.RiskLevel.READ_ONLY) {
            ApprovalDecision.Approved(ApprovalScope.ONCE)
        } else {
            ApprovalDecision.Denied("只读模式下不允许 ${spec.name}")
        }
}
