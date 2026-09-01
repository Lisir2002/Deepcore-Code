package com.deepcode.designsystem.components.messaging

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deepcode.designsystem.theme.Dimens
import com.deepcode.designsystem.theme.appColors

/**
 * 移动化进度面板（§6.8.6 / D20）：置顶摘要条 + 细进度线。
 *
 * 不做独立进度面板（Compact 端挤压会话区、与五型骨架冲突）——多步任务运行时
 * sticky 于输入坞上方，点条打开 `AppModalSheet` 时间线抽屉（每步折叠工具卡逐一审计）。
 *
 * 规则：单工具短任务（<3s）由工具卡自足，不应出现本条。出现/淡出由调用方用
 * `AnimatedVisibility` 包装；未知总数时不画进度比例，只显示「N 步完成」+ 当前动作。
 */
@Composable
fun AppProgressSummary(
    doneSteps: Int,
    totalSteps: Int?,
    currentLabel: String,
    onOpenTimeline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = appColors()
    val completed = totalSteps != null && doneSteps >= totalSteps
    val progress = (doneSteps.toFloat() / (totalSteps?.coerceAtLeast(doneSteps) ?: doneSteps)).coerceIn(0f, 1f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(interactionSource = null, indication = null, onClick = onOpenTimeline),
        shape = RoundedCornerShape(Dimens.radiusM),
        color = MaterialTheme.colorScheme.surfaceElevated,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
            Column(modifier = Modifier.padding(horizontal = Dimens.spaceM, vertical = Dimens.spaceS)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (completed) Icons.Filled.CheckCircle else Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = if (completed) colors.success else colors.primary,
                        modifier = Modifier.size(Dimens.iconM),
                    )
                    Spacer(Modifier.size(Dimens.spaceS))
                    Text(
                        text = if (totalSteps != null) "$doneSteps/$totalSteps 步完成" else "$doneSteps 步完成",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.size(Dimens.spaceS))
                    Text(
                        text = "· $currentLabel",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (totalSteps != null) {
                    Spacer(Modifier.height(Dimens.spaceS))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = colors.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
        }
    }
}