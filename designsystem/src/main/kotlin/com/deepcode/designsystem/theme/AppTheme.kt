package com.deepcode.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle

/**
 * Material3 的 ColorScheme 管不了"diff 的增删色""工具运行状态色""代码块底色"这类
 * 业务语义色。单独放一层，避免这些颜色散落到各页面里各写各的。
 */
data class AppColors(
    val diffAdd: Color,
    val diffRemove: Color,
    val toolRunning: Color,
    val toolSuccess: Color,
    val toolFailed: Color,
    val toolAwaiting: Color,
    val thinking: Color,
    val codeSurface: Color,
    val codeBorder: Color,
)

private val LightAppColors = AppColors(
    diffAdd = Color(0xFFDCFCE7),
    diffRemove = Color(0xFFFEE2E2),
    toolRunning = Color(0xFF2563EB),
    toolSuccess = Color(0xFF15803D),
    toolFailed = Color(0xFFB91C1C),
    toolAwaiting = Color(0xFFB45309),
    thinking = Color(0xFF6B7280),
    codeSurface = Color(0xFFF6F7F9),
    codeBorder = Color(0xFFE3E6EA),
)

private val DarkAppColors = AppColors(
    diffAdd = Color(0xFF14532D),
    diffRemove = Color(0xFF7F1D1D),
    toolRunning = Color(0xFF60A5FA),
    toolSuccess = Color(0xFF4ADE80),
    toolFailed = Color(0xFFF87171),
    toolAwaiting = Color(0xFFFBBF24),
    thinking = Color(0xFF9CA3AF),
    codeSurface = Color(0xFF1C1F24),
    codeBorder = Color(0xFF2E333B),
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

@Composable
@ReadOnlyComposable
fun appColors(): AppColors = LocalAppColors.current

private val LightColorScheme = lightColorScheme()
private val DarkColorScheme = darkColorScheme()

private val AppTypography = Typography(
    bodyLarge = TextStyle(fontSize = TypeScale.bodyLarge),
    bodyMedium = TextStyle(fontSize = TypeScale.bodyMedium),
    bodySmall = TextStyle(fontSize = TypeScale.bodySmall),
    titleLarge = TextStyle(fontSize = TypeScale.titleLarge),
    titleMedium = TextStyle(fontSize = TypeScale.titleMedium),
    labelLarge = TextStyle(fontSize = TypeScale.labelLarge),
    labelMedium = TextStyle(fontSize = TypeScale.labelMedium),
    labelSmall = TextStyle(fontSize = TypeScale.labelSmall),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(Dimens.radiusS),
    small = RoundedCornerShape(Dimens.radiusS),
    medium = RoundedCornerShape(Dimens.radiusM),
    large = RoundedCornerShape(Dimens.radiusL),
    extraLarge = RoundedCornerShape(Dimens.radiusXL),
)

/**
 * 全 App 唯一的主题入口。
 *
 * 页面里只允许写 AppTheme { ... }，不允许自己组装 MaterialTheme——
 * 一旦各页面自己配主题，深色模式必然有页面漏掉。
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val appColors = if (darkTheme) DarkAppColors else LightAppColors

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
