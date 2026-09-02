package com.deepcode.designsystem.components.form

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.deepcode.designsystem.behavior.appStateLayer
import com.deepcode.designsystem.components.AppText
import com.deepcode.designsystem.theme.AppTextStyle
import com.deepcode.designsystem.theme.AppTextTone
import com.deepcode.designsystem.theme.Dimens
import com.deepcode.designsystem.theme.appColors

/**
 * 设置行内条目（§6.7 选择控件族之上的"行条目"泛型）。
 *
 * 高频设置项（模型参数 / 快捷键 / 数据开关…）通常形态一致：左侧 title +
 * 可选 supporting 说明，右侧一个控件槽（Switch / 分段 / chevron）。
 * 本组件把这种"行"收口：业务层只需填 label + trailing，不再各写各的 Row。
 *
 * 约定：
 *   - `onClick != null && trailing == null`：右侧自动补 chevron，整行可点（跳转类条目）。
 *   - `onClick == null && trailing != null`：整行不可点，右侧控件（如 Switch）独占交互。
 *   - 两者都不为 null：允许，但调用方需自行控制点击/控件焦点不打架；通常不入。
 *   - `enabled` 统一收口 disabled 态的色调与点击。
 */
@Composable
fun AppSettingRow(
    label: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = appColors()
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .appStateLayer(interaction, shape = RoundedCornerShape(Dimens.radiusS))
            .then(
                if (onClick != null && enabled) {
                    Modifier.clickable(
                        enabled = enabled,
                        interactionSource = interaction,
                        indication = null,
                    ) { onClick() }
                } else {
                    Modifier
                },
            )
            .padding(vertical = Dimens.spaceS),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    label,
                    style = AppTextStyle.Body,
                    tone = if (enabled) AppTextTone.Default else AppTextTone.Muted,
                )
                if (supporting != null) {
                    AppText(
                        supporting,
                        style = AppTextStyle.Caption,
                        tone = AppTextTone.Muted,
                    )
                }
            }
            Spacer(Modifier.width(Dimens.spaceS))
            when {
                // 跳转类条目：无显式 trailing 时默认 chevron（复用入口列表语义）
                trailing != null -> trailing()
                onClick != null -> {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(Dimens.iconS),
                    )
                }
            }
        }
    }
}