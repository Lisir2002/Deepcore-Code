package com.deepcode.designsystem.components.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.deepcode.designsystem.components.AppPrimaryButton
import com.deepcode.designsystem.components.AppTextButton
import com.deepcode.designsystem.theme.Dimens
import com.deepcode.designsystem.theme.appColors
import com.deepcode.designsystem.theme.appTokens

/**
 * 浮层子组件——模态决策壳（§6.6.1）：**一个壳，三个语义变体**（confirm/status/notice）。
 *
 * 规则（§6.6 金字塔）：dialog 是打断成本最高的组件，只留给「必须决策」。
 * 回执走 AppToast、引导走 AppBanner、校验走 inline，禁止用 dialog 做操作回执。
 *
 * 共享壳规格：宽 `min(内容, 320dp)`（Expanded 上限 420dp）、圆角 `radiusXL`、
 * 内容边距 24dp、海拔 `surfaceElevated`、遮罩 = M3 默认黑 32%（scrim）。
 * 按钮排列铁律：≤2 钮、取消永远在确认左侧、确认钮可禁用、取消钮永不禁用。
 */
private object DialogSpec {
    val MaxWidth = 320.dp
    val ExpandedMaxWidth = 420.dp
    val IconCircle = 40.dp
}

// ───────────────────────────── 确认 confirm() ─────────────────────────────

/**
 * 破坏性/不可逆操作前置确认。
 * 双钮横排右对齐：文字钮「取消」在左 + 确认钮最靠边；`danger=true` 时确认钮转 danger。
 * `confirmEnabled` 用于「选择做出前禁用确认钮」；取消钮永不禁用。
 */
@Composable
fun AppConfirmDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmText: String,
    dismissText: String = "取消",
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    confirmEnabled: Boolean = true,
) {
    val colors = appColors()
    AppDialogBaseline(
        modifier = modifier,
        onDismiss = onDismiss,
    ) {
        AppDialogTitle(title)
        AppDialogBody(body)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.spaceXL),
            horizontalArrangement = Arrangement.End,
        ) {
            AppTextButton(
                text = dismissText,
                onClick = onDismiss,
            )
            Spacer(Modifier.size(Dimens.spaceS))
            AppPrimaryButton(
                text = confirmText,
                onClick = onConfirm,
                enabled = confirmEnabled,
                danger = danger,
            )
        }
    }
}

// ───────────────────────────── 状态 status() ─────────────────────────────

/** Acknowledgment 确认钮文案与会话状态之间的桥。 */
enum class AppDialogStatusState { Progress, Success, Failure }

/**
 * 进度/成功/失败回执。Progress 只渲染转圈 + 文案**且不出按钮**（防等待中误触）；
 * Success/Failure 出单「知道了」acknowledgement 钮。图标位 = 40dp 圆底 + 白图标。
 */
@Composable
fun AppStatusDialog(
    state: AppDialogStatusState,
    title: String,
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    acknowledgeText: String = "知道了",
) {
    val colors = appColors()
    val statusColor = when (state) {
        AppDialogStatusState.Progress -> colors.primary
        AppDialogStatusState.Success -> colors.success
        AppDialogStatusState.Failure -> colors.danger
    }
    AppDialogBaseline(
        modifier = modifier,
        onDismiss = onDismiss,
    ) {
        if (state != AppDialogStatusState.Progress) {
            // 图标位：40dp 圆底 + 白图标
            Box(
                modifier = Modifier
                    .size(DialogSpec.IconCircle)
                    .background(statusColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (state == AppDialogStatusState.Success) Icons.Filled.Check else Icons.Filled.Close,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(Dimens.iconL),
                )
            }
            Spacer(Modifier.height(Dimens.spaceL))
        } else {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(Dimens.iconL),
                    color = statusColor,
                    strokeWidth = 2.5.dp,
                )
            }
            Spacer(Modifier.height(Dimens.spaceL))
        }
        AppDialogTitle(title, textAlign = Alignment.CenterHorizontally)
        AppDialogBody(message, textAlign = Alignment.CenterHorizontally)
        if (state != AppDialogStatusState.Progress) {
            AppPrimaryButton(
                text = acknowledgeText,
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.spaceXL),
            )
        }
    }
}

// ───────────────────────────── 提示 notice() ─────────────────────────────

/**
 * 纯信息告知（版本说明/条款）。单「知道了」钮或自定义 actions；
 * 可承载富内容槽（正文长文滚动区 `maxHeight = 60%` 屏高）。中性，不着状态色。
 */
@Composable
fun AppNoticeDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    body: String? = null,
    acknowledgeText: String? = "知道了",
    richContent: @Composable ColumnScope.() -> Unit = {},
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    AppDialogBaseline(
        modifier = modifier,
        onDismiss = onDismiss,
    ) {
        AppDialogTitle(title)
        if (body != null) {
            AppDialogBody(body)
            Spacer(Modifier.height(Dimens.spaceM))
        }
        // 富内容槽：长文滚动区（maxHeight = 60% 屏高）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 0.6f * LocalConfiguration.current.screenHeightDp.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Column { richContent() }
        }
        if (acknowledgeText != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.spaceXL),
                horizontalArrangement = Arrangement.End,
            ) {
                AppPrimaryButton(text = acknowledgeText, onClick = onDismiss)
            }
        }
        actions()
    }
}

// ───────────────────────────── 共享壳 ─────────────────────────────

/**
 * 三变体共享的模态壳。宽按内容自适应、上限 320dp；禁业务层裸用 M3 Dialog
 *（lint ForbiddenWindowComponent 拦截），只需选一个语义变体。
 */
@Composable
private fun AppDialogBaseline(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = appTokens()
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
    ) {
        Surface(
            modifier = modifier
                .widthIn(max = DialogSpec.MaxWidth),
            shape = RoundedCornerShape(tokens.radius.sheet),
            color = MaterialTheme.colorScheme.surfaceElevated,
            tonalElevation = 2.dp,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.spaceXL),
                content = content,
            )
        }
    }
}

@Composable
private fun AppDialogTitle(
    text: String,
    textAlign: TextAlign? = null,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
        textAlign = textAlign ?: TextAlign.Start,
    )
    Spacer(Modifier.height(Dimens.spaceS))
}

@Composable
private fun AppDialogBody(
    text: String,
    textAlign: TextAlign? = null,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
        textAlign = textAlign ?: TextAlign.Start,
    )
}