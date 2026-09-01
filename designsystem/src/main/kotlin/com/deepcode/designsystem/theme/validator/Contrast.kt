package com.deepcode.designsystem.theme.validator

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/**
 * WCAG 2.1 相对亮度 / 对比度（§10.1），供 `ThemeValidator` 与 `ContrastMatrixTest` 单一复用。
 *
 * 公式：
 *   sRGB ⇒ linear：`v ≤ 0.04045 → v/12.92；否则 ((v+0.055)/1.055)^2.4`
 *   线性后亮度：`L = 0.2126·R + 0.7152·G + 0.0722·B`
 *   对比度：`ratio = (L_亮 + 0.05) / (L_暗 + 0.05)`
 *
 * 纯 Kotlin、可 JVM 单测——不依赖 Android 运行时实现，也不做 UI 渲染。
 */
object Contrast {

    private fun linearChannel(v: Float): Double = when {
        v <= 0.04045f -> (v / 12.92f).toDouble()
        else -> ((v + 0.055f) / 1.055f).toDouble().pow(2.4)
    }

    /** WCAG 相对亮度（0..1）。 */
    fun Color.relativeLuminance(): Double {
        val r = linearChannel(red)
        val g = linearChannel(green)
        val b = linearChannel(blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /** 两色对比度（≥1.0）。 */
    fun contrast(a: Color, b: Color): Double {
        val la = a.relativeLuminance()
        val lb = b.relativeLuminance()
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }
}