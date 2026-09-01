package com.deepcode.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deepcode.designsystem.behavior.appStateLayer
import com.deepcode.designsystem.theme.Dimens
import com.deepcode.designsystem.theme.TypeScale
import com.deepcode.designsystem.theme.appColors

// ─────────────────────────── 状态态组件 ───────────────────────────

@Composable
fun AppLoadingIndicator(modifier: Modifier = Modifier, message: String? = null) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(Dimens.iconL))
        if (message != null) {
            Spacer(Modifier.height(Dimens.spaceM))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun AppEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: ImageVector? = Icons.Filled.Inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.spaceXL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(Dimens.spaceL))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (message != null) {
            Spacer(Modifier.height(Dimens.spaceS))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Dimens.spaceL))
            AppPrimaryButton(text = actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun AppErrorState(
    message: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    retryable: Boolean = true,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.spaceXL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(Dimens.spaceL))
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (detail != null) {
            Spacer(Modifier.height(Dimens.spaceS))
            AppCodeBlock(text = detail, maxLines = 6)
        }
        if (retryable && onRetry != null) {
            Spacer(Modifier.height(Dimens.spaceL))
            AppPrimaryButton(text = "重试", onClick = onRetry)
        }
    }
}

// ─────────────────────────── 基础组件 ───────────────────────────

@Composable
fun AppPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    danger: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier.appStateLayer(interaction),
        enabled = enabled,
        interactionSource = interaction,
        shape = RoundedCornerShape(Dimens.radiusM),
        contentPadding = PaddingValues(horizontal = Dimens.spaceL, vertical = Dimens.spaceS),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            contentColor = if (danger) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(Dimens.iconS))
            Spacer(Modifier.size(Dimens.spaceXS))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun AppSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.appStateLayer(interaction),
        enabled = enabled,
        interactionSource = interaction,
        shape = RoundedCornerShape(Dimens.radiusM),
        contentPadding = PaddingValues(horizontal = Dimens.spaceL, vertical = Dimens.spaceS),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun AppTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    TextButton(
        onClick = onClick,
        modifier = modifier.appStateLayer(interaction),
        enabled = enabled,
        interactionSource = interaction,
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentPadding: PaddingValues = PaddingValues(Dimens.spaceM),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Dimens.radiusM)
    val colors = CardDefaults.elevatedCardColors(containerColor = containerColor)
    val body: @Composable ColumnScope.() -> Unit = {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
    if (onClick != null) {
        val interaction = remember { MutableInteractionSource() }
        ElevatedCard(
            onClick = onClick,
            modifier = modifier.appStateLayer(interaction),
            shape = shape,
            colors = colors,
            interactionSource = interaction,
        ) { body() }
    } else {
        ElevatedCard(modifier = modifier, shape = shape, colors = colors) { body() }
    }
}

/** 代码块 / 命令输出。全 App 只有这一处定义代码长什么样。 */
@Composable
fun AppCodeBlock(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    tintBackground: Boolean = true,
) {
    val colors = appColors()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.radiusS),
        color = if (tintBackground) colors.codeSurface else Color.Transparent,
        border = if (tintBackground) {
            androidx.compose.foundation.BorderStroke(1.dp, colors.codeBorder)
        } else null,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(Dimens.spaceS),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = TypeScale.code,
                lineHeight = TypeScale.codeLineHeight,
            ),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 状态标签：工具状态、风险等级、模型标签都用它，样式不会各写一套。 */
@Composable
fun AppStatusChip(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    icon: ImageVector? = null,
    busy: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.spaceS, vertical = Dimens.spaceXXS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (busy) {
                // 运行中态：小号 loading 旋转指示，替代图标（§4.1 loading 态）
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 2.dp,
                    color = contentColor,
                )
                Spacer(Modifier.size(Dimens.spaceXXS))
            } else if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp))
                Spacer(Modifier.size(Dimens.spaceXXS))
            }
            Text(text = text, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun AppSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spaceL, vertical = Dimens.spaceS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** 内容居中且限宽，平板/横屏时不会拉成一条长线。 */
@Composable
fun AppLimitedWidthContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(modifier = Modifier.widthIn(max = Dimens.maxContentWidth)) {
            content()
        }
    }
}
