package com.deepcode.designsystem.theme.validator

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnitType
import com.deepcode.designsystem.theme.AppThemeSpec
import com.deepcode.designsystem.theme.AppTokens
import com.deepcode.designsystem.theme.ThemePacks

/**
 * 主题校验器（§7.4「A11y 硬约束 / 可用性软约束」两行），跑在**合并后**的
 * `AppThemeSpec` 上（§7.3 第 5 步）。纯 Kotlin、可 JVM 单测。
 *
 * 职责边界（与 `ThemeJsonCodec` 分工，避免两层重复校验）：
 *   • 结构/值（JSON、schemaVersion、必填、hex、radius 越界）→ `ThemeJsonCodec`（整包拒载）；
 *   • 这里只做**合并后**的语义校验：
 *       - A11y 硬约束（§10.1 对比度配对、§10.2 body 字号下限）→ 该令牌**回退 brand 值**，报告说明；
 *       - 可用性软约束（表面三色差异、divider 边界感）→ **告警保留**，不拒载。
 */
object ThemeValidator {

    /** §7.4 校验产出。 */
    data class Report(
        /** A11y 硬约束失败 → 需回退 brand 值的令牌。key = 落点路径（如 `light.onPrimary`），value = 原因。 */
        val fallbacks: Map<String, String> = emptyMap(),
        /** 可用性软约束 → 告警保留。 */
        val warnings: List<String> = emptyList(),
    )

    /** §10.2 body 字号下限（sp）。低于则回退 brand（A11y 硬约束）。 */
    private const val BODY_FONT_MIN_SP = 13f

    /** §3.2 可用性：三个表面至少有可感差异（低于则视觉糊成一片）。 */
    private const val SURFACE_MIN_RATIO = 1.05

    /** §3.2 可用性：divider 相对 surface 要有边界感。 */
    private const val DIVIDER_MIN_RATIO = 1.1

    fun validate(
        spec: AppThemeSpec,
        brand: AppThemeSpec = ThemePacks.brand,
    ): Report {
        val fallbacks = linkedMapOf<String, String>()
        val warnings = mutableListOf<String>()

        checkBoard(mode = "light", board = spec.light, fallbacks, warnings)
        checkBoard(mode = "dark", board = spec.dark, fallbacks, warnings)

        return Report(fallbacks, warnings)
    }

    private fun checkBoard(
        mode: String,
        board: AppTokens,
        fallbacks: MutableMap<String, String>,
        warnings: MutableList<String>,
    ) {
        val c = board.colors

        // A11y 配对矩阵（§3.2/§10.1；floor 决议同 ContrastMatrixTest：textTertiary 浅 2.5 / 深 3.0）
        val tertiaryFloor = if (mode == "light") 2.5 else 3.0
        a11yPair(mode, "onPrimary", "primary", c.onPrimary, c.primary, 4.5, fallbacks)
        a11yPair(mode, "onPrimaryContainer", "primaryContainer", c.onPrimaryContainer, c.primaryContainer, 4.5, fallbacks)
        a11yPair(mode, "textPrimary", "surface", c.textPrimary, c.surface, 4.5, fallbacks)
        a11yPair(mode, "textSecondary", "surface", c.textSecondary, c.surface, 4.5, fallbacks)
        a11yPair(mode, "textTertiary", "surface", c.textTertiary, c.surface, tertiaryFloor, fallbacks)
        a11yPair(mode, "textPrimary", "surfaceElevated", c.textPrimary, c.surfaceElevated, 4.5, fallbacks)

        // body 字号下限（§10.2）——v1 不开放 type 覆盖，正常为 no-op，仍按规范兜底
        val bodySp = board.type.body.fontSize.value
        if (board.type.body.fontSize.type == TextUnitType.Sp && bodySp < BODY_FONT_MIN_SP) {
            fallbacks["$mode.type.body"] = "body 字号 ${bodySp}sp < 下限 ${BODY_FONT_MIN_SP}sp（§10.2），回退 brand 值"
        }

        // 可用性软约束：表面三色差异 + divider 边界感 → 告警保留
        softBoundary(mode, "surface/surfaceVariant", c.surfaceVariant, c.surface, SURFACE_MIN_RATIO, warnings)
        softBoundary(mode, "surface/surfaceElevated", c.surfaceElevated, c.surface, SURFACE_MIN_RATIO, warnings)
        softBoundary(mode, "divider/surface", c.divider, c.surface, DIVIDER_MIN_RATIO, warnings)
    }

    private fun a11yPair(
        mode: String,
        fgName: String,
        bgName: String,
        fg: Color,
        bg: Color,
        floor: Double,
        fallbacks: MutableMap<String, String>,
    ) {
        val ratio = Contrast.contrast(fg, bg)
        if (ratio < floor) {
            // 「该令牌回退 brand 值」：两成员都记（§10.1 配对是双方耦合），合并器按 brand 对应模式取值
            val reason = "对比度配对 $fgName/$bgName 仅 ${"%.2f".format(ratio)} < $floor（§10.1），回退 brand 值"
            fallbacks.putIfAbsent("$mode.$fgName", reason)
            fallbacks.putIfAbsent("$mode.$bgName", reason)
        }
    }

    private fun softBoundary(
        mode: String,
        label: String,
        a: Color,
        b: Color,
        floor: Double,
        warnings: MutableList<String>,
    ) {
        val ratio = Contrast.contrast(a, b)
        if (ratio < floor) {
            warnings += "$mode：$label 差异不足（对比度 ${"%.2f".format(ratio)} < $floor），视觉分层弱（§7.4 软约束，告警保留）"
        }
    }
}