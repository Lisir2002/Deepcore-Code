package com.deepcode.designsystem.components.messaging

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deepcode.core.model.RiskLevel
import com.deepcode.core.uistate.RenderBlock
import com.deepcode.core.uistate.ToolVisualStatus
import com.deepcode.designsystem.components.AppStatusChip
import com.deepcode.designsystem.components.AppTextButton
import com.deepcode.designsystem.render.ToolOutputView
import com.deepcode.designsystem.theme.Dimens
import com.deepcode.designsystem.theme.appColors

/**
 * 工具调用卡（§6.8.2）。卡片三区：头（图标 + 标题 + 状态徽章）/ 身（关键参数摘要，全文折叠）/
 * 脚（耗时 + 失败时重试链接）。默认高度 ≤120dp。
 *
 * 展开策略（已拍板）：运行中展开 + 结果流式进卡（替代黑盒 spinner），完成即自动折叠为一行摘要；
 * 用户手动展开的卡片不自动收起（尊重显式意图）。
 */
@Composable
fun AppToolCard(
    block: RenderBlock.ToolInvocation,
    registry: ToolCardRegistry,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val colors = appColors()
    val spec = registry[block.toolName]
    val (statusText, statusColor) = toolStatus(block.status, colors.toolAwaiting, colors.toolRunning, colors.toolSuccess, colors.toolFailed)

    // 折叠/展开：用户显式意图优先，未显式时运行中自动展开。
    var userExpanded by rememberSaveable { mutableStateOf(false) }
    val autoJoinRunning = block.status == ToolVisualStatus.RUNNING && !userExpanded
    val expanded = userExpanded || autoJoinRunning
    val hasOutput = block.output != null || block.progressText.isNotBlank()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 120.dp), // 折叠态上限（§6.8.2：10 步运行一屏可见）
        shape = RoundedCornerShape(Dimens.radiusM),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(Dimens.spaceM)) {
            // 头：图标 + 标题 + 状态徽章 + 风险 + 展开箭头
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = spec.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Dimens.iconM),
                )
                Spacer(Modifier.size(Dimens.spaceS))
                Text(
                    text = spec.title(block.toolName),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                AppStatusChip(
                    text = statusText,
                    containerColor = statusColor.copy(alpha = 0.16f),
                    contentColor = statusColor,
                    busy = block.status == ToolVisualStatus.RUNNING,
                )
                if (block.risk != RiskLevel.READ_ONLY) {
                    Spacer(Modifier.size(Dimens.spaceXS))
                    AppStatusChip(text = riskLabel(block.risk))
                }
                if (autoJoinRunning || userExpanded) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "展开/折叠",
                        modifier = Modifier
                            .size(Dimens.iconM)
                            .appClickable { userExpanded = !userExpanded },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 身：关键参数摘要一行（全文折叠）——一律折叠区（未见含未审计 JSON 的正文）
            if (block.argumentsSummary.isNotBlank()) {
                Spacer(Modifier.height(Dimens.spaceXS))
                Text(
                    text = spec.summary(block.argumentsSummary),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .appClickable { userExpanded = !userExpanded },
                )
            }

            // 展开区：流式进度 + 结果内联（按 ToolOutput 类型路由）；原始 JSON 仅在此折叠区
            AnimatedVisibility(visible = expanded) {
                Column {
                    if (block.progressText.isNotBlank() && block.status == ToolVisualStatus.RUNNING) {
                        Spacer(Modifier.height(Dimens.spaceS))
                        Text(
                            text = block.progressText,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (hasOutput) Spacer(Modifier.height(Dimens.spaceS))
                    }
                    block.output?.let { output ->
                        ToolOutputView(output)
                    }
                }
            }

            // 脚：耗时 + 失败重试链接
            if (block.durationMs > 0 || (block.status == ToolVisualStatus.FAILED && onRetry != null)) {
                Spacer(Modifier.height(Dimens.spaceS))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (block.durationMs > 0) {
                        Text(
                            text = "${block.durationMs}ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (block.status == ToolVisualStatus.FAILED && onRetry != null) {
                        AppTextButton(text = "重试", onClick = onRetry)
                    }
                }
            }
        }
    }
}

/** 供 messages 包内复用的轻量点击 modifier（无墨迹，避免 M3 ripple）。 */
private fun Modifier.appClickable(onClick: () -> Unit): Modifier =
    clickable(interactionSource = null, indication = null, onClick = onClick)

private fun toolStatus(
    status: ToolVisualStatus,
    awaiting: androidx.compose.ui.graphics.Color,
    running: androidx.compose.ui.graphics.Color,
    success: androidx.compose.ui.graphics.Color,
    failed: androidx.compose.ui.graphics.Color,
): Pair<String, androidx.compose.ui.graphics.Color> = when (status) {
    ToolVisualStatus.AWAITING_APPROVAL -> "待确认" to awaiting
    ToolVisualStatus.RUNNING -> "执行中" to running
    ToolVisualStatus.SUCCEEDED -> "已完成" to success
    ToolVisualStatus.FAILED -> "失败" to failed
    ToolVisualStatus.DENIED -> "已拒绝" to failed
}

private fun riskLabel(risk: RiskLevel): String = when (risk) {
    RiskLevel.READ_ONLY -> "只读"
    RiskLevel.WRITE -> "写入"
    RiskLevel.DESTRUCTIVE -> "危险"
    RiskLevel.NETWORK -> "联网"
    RiskLevel.PRIVILEGED -> "敏感权限"
}