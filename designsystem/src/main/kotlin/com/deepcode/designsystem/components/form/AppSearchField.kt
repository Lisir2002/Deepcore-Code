package com.deepcode.designsystem.components.form

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import com.deepcode.designsystem.components.AppTextField
import com.deepcode.designsystem.components.AppTextFieldVariant

/**
 * 搜索框（§6.7.2）：`AppTextField` 变体——`Outlined` + 全圆（`radiusXL`）+ leading search
 * 图标 + trailing clear。M2 会话列表直接复用。
 */
@Composable
fun AppSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索",
    enabled: Boolean = true,
    leadingIcon: ImageVector = Icons.Filled.Search,
) {
    AppTextField(
        label = "",
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        variant = AppTextFieldVariant.Outlined,
        leadingIcon = leadingIcon,
        showClearWhenFocused = true,
        shape = RoundedCornerShape(percent = 50),
    )
}