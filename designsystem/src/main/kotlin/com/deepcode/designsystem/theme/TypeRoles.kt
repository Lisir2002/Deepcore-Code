package com.deepcode.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Color

/**
 * 文本语义令牌的唯一出口（§3.3）：业务层只选角色，不 comb 字号/字重。
 *
 * 角色与色调保持分离——`AppText(style = Body, tone = Muted)`，色与形正交，
 * 避免 6×5 组合枚举爆炸。
 *
 * 六角色：Title / SectionHeader / Body / Label / Caption / Code。
 * 枚举值由编译期定稿，映射 # AppTypographyTokens。
 */
enum class AppTextStyle {
    Title, SectionHeader, Body, Label, Caption, Code,
}

/** 文本色调，映射到语义色面板。业务层只用枚举，不持有 material3 Color。 */
enum class AppTextTone {
    Default, Muted, Primary, Error, Success, Inverse
}

@Composable
@ReadOnlyComposable
internal fun AppTextStyle.toTextStyle(): TextStyle = when (this) {
    AppTextStyle.Title -> appTokens().type.title
    AppTextStyle.SectionHeader -> appTokens().type.sectionHeader
    AppTextStyle.Body -> appTokens().type.body
    AppTextStyle.Label -> appTokens().type.label
    AppTextStyle.Caption -> appTokens().type.caption
    AppTextStyle.Code -> appTokens().type.code
}

@Composable
@ReadOnlyComposable
internal fun AppTextTone.toColor(): Color = when (this) {
    AppTextTone.Default -> appTokens().colors.textPrimary
    AppTextTone.Muted -> appTokens().colors.textSecondary
    AppTextTone.Primary -> appTokens().colors.primary
    AppTextTone.Error -> appTokens().colors.danger
    AppTextTone.Success -> appTokens().colors.toolSuccess
    AppTextTone.Inverse -> appTokens().colors.textInverse
}