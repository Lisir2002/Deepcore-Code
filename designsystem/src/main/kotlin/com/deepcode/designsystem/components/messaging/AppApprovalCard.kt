package com.deepcode.designsystem.components.messaging

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deepcode.core.model.RiskLevel
import com.deepcode.core.model.ToolCall
import com.deepcode.designsystem.components.AppPrimaryButton
import com.deepcode.designsystem.components.AppSecondaryButton
import com.deepcode.designsystem.components.AppTextButton
import com.deepcode.designsystem.theme.Dimens
import com.deepcode.designsystem.theme.appColors

/**
 * 审批卡（§6.8.4 PermissionGate 内联形态）：危险等级 ≥ 写 的工具调用触发。
 *
 * 知情决策而非盲确认：命令完整预览（mono）。三选择竖排——拒绝（次钮）/ 仅本次允许（次钮）/
 * 本会话允许（实心主钮）；破坏性操作主钮转 `danger` 并附后果一句话（标题三禁同样适用）。
 *
 * **不因滚动消失**：由置顶位承载（输入坞上方 sticky，业务侧保证）。
 */
@Composable
fun AppApprovalCard(
    call: ToolCall,
    risk: RiskLevel,
    commandPreview: String,
    onDeny: () -> Unit,
    onAllowOnce: () -> Unit,
    onAllowSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = appColors()
    val destructive = risk == RiskLevel.DESTRUCTIVE

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.radiusM),
        color = if (destructive) colors.dangerContainer else colors.surfaceElevated,
        border = if (destructive) {
            androidx.compose.foundation.BorderStroke(1.dp, colors.danger.copy(alpha = 0.4f))
        } else null,
    ) {
        Column(modifier = Modifier.padding(Dimens.spaceL)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (destructive) colors.danger else colors.toolAwaiting,
                    modifier = Modifier.padding(end = Dimens.spaceS),
                )
                Text(
                    text = if (destructive) "这个操作有风险" else "需要你确认",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(Dimens.spaceS))
            Text(
                text = commandPreview,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(Dimens.spaceL))

            // 三选择竖排：主钮 = 本会话允许（破坏性转 danger），两个次钮在上
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
                AppSecondaryButton(text = "拒绝", onClick = onDeny, modifier = Modifier.fillMaxWidth())
                AppSecondaryButton(text = "仅本次允许", onClick = onAllowOnce, modifier = Modifier.fillMaxWidth())
                AppPrimaryButton(
                    text = "本会话允许",
                    onClick = onAllowSession,
                    modifier = Modifier.fillMaxWidth(),
                    danger = destructive,
                )
            }
            if (destructive) {
                Spacer(Modifier.height(Dimens.spaceS))
                Text(
                    text = "本次操作将持久修改文件或触发不可逆动作，请确认已在命令预览中了解后果。",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.danger,
                )
            }
        }
    }
}