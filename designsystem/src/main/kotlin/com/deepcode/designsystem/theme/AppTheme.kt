package com.deepcode.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/**
 * 全 App 唯一的主题入口。
 *
 * 页面里只允许写 AppTheme { ... }，不允许自己组装 MaterialTheme——
 * 一旦各页面自己配主题，深色模式必然有页面漏掉。
 *
 * v4.2.1（P1）：
 *  - 品牌色（AppBrandTokens）经 AppColors.fromBrand 派生语义色，首次填满 M3 全槽位（§9.1）；
 *  - 移除 dynamicColor（设计定稿：品牌是硬编码 source of truth，D8）；
 *  - AppCompositionLocal 同时提供 LocalAppTokens（全量语义）与 LocalAppColors（兼容旧业务色读取）。
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val tokens = if (darkTheme) DarkAppTokens else LightAppTokens
    val brand = if (darkTheme) DarkBrand else LightBrand

    CompositionLocalProvider(
        LocalAppTokens provides tokens,
        LocalAppColors provides tokens.colors,
    ) {
        MaterialTheme(
            colorScheme = appColorScheme(tokens.colors, brand),
            typography = appTypography(tokens.type),
            shapes = AppShapes,
            content = content,
        )
    }
}

// ─────────────────────────── M3 桥接（§9.1 权威映射表）───────────────────────────

/**
 * 把语义面板填进 Material3 ColorScheme 全槽位。
 * 组件内部读 MaterialTheme ≡ 读语义层（读法见 9.2）；缝隙由 M3MirrorTest（T8.2/12.4）锁死。
 */
private fun appColorScheme(colors: AppColors, brand: AppBrandTokens): ColorScheme {
    val scheme = if (brand.surface == LightBrand.surface) lightColorScheme() else darkColorScheme()
    return scheme.copy(
        primary = colors.primary,
        onPrimary = colors.onPrimary,
        primaryContainer = colors.primaryContainer,
        onPrimaryContainer = colors.onPrimaryContainer,
        secondary = desaturate(colors.primary, colors.surfaceVariant),
        onSecondary = colors.onPrimary,
        secondaryContainer = colors.surfaceVariant,
        onSecondaryContainer = colors.textPrimary,
        tertiary = colors.thinking,
        onTertiary = colors.surface,
        tertiaryContainer = colors.successContainer,
        onTertiaryContainer = colors.success,
        background = colors.surface,
        onBackground = colors.textPrimary,
        surface = colors.surface,
        onSurface = colors.textPrimary,
        surfaceVariant = colors.surfaceVariant,
        onSurfaceVariant = colors.textSecondary,
        surfaceTint = colors.primary,
        inverseSurface = colors.textPrimary,
        inverseOnSurface = colors.surface,
        inversePrimary = colors.primary,
        error = colors.danger,
        onError = colors.surface,
        errorContainer = colors.dangerContainer,
        onErrorContainer = colors.danger,
        outline = colors.border,
        outlineVariant = colors.divider,
        scrim = colors.border,
        surfaceBright = colors.surface,
        surfaceDim = colors.surfaceVariant,
        surfaceContainerLowest = colors.surface,
        surfaceContainerLow = colors.surfaceVariant,
        surfaceContainer = colors.surfaceVariant,
        surfaceContainerHigh = colors.surfaceElevated,
        surfaceContainerHighest = colors.surfaceElevated,
    )
}

/** 简易去饱和：把 a 向中性底色 b 混 50%，用于 secondary（§9.1：style 包不单独控制 secondary*）。 */
private fun desaturate(a: Color, b: Color): Color = Color(
    red = (a.red + b.red) / 2f,
    green = (a.green + b.green) / 2f,
    blue = (a.blue + b.blue) / 2f,
    alpha = a.alpha,
)

private fun appTypography(t: AppTypographyTokens): Typography = Typography(
    displaySmall = t.title,
    headlineMedium = t.title,
    titleLarge = t.title,
    titleMedium = t.sectionHeader,
    titleSmall = t.sectionHeader,
    bodyLarge = t.body,
    bodyMedium = t.body,
    bodySmall = t.caption,
    labelLarge = t.label,
    labelMedium = t.caption,
    labelSmall = t.caption,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(Dimens.radiusS),
    small = RoundedCornerShape(Dimens.radiusS),
    medium = RoundedCornerShape(Dimens.radiusM),
    large = RoundedCornerShape(Dimens.radiusL),
    extraLarge = RoundedCornerShape(Dimens.radiusXL),
)