package com.deepcode.designsystem.components.overlay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.deepcode.designsystem.behavior.appStateLayer
import com.deepcode.designsystem.behavior.rememberNoInkIndication
import com.deepcode.designsystem.components.AppModalSheet
import com.deepcode.designsystem.theme.Dimens
import com.deepcode.designsystem.theme.appColors
import com.deepcode.designsystem.theme.appTokens

/**
 * 浮层子组件——单选下拉（§6.6.2）。
 *
 * 触发器两形态：`Field` 形态（内嵌 AppTextField 外观）/ `Button` 形态（筛选条/顶栏操作）。
 * 面板：圆角 `radiusM`、`surfaceElevated` 海拔、宽 = 触发器宽（最小 120dp）、条目高 44dp、
 * 条目态走 §4.1（selected = primary 12% 底 + check 图标 primary 色）、条目可分块（分隔线 + 分组标题）。
 *
 * 多选不走菜单（撑不下且触控质量差）——用 [AppMultiSelectSheet]，见 §6.6.2 末段。
 * 禁业务层裸用 `DropdownMenu` / `ExposedDropdownMenuBox`（lint ForbiddenRawDropdown 拦截）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    anchor: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    minWidth: Dp = 120.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box {
        anchor()
        if (expanded) {
            DropdownMenu(
                expanded = true,
                onDismissRequest = onDismissRequest,
                modifier = modifier.widthIn(min = minWidth),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(appTokens().radius.card),
                containerColor = MaterialTheme.colorScheme.surfaceElevated,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
            ) {
                content()
            }
        }
    }
}

/** 下拉条目：44dp 高，触控冗余 48dp；selected = primary 12% 底 + check 图标 primary 色。 */
@Composable
fun AppDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    val colors = appColors()
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .appStateLayer(interaction, selected = selected, overlayColor = colors.primary)
            .clickable(
                interactionSource = interaction,
                indication = rememberNoInkIndication(),
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = Dimens.spaceL),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = if (selected) colors.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimens.iconM),
            )
            Spacer(Modifier.width(Dimens.spaceM))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(Dimens.iconM),
            )
        }
    }
}

/** 分组标题：labelMedium + textTertiary。 */
@Composable
fun AppDropdownMenuHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spaceL, vertical = Dimens.spaceS),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 条目分隔线（分组之间）。 */
@Composable
fun AppDropdownMenuDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.fillMaxWidth(),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

// ───────────────────────────── 多选 AppMultiSelectSheet ─────────────────────────────

/**
 * 多选 = [AppModalSheet] + checkbox 列表（§6.6.2 末段）：Compact 端多选不撑菜单，
 * 走底部模态面板，触控质量与可视性更佳。选中态走 §4.1（primary + check）。
 */
@Composable
fun AppMultiSelectSheet(
    title: String,
    options: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    confirmText: String = "完成",
) {
    val colors = appColors()
    AppModalSheet(onDismiss = onDismiss, modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Dimens.spaceL))
        options.forEach { option ->
            val isSelected = option in selected
            val interaction = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .appStateLayer(interaction, selected = isSelected, overlayColor = colors.primary)
                    .clickable(
                        interactionSource = interaction,
                        indication = rememberNoInkIndication(),
                        onClick = { onToggle(option) },
                    )
                    .padding(Dimens.spaceM),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = if (isSelected) colors.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.0f),
                    modifier = Modifier.size(Dimens.iconM),
                )
            }
        }
        Spacer(Modifier.height(Dimens.spaceL))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) {
                Text("取消", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.width(Dimens.spaceS))
            TextButton(onClick = onConfirm) {
                Text(confirmText, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}