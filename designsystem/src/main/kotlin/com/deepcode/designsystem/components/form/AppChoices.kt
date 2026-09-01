package com.deepcode.designsystem.components.form

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.deepcode.designsystem.behavior.appStateLayer
import com.deepcode.designsystem.behavior.rememberNoInkIndication
import com.deepcode.designsystem.theme.Dimens
import com.deepcode.designsystem.theme.appColors

/**
 * 选择控件族（§6.7.2）：`AppCheckbox` / `AppRadio`。
 *
 * 均 20dp、选中 `primary`、态走 §4.1（appStateLayer + 无墨迹）。
 * 禁业务层裸用 M3 `Checkbox`/`RadioButton`/`Switch`。
 */
private object ChoiceSpec {
    const val Box = 20
}

// ───────────────────────────── AppCheckbox ─────────────────────────────

/**
 * 复选：20dp，选中 `primary` 底 + 白勾；`indeterminate` 半选态预留（短横）。
 */
@Composable
fun AppCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    indeterminate: Boolean = false,
) {
    val colors = appColors()
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(4.dp)
    val bg = if (checked) colors.primary else if (enabled) MaterialTheme.colorScheme.surfaceVariant
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    Box(
        modifier = modifier
            .size(ChoiceSpec.Box.dp)
            .appStateLayer(interaction, overlayColor = colors.primary, shape = shape)
            .background(bg, shape)
            .toggleable(
                value = checked,
                onValueChange = { if (enabled) onCheckedChange(it) },
                enabled = enabled,
                role = Role.Checkbox,
                interactionSource = interaction,
                indication = rememberNoInkIndication(),
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(visible = checked) {
            Icon(
                imageVector = if (indeterminate) Icons.Filled.Remove else Icons.Filled.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/** 带标签的复选行（含最小触控区）。 */
@Composable
fun RowScope.AppCheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supporting: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        AppCheckbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        Spacer(Modifier.width(Dimens.spaceM))
        androidx.compose.foundation.layout.Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ───────────────────────────── AppRadio ─────────────────────────────

/**
 * 单选：20dp，选中 `primary` 外环 + 内点。
 */
@Composable
fun AppRadio(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = appColors()
    val interaction = remember { MutableInteractionSource() }
    val ringColor = if (selected) colors.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

    Box(
        modifier = modifier
            .size(ChoiceSpec.Box.dp)
            .appStateLayer(interaction, overlayColor = colors.primary, shape = RoundedCornerShape(50))
            .selectable(
                selected = selected,
                onClick = onClick,
                enabled = enabled,
                role = Role.RadioButton,
                interactionSource = interaction,
                indication = rememberNoInkIndication(),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(ChoiceSpec.Box.dp)) {
            val stroke = (size.width * 0.09f).coerceAtLeast(1.5f)
            drawCircle(color = ringColor, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        }
        if (selected) {
            Canvas(
                modifier = Modifier
                    .size(ChoiceSpec.Box.dp)
                    .padding((ChoiceSpec.Box * 0.28f).dp),
            ) {
                drawCircle(color = ringColor)
            }
        }
    }
}

/** 单选行选项（含可点整行）。 */
@Composable
fun RowScope.AppRadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .selectable(
                selected = selected,
                onClick = onClick,
                enabled = enabled,
                role = Role.RadioButton,
            )
            .padding(vertical = Dimens.spaceS),
    ) {
        AppRadio(selected = selected, onClick = onClick, enabled = enabled)
        Spacer(Modifier.width(Dimens.spaceM))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
    }
}