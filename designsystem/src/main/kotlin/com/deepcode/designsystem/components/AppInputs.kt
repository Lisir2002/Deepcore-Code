package com.deepcode.designsystem.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.deepcode.designsystem.behavior.appStateLayer
import com.deepcode.designsystem.theme.AppTextStyle
import com.deepcode.designsystem.theme.AppTextTone
import com.deepcode.designsystem.theme.Dimens
import com.deepcode.designsystem.theme.appColors
import com.deepcode.designsystem.theme.toColor
import com.deepcode.designsystem.theme.toTextStyle

// ─────────────────────────── 文本 ───────────────────────────
//
// 业务层禁止 import androidx.compose.material3.Text（被 :lint 的 DirectMaterial3Usage 拦截），
// 所以这里把"任意文本"封成一个 App* 组件。样式通过语义枚举表达，业务层不碰 MaterialTheme。
//
// AppTextStyle / AppTextTone 及其 @Composable 映射（toTextStyle / toColor）现统一收口在
// theme/TypeRoles.kt（§3.3），此处仅使用，不再各自定义。

/**
 * 全 App 唯一可直接摆放的"裸文本"。页面不得自己写 Text——间距/字号/色调的统一
 * 只在这一处发生。
 */
@Composable
fun AppText(
    text: String,
    modifier: Modifier = Modifier,
    style: AppTextStyle = AppTextStyle.Body,
    tone: AppTextTone = AppTextTone.Default,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(
        text = text,
        modifier = modifier,
        style = style.toTextStyle(),
        color = tone.toColor(),
        maxLines = maxLines,
        overflow = overflow,
    )
}

// ─────────────────────────── 输入框 ───────────────────────────

/**
 * 输入框形态（§6.7.1）：`Outlined` 默认（密集表单低强调）/ `Filled` 高强调关键项/ `Bare` 无框特例（AppInputBar 内部）。
 */
enum class AppTextFieldVariant { Outlined, Filled }

/**
 * 统一的输入框（§6.7.1 完整规范）。模型对齐 M3 text field：
 * 常态 1dp 描边 → focused/error 2dp（primary/danger）；浮动标签；支持 leading/trailing、
 * prefix/suffix、helper/error 附属。业务层无需 import material3。
 */
@Composable
fun AppTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    placeholder: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    errorText: String? = null,
    supportingText: String? = null,
    helperText: String? = null,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    variant: AppTextFieldVariant = AppTextFieldVariant.Outlined,
    leadingIcon: ImageVector? = null,
    showClearWhenFocused: Boolean = false,
    prefix: String? = null,
    suffix: String? = null,
    shape: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.RoundedCornerShape(Dimens.radiusM),
) {
    val colors = appColors()
    val effectiveSupporting = when {
        isError -> errorText
        !helperText.isNullOrBlank() -> helperText
        else -> supportingText
    }

    // ---- trailing 槽：error 图标优先（M3 惯例），其后才是清空按钮 ----
    val trailing: (@Composable () -> Unit)? = when {
        isError -> {
            {
                Icon(
                    imageVector = Icons.Filled.Error,
                    contentDescription = "输入有误",
                    tint = colors.danger,
                )
            }
        }
        showClearWhenFocused && value.isNotEmpty() -> {
            {
                val clearInteraction = remember { MutableInteractionSource() }
                IconButton(
                    onClick = { onValueChange("") },
                    interactionSource = clearInteraction,
                    modifier = Modifier.size(Dimens.minTouchTarget).appStateLayer(clearInteraction),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = "清空",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        else -> null
    }

    // ---- 统一的参数集中到公共 lambda，供两种形态复用 ----
    if (variant == AppTextFieldVariant.Outlined) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            isError = isError,
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            supportingText = effectiveSupporting?.let { { Text(it) } },
            singleLine = singleLine,
            maxLines = maxLines,
            keyboardOptions = keyboardOptions,
            leadingIcon = leadingIcon?.let { icon ->
                { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            },
            trailingIcon = trailing,
            prefix = prefix?.let { { Text(it) } },
            suffix = suffix?.let { { Text(it) } },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (isError) colors.danger else colors.primary,
                errorBorderColor = colors.danger,
                unfocusedBorderColor = colors.border,
                focusedLabelColor = if (isError) colors.danger else colors.primary,
                errorLabelColor = colors.danger,
                errorSupportingTextColor = colors.danger,
            ),
            shape = shape,
        )
    } else {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            isError = isError,
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            supportingText = effectiveSupporting?.let { { Text(it) } },
            singleLine = singleLine,
            maxLines = maxLines,
            keyboardOptions = keyboardOptions,
            leadingIcon = leadingIcon?.let { icon ->
                { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            },
            trailingIcon = trailing,
            prefix = prefix?.let { { Text(it) } },
            suffix = suffix?.let { { Text(it) } },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                errorContainerColor = colors.dangerContainer,
                focusedLabelColor = if (isError) colors.danger else colors.primary,
                errorLabelColor = colors.danger,
                errorSupportingTextColor = colors.danger,
            ),
            shape = shape,
        )
    }
}

// ─────────────────────────── 开关 ───────────────────────────

/**
 * "受信任 server"等布尔开关。封装 material3 Switch，受信任态用主色强调。
 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier.appStateLayer(interaction),
        interactionSource = interaction,
        colors = SwitchDefaults.colors(
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}
