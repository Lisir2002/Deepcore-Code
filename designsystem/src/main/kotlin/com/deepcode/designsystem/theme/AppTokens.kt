package com.deepcode.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * 语义令牌面板（§3.1）：具名属性即令牌——加令牌 = 加属性 + 明暗值 + 配对声明 + 单测。
 * 正确用法由编译期保证，不再有"拼错令牌名"的空间。
 *
 * 分六组：品牌 / 表面 / 文本 / 边线 / 状态 / 业务保留。业务组（diff/tool/thinking/code）
 * 是 Material3 管不了的语义色，仍归本层统一。
 */
@Immutable
data class AppColors(
    // 品牌
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    // 表面
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceElevated: Color,
    // 文本
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textInverse: Color,
    // 边线
    val divider: Color,
    val border: Color,
    // 状态
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val info: Color,
    // 业务保留（聊天场景）
    val diffAdd: Color,
    val diffRemove: Color,
    val toolRunning: Color,
    val toolSuccess: Color,
    val toolFailed: Color,
    val toolAwaiting: Color,
    val thinking: Color,
    val codeSurface: Color,
    val codeBorder: Color,
) {
    companion object {
        /** 从品牌 Primitive 板派生语义面板（§3.2 业务色映射：tool→primary/status 组）。 */
        fun fromBrand(b: AppBrandTokens): AppColors = AppColors(
            primary = b.primary,
            onPrimary = b.onPrimary,
            primaryContainer = b.primaryContainer,
            onPrimaryContainer = b.onPrimaryContainer,
            surface = b.surface,
            surfaceVariant = b.surfaceVariant,
            surfaceElevated = b.surfaceElevated,
            textPrimary = b.textPrimary,
            textSecondary = b.textSecondary,
            textTertiary = b.textTertiary,
            textInverse = b.textInverse,
            divider = b.divider,
            border = b.border,
            success = b.success,
            successContainer = b.successContainer,
            warning = b.warning,
            warningContainer = b.warningContainer,
            danger = b.danger,
            dangerContainer = b.dangerContainer,
            info = b.info,
            // 业务保留 → 语义兜底（风格包可另覆盖，本轮不开放）
            diffAdd = b.successContainer,
            diffRemove = b.dangerContainer,
            toolRunning = b.primary,
            toolSuccess = b.success,
            toolFailed = b.danger,
            toolAwaiting = b.warning,
            thinking = b.thinking,
            codeSurface = b.codeSurface,
            codeBorder = b.codeBorder,
        )
    }
}

/** 字体令牌 + 六角色定稿（§3.3）。角色即令牌：页面选角色，不自由组合字号/字重。 */
@Immutable
data class AppTypographyTokens(
    val fontSans: FontFamily,
    val fontMono: FontFamily,
    val title: TextStyle,
    val sectionHeader: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val code: TextStyle,
) {
    companion object {
        /** 由 Dimens.TypeScale（存量字号源）派生六角色。基线：标题行高 1.2 / 正文 1.4 / 代码 1.5。 */
        fun fromTypeScale(scale: TypeScale): AppTypographyTokens {
            val sans = FontFamily.SansSerif
            val mono = FontFamily.Monospace
            return AppTypographyTokens(
                fontSans = sans,
                fontMono = mono,
                title = TextStyle(fontFamily = sans, fontSize = scale.titleLarge, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
                sectionHeader = TextStyle(fontFamily = sans, fontSize = scale.titleMedium, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
                body = TextStyle(fontFamily = sans, fontSize = scale.bodyMedium, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
                label = TextStyle(fontFamily = sans, fontSize = scale.labelLarge, lineHeight = 17.sp, fontWeight = FontWeight.Medium),
                caption = TextStyle(fontFamily = sans, fontSize = scale.labelSmall, lineHeight = 13.sp, fontWeight = FontWeight.Normal),
                code = TextStyle(fontFamily = mono, fontSize = scale.code, lineHeight = scale.codeLineHeight, fontWeight = FontWeight.Normal),
            )
        }
    }
}

/**
 * 动效档位（§3.5 tween 兜底轨）。spring 物理双轨是行为层（T8.5）的输入，
 * 语意层只载固定时长 + 缓动；reduce motion 时由 MotionResolver 直切。
 */
@Immutable
data class AppMotion(
    val fast: Duration,
    val normal: Duration,
    val slow: Duration,
    val easingStandard: Easing,
    val easingEmphasized: Easing,
)

/** 单套 AppTokens 聚合（含圆角；运行时由 ThemeSpec/spec 承载，见 AppThemeSpec.kt）。 */
@Immutable
data class AppTokens(
    val colors: AppColors,
    val type: AppTypographyTokens,
    val motion: AppMotion,
    val radius: AppRadius,
)

internal val LightAppTokens = AppTokens(
    colors = AppColors.fromBrand(LightBrand),
    type = AppTypographyTokens.fromTypeScale(TypeScale),
    motion = appMotionTokens(),
    radius = AppRadiusTokens,
)

internal val DarkAppTokens = AppTokens(
    colors = AppColors.fromBrand(DarkBrand),
    type = AppTypographyTokens.fromTypeScale(TypeScale),
    motion = appMotionTokens(),
    radius = AppRadiusTokens,
)

// 兼容旧入口：business 色读取走 appColors()；新入口走 appTokens() 读全量语义。
val LocalAppColors = staticCompositionLocalOf { LightAppTokens.colors }
val LocalAppTokens = staticCompositionLocalOf { LightAppTokens }

@Composable
@ReadOnlyComposable
fun appColors(): AppColors = LocalAppColors.current

@Composable
@ReadOnlyComposable
fun appTokens(): AppTokens = LocalAppTokens.current

private fun appMotionTokens(): AppMotion = AppMotion(
    fast = 120.milliseconds,
    normal = 220.milliseconds,
    slow = 320.milliseconds,
    easingStandard = FastOutSlowInEasing,
    easingEmphasized = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f),
)