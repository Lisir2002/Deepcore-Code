package com.deepcode.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * 品牌 Primitive 令牌（§3.2）：黑白灰主调 · 蓝紫点缀 · 红绿状态。
 *
 * 这一层只承载**原始取值**（十六进制定稿），不做语义；语义面板 `AppColors`
 * 由它派生（`AppColors.fromBrand`）。明暗两套各一份，见 [LightBrand] / [DarkBrand]。
 *
 * 为什么要拆这一层：风格包（T8.2/T8.3）覆盖的是**语义层**，品牌 Primitive 是
 * 编译期定稿、不改的底座；拆开后语义覆盖不会污染品牌灰阶基线。
 *
 * 对比度约束（§3.2）设计时已锁死，由 `ContrastMatrixTest`（12.2）回归兜底。
 */
@Immutable
data class AppBrandTokens(
    // ── 品牌：蓝 = 可操作 ──
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    // ── 表面（灰阶冷色，承担 95% 界面）──
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceElevated: Color,
    // ── 文本 ──
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textInverse: Color,
    // ── 边线 ──
    val divider: Color,
    val border: Color,
    // ── 状态：红绿黄 = 状态 ──
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val info: Color,        // v1 复用 primary（预留槽位）
    // ── AI 身份：紫 ──
    val thinking: Color,
    val thinkingContainer: Color,
    // ── 代码块 ──
    val codeSurface: Color,
    val codeBorder: Color,
)

/**
 * 亮色板（§3.2 定稿）。暗色上所有状态/品牌色已按「去饱和」规则调过（见 DarkBrand）。
 *
 * 落位：surfaceElevated 亮色同 surface（靠阴影表达海拔，§3.5 之外见 3.2 海拔表）。
 */
val LightBrand = AppBrandTokens(
    primary = Color(0xFF2563EB),           // 蓝，白字 5.1:1
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEFF3FF),
    onPrimaryContainer = Color(0xFF1E3A8A),
    surface = Color(0xFFFFFFFF),           // gray-0
    surfaceVariant = Color(0xFFF4F5F7),    // gray-50
    surfaceElevated = Color(0xFFFFFFFF),   // gray-0
    textPrimary = Color(0xFF17181C),       // ink-900
    textSecondary = Color(0xFF5C6270),     // gray-600
    textTertiary = Color(0xFF9CA1AC),      // gray-400
    textInverse = Color(0xFFFFFFFF),
    divider = Color(0xFFE8EAEF),           // gray-100
    border = Color(0xFFD9DCE3),            // gray-200
    success = Color(0xFF16A34A),
    successContainer = Color(0xFFE9F9EF),
    warning = Color(0xFFD97706),
    warningContainer = Color(0xFFFDF3E3),
    danger = Color(0xFFDC2626),
    dangerContainer = Color(0xFFFDECEC),
    info = Color(0xFF2563EB),              // = primary
    thinking = Color(0xFF7C3AED),          // 紫：AI 身份
    thinkingContainer = Color(0xFFF3EFFE),
    codeSurface = Color(0xFFF4F5F7),       // gray-50
    codeBorder = Color(0xFFE8EAEF),        // gray-100
)

/**
 * 暗色板（§3.2 定稿）。深色下把「抬升变亮」当海拔（阴影不可见），
 * 状态/品牌色均比亮板**去饱和**，避免暗底光学振动。
 */
val DarkBrand = AppBrandTokens(
    primary = Color(0xFF7A93FF),           // 亮蓝，深字 6.0:1
    onPrimary = Color(0xFF0D1230),
    primaryContainer = Color(0xFF1B2A5E),
    onPrimaryContainer = Color(0xFFC7D2FE),
    surface = Color(0xFF131417),           // gray-900
    surfaceVariant = Color(0xFF1C1E23),    // gray-850
    surfaceElevated = Color(0xFF2B2E35),   // 灰阶 +3~4 档，抬升变亮
    textPrimary = Color(0xFFF2F3F5),       // ink-50
    textSecondary = Color(0xFFA6ABB7),     // ink-300
    textTertiary = Color(0xFF6B7180),      // ink-500
    textInverse = Color(0xFF17181C),
    divider = Color(0xFF2A2D34),           // gray-800
    border = Color(0xFF353943),            // gray-750
    success = Color(0xFF4ADE80),
    successContainer = Color(0xFF12291B),
    warning = Color(0xFFFBBF24),
    warningContainer = Color(0xFF2E2210),
    danger = Color(0xFFF87171),
    dangerContainer = Color(0xFF331A1A),
    info = Color(0xFF7A93FF),              // = primary
    thinking = Color(0xFFA78BFA),          // 紫（去饱和）
    thinkingContainer = Color(0xFF221B38),
    codeSurface = Color(0xFF1C1E23),       // gray-850
    codeBorder = Color(0xFF353943),        // gray-750
)