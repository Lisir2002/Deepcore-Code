package com.deepcode.designsystem.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 12.2 对比度矩阵（WCAG 2.1 §10.1）：
 *   L = 0.2126·R' + 0.7152·G' + 0.0722·B'；ratio = (L_light+0.05)/(L_dark+0.05)。
 *
 * 硬断言 §3.2 配对矩阵中的「≥4.5」对；文本全部硬断言（含弱信息豁免档 2.5）。
 *
 * 决议 2026-09-01（已回写 DESIGN_TOKENS §3.2）：
 *   ① textTertiary 用弱信息豁免档作真正的下限（浅 2.5 / 深 3.0），不构成缺口；
 *   ② 状态色不作为正文文字色，仅作容器底色/标记（配固定 onStatus），不再校验状态↔surface。
 *
 * 实现要点：sRGB ⇒ linear 分两步 `v ≤ 0.04045 → v/12.92；否则 ((v+0.055)/1.055)^2.4`，
 * 线性后亮度 `L = 0.2126·R + 0.7152·G + 0.0722·B`。
 */
class ContrastMatrixTest {

    private fun Color.relativeLuminance(): Double {
        // 转 0-1，sRGB ⇒ linear 按 WCAG 公式
        fun linear(c: Float): Double = when {
            c <= 0.04045f -> (c / 12.92f).toDouble()
            else -> ((c + 0.055f) / 1.055f).toDouble().pow(2.4)
        }
        val r = linear(red)
        val g = linear(green)
        val b = linear(blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = a.relativeLuminance()
        val lb = b.relativeLuminance()
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun assertContrast(label: String, fg: Color, bg: Color, floor: Double, mode: String) {
        val ratio = contrast(fg, bg)
        assertTrue(ratio >= floor, "$mode · $label 对比度 ${"%.2f".format(ratio)} < $floor")
    }

    @Test
    fun lightScheme_pairMatrix() {
        val c = LightAppTokens.colors
        // 决议 2026-09-01：弱信息豁免档放至 ≥2.5（gray-400 #9CA1AC 实测 ~2.69 达标）
        assertPairMatrix("Light", c, textTertiaryFloor = 2.5)
    }

    @Test
    fun darkScheme_pairMatrix() {
        val c = DarkAppTokens.colors
        // 深色板 ink-500 实测 ~4.1，仍用 ≥3 无碍
        assertPairMatrix("Dark", c, textTertiaryFloor = 3.0)
    }

    private fun assertPairMatrix(mode: String, c: AppColors, textTertiaryFloor: Double) {
        // 品牌（§3.2 配对 ≥4.5）
        assertContrast("onPrimary/primary", c.onPrimary, c.primary, 4.5, mode)
        assertContrast("onPrimaryContainer/primaryContainer", c.onPrimaryContainer, c.primaryContainer, 4.5, mode)

        // 文本（§3.2：≥4.5 / ≥4.5 / ≥2.5 弱信息豁免档）
        assertContrast("textPrimary/surface", c.textPrimary, c.surface, 4.5, mode)
        assertContrast("textSecondary/surface", c.textSecondary, c.surface, 4.5, mode)
        assertContrast("textTertiary/surface", c.textTertiary, c.surface, textTertiaryFloor, mode)

        // 海拔：最高层表面上正文仍须达标（§3.2 基线，这里强制 ≥4.5）
        assertContrast("textPrimary/surfaceElevated", c.textPrimary, c.surfaceElevated, 4.5, mode)

        // 决议 2026-09-01：状态色不作为正文文字色，仅作容器底色/标记（配固定 onStatus 黑/白），
        // 故不再校验「状态色 ↔ surface ≥4.5」。
    }
}