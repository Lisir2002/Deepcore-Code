package com.deepcode.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Material3 桥接（§9.1 权威映射表）：把语义板填进 ColorScheme 全槽位 + Typography。
 *
 * 规则：组件内部读 MaterialTheme ≡ 读语义层（§9.2）；风格包覆盖的是语义层，
 * shell 层（M3 槽位填充）只按 §9.1 定稿映射铺一圈。返回的 [Domesticated] 与源槽位
 * 完全同步，由 M3MirrorTest（12.4）用反射锁死「铺进来什么就体现为什么」。
 */
@Immutable
data class Domesticated(
    val scheme: ColorScheme,
    val typography: Typography,
)

object AppThemeBridge {

    /** desaturate：把 a 向中性底色 b 混 50%，用于 secondary（§9.1：风格包不单独控制 secondary*）。 */
    private fun desaturate(a: Color, b: Color): Color = Color(
        red = (a.red + b.red) / 2f,
        green = (a.green + b.green) / 2f,
        blue = (a.blue + b.blue) / 2f,
        alpha = a.alpha,
    )

    /** 语义板 + 字型 → M3 全槽位（§9.1）。 */
    fun domesticate(colors: AppColors, type: AppTypographyTokens, isDark: Boolean): Domesticated {
        val scheme = (if (isDark) darkColorScheme() else lightColorScheme()).copy(
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
        return Domesticated(scheme = scheme, typography = typography(type))
    }

    /** 六角色 → M3 Typography，供 MaterialTheme.typography 读取（组件内部 ≈ 语义字型）。 */
    fun typography(type: AppTypographyTokens): Typography = Typography(
        displaySmall = type.title,
        headlineMedium = type.title,
        titleLarge = type.title,
        titleMedium = type.sectionHeader,
        titleSmall = type.sectionHeader,
        bodyLarge = type.body,
        bodyMedium = type.body,
        bodySmall = type.caption,
        labelLarge = type.label,
        labelMedium = type.caption,
        labelSmall = type.caption,
    )
}