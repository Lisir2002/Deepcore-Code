package com.deepcode.designsystem.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.deepcode.designsystem.theme.AppTextStyle
import com.deepcode.designsystem.theme.AppTextTone
import com.deepcode.designsystem.theme.Dimens
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
 * 单行的 MCP server 配置输入框（URL / 名称等）。封装 OutlinedTextField，
 * 业务层无需 import material3 即可拿到受控输入框。
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
    supportingText: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = supportingText?.let { { Text(it) } },
        isError = isError,
        enabled = enabled,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(Dimens.radiusM),
    )
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
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}
