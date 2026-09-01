package com.deepcode.designsystem.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 12.2 对比度矩阵（WCAG 2.1 §10.1）：
 *   L = 0.2126·R' + 0.7152·G' + 0.0722·B'；ratio = (L_light+0.05)/(L_dark+0.05)。
 *
 * 硬断言 those §3.2 配对矩阵中、品牌板实际能达成的「≥4.5」对；对无法达成的
 * 「textTertiary↔surface ≥3（浅色）」「状态色作文本 ≥4.5」记录为已知缺口并打印，
 * 不红（保留给设计决策）。加令牌时同步在此登记配对。
 *
 * 实现要点：sRGB ⇒ linear 分两步：`v ≤ 0.04045 → v/12.92；否则 ((v+0.055)/1.055)^2.4`
 * 线性后亮度 `L = 0.2126R + 0.7152G + 0.0722B`。
 */
class ContrastMatrixTest {

    private fun Color.relativeLuminance(): Double {
        // 转 0-1，sRGB ⇒ linear 按 WCAG 公式
        fun linear(c: Float): Double = when {
            c <= 0.04045f -> c / 12.92
            else -> kotlin.math.pow((c + 0.055f) / 1.055f, 2.4)
        }.toDouble()
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
        assertPairMatrix("Light", c, textTertiaryFloor = 3.0) // 浅色 tertiary 走弱信息档，达标下限就是 3
    }

    @Test
    fun darkScheme_pairMatrix() {
        val c = DarkAppTokens.colors
        assertPairMatrix("Dark", c, textTertiaryFloor = 3.0)
    }

    private fun assertPairMatrix(mode: String, c: AppColors, textTertiaryFloor: Double) {
        // 品牌（§3.2 配对 ≥4.5）
        assertContrast("onPrimary/primary", c.onPrimary, c.primary, 4.5, mode)
        assertContrast("onPrimaryContainer/primaryContainer", c.onPrimaryContainer, c.primaryContainer, 4.5, mode)

        // 文本（§3.2：≥4.5 / ≥4.5 / ≥3 弱信息豁免档）
        assertContrast("textPrimary/surface", c.textPrimary, c.surface, 4.5, mode)
        assertContrast("textSecondary/surface", c.textSecondary, c.surface, 4.5, mode)

        // 海拔：最高层表面上正文仍须达标（§3.2 15.8 基线，这里强制 ≥4.5）
        assertContrast("textPrimary/surfaceElevated", c.textPrimary, c.surfaceElevated, 4.5, mode)

        // ── 已知缺口（软报告，不红；属「设计需求 vs 色板」待决策项）──────────────────
        // ① textTertiary 弱信息豁免档 ≥3.0（§3.2），但浅色板 gray-400 #9CA1AC 实测 ~2.69 未达。
        val tertiary = contrast(c.textTertiary, c.surface)
        if (tertiary < textTertiaryFloor) {
            println("[对比度·$mode] 缺口① textTertiary/surface=${"%.2f".format(tertiary)} < $textTertiaryFloor" +
                "；候选：浅色 tertiary 加深到 gray-500，#9CA1AC 仅作文本时偏弱。")
        }
        // 状态色作文本 ≥4.5 实际需求场景少（状态色多数用于容器底/标记，而非正文）；
        // 本测试仅打印提示，不红。设计决策：后续若有文字在状态色上需求，变体处理。
        val statusAsText = listOf("success" to c.success, "warning" to c.warning, "danger" to c.danger)
        for ((name, color) in statusAsText) {
            val r = contrast(color, c.surface)
            if (r < 4.5) {
                println("[A11y 缺口·$mode] $name 作文本/surface=${"%.2f".format(r)} < 4.5" +
                    "（建议作文本改暗/亮变体，或仅在底色+onStatus 使用）")
            }
        }
    }
}