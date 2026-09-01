package com.deepcode.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deepcode.designsystem.behavior.appStateLayer
import com.deepcode.designsystem.theme.Dimens

/**
 * 全 App 唯一的输入栏。
 *
 * 会话页、快速提问弹窗、终端页都用它，输入框高度、圆角、按钮位置必然一致。
 */
@Composable
fun AppInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    onStop: (() -> Unit)? = null,
    placeholder: String = "描述你要做的事…",
    hint: String? = null,
    enabled: Boolean = true,
    maxLines: Int = 5,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            // Ime 避让：adjustResize 下为 no-op（窗口已缩放），非缩放场景自动抬升，自包含不依赖宿主 scaffold。
            .imePadding(),
    ) {
        androidx.compose.material3.HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Dimens.spaceL, vertical = Dimens.spaceXS),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spaceM, vertical = Dimens.spaceS),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Dimens.minTouchTarget),
                enabled = enabled,
                placeholder = {
                    Text(text = placeholder, style = MaterialTheme.typography.bodyMedium)
                },
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(Dimens.radiusL),
                maxLines = maxLines,
                colors = OutlinedTextFieldDefaults.colors(),
            )

            Spacer(Modifier.size(Dimens.spaceS))

            if (onStop != null) {
                val stopInteraction = remember { MutableInteractionSource() }
                IconButton(
                    onClick = onStop,
                    modifier = Modifier.size(Dimens.minTouchTarget).appStateLayer(stopInteraction),
                    interactionSource = stopInteraction,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = "停止",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                val sendInteraction = remember { MutableInteractionSource() }
                IconButton(
                    onClick = onSend,
                    enabled = enabled && value.isNotBlank(),
                    modifier = Modifier.size(Dimens.minTouchTarget).appStateLayer(sendInteraction),
                    interactionSource = sendInteraction,
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowForward,
                        contentDescription = "发送",
                        tint = if (value.isNotBlank() && enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}
