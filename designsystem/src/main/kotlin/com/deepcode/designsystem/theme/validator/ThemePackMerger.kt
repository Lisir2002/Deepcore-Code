package com.deepcode.designsystem.theme.validator

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.deepcode.designsystem.theme.AppColors
import com.deepcode.designsystem.theme.AppRadius
import com.deepcode.designsystem.theme.AppThemeSpec
import com.deepcode.designsystem.theme.AppTokens
import com.deepcode.designsystem.theme.ThemePacks

/**
 * theme.json v1 delta 合并器（§7.3）：把编解码后的 `ThemePackJson`（已过拒载门槛）
 * 覆盖到 brand 底座，产出完整 `AppThemeSpec`。纯 Kotlin、可 JVM 单测。
 *
 * 精确规则（§7.3）：
 *   1. light / dark **各自独立** 与 brand 对应模式合并，互不渗透（dark 缺 surface
 *      自然继承 brand.dark.surface）；
 *   2. 组名+令牌名匹配 → 覆盖；未知令牌/组 → 跳过并回报（前向兼容，codec 侧已告警，
 *      这里收集省合并重复）；
 *   3. 类型冲突 / 非法 hex 已在 `ThemeJsonCodec` 拒载，此处不再重复报错；
 *   4. 显式 null 同样由 codec 拒载（「回退」语义 = 删键，不写 null）；
 *   5. A11y 硬约束（§7.4 第 3 行）在合并后由 `ThemeValidator` 判定，`ThemePackLoader`
 *      据其报告把违规令牌回退 brand 值。
 *
 * v1 只开放 color / radius 两组覆盖；type / motion 不可覆盖（§3.3/§3.5）。
 */
object ThemePackMerger {

    /** 合并产物。 */
    data class MergeResult(
        val spec: AppThemeSpec,
        /** 已应用的覆盖键路径（导入报告）。 */
        val applied: List<String>,
        /** 未知令牌（§7.3.2 告警忽略，前向兼容）。 */
        val skippedUnknown: List<String>,
    )

    fun merge(
        pack: ThemePackJson,
        brand: AppThemeSpec = ThemePacks.brand,
        source: AppThemeSpec.Source = AppThemeSpec.Source.USER_IMPORTED,
    ): MergeResult {
        val lightApplied = mutableListOf<String>()
        val lightUnknown = mutableListOf<String>()
        val light = mergeBoard(pack.light, brand.light, "light", lightApplied, lightUnknown)

        val darkApplied = mutableListOf<String>()
        val darkUnknown = mutableListOf<String>()
        val dark = mergeBoard(pack.dark, brand.dark, "dark", darkApplied, darkUnknown)

        val spec = AppThemeSpec(
            id = pack.id,
            name = pack.name,
            schemaVersion = pack.schemaVersion,
            light = light,
            dark = dark,
            source = source,
        )
        return MergeResult(
            spec = spec,
            applied = lightApplied + darkApplied,
            skippedUnknown = lightUnknown + darkUnknown,
        )
    }

    private fun mergeBoard(
        board: BoardJson?,
        base: AppTokens,
        mode: String,
        applied: MutableList<String>,
        unknown: MutableList<String>,
    ): AppTokens {
        if (board == null) return base

        val colorOver = linkedMapOf<String, Color>()
        board.color.forEach { (key, raw) ->
            if (key in ThemeTokenCatalog.colorKeys) {
                colorOver[key] = parseHex(raw)
                applied += "$mode.color.$key"
            } else {
                unknown += "$mode.color.$key"
            }
        }

        val radiusOver = linkedMapOf<String, Dp>()
        board.radius.forEach { (key, v) ->
            if (key in ThemeTokenCatalog.radiusKeys) {
                radiusOver[key] = v.dp
                applied += "$mode.radius.$key"
            } else {
                unknown += "$mode.radius.$key"
            }
        }

        return base.copy(
            colors = applyColors(base.colors, colorOver),
            radius = applyRadius(base.radius, radiusOver),
        )
    }

    /** 按令牌名逐个覆盖。键集合与 AppColors 属性一一对应（ThemeTokenCatalogTest 反射对齐防漂移）。 */
    private fun applyColors(base: AppColors, overrides: Map<String, Color>): AppColors {
        var c = base
        overrides.forEach { (key, value) ->
            c = when (key) {
                "primary" -> c.copy(primary = value)
                "onPrimary" -> c.copy(onPrimary = value)
                "primaryContainer" -> c.copy(primaryContainer = value)
                "onPrimaryContainer" -> c.copy(onPrimaryContainer = value)
                "surface" -> c.copy(surface = value)
                "surfaceVariant" -> c.copy(surfaceVariant = value)
                "surfaceElevated" -> c.copy(surfaceElevated = value)
                "textPrimary" -> c.copy(textPrimary = value)
                "textSecondary" -> c.copy(textSecondary = value)
                "textTertiary" -> c.copy(textTertiary = value)
                "textInverse" -> c.copy(textInverse = value)
                "divider" -> c.copy(divider = value)
                "border" -> c.copy(border = value)
                "success" -> c.copy(success = value)
                "successContainer" -> c.copy(successContainer = value)
                "warning" -> c.copy(warning = value)
                "warningContainer" -> c.copy(warningContainer = value)
                "danger" -> c.copy(danger = value)
                "dangerContainer" -> c.copy(dangerContainer = value)
                "info" -> c.copy(info = value)
                "diffAdd" -> c.copy(diffAdd = value)
                "diffRemove" -> c.copy(diffRemove = value)
                "toolRunning" -> c.copy(toolRunning = value)
                "toolSuccess" -> c.copy(toolSuccess = value)
                "toolFailed" -> c.copy(toolFailed = value)
                "toolAwaiting" -> c.copy(toolAwaiting = value)
                "thinking" -> c.copy(thinking = value)
                "codeSurface" -> c.copy(codeSurface = value)
                "codeBorder" -> c.copy(codeBorder = value)
                else -> c   // 不可达：非 catalog 键已被过滤；保守保留
            }
        }
        return c
    }

    private fun applyRadius(base: AppRadius, overrides: Map<String, Dp>): AppRadius {
        var r = base
        overrides.forEach { (key, value) ->
            r = when (key) {
                "card" -> r.copy(card = value)
                "listItem" -> r.copy(listItem = value)
                "chip" -> r.copy(chip = value)
                "sheet" -> r.copy(sheet = value)
                "bubble" -> r.copy(bubble = value)
                else -> r
            }
        }
        return r
    }

    /** 仅接受 codec 已通过的 `#RGB/#RRGGBB/#RRGGBBAA`。 */
    private fun parseHex(raw: String): Color {
        val hex = raw.trim().removePrefix("#")
        val expanded = if (hex.length == 3) hex.map { "$it$it" }.joinToString("") else hex
        val argb = when (expanded.length) {
            6 -> "FF$expanded".toLong(16)
            8 -> expanded.toLong(16)
            else -> 0xFFFFFFFFL   // 不可达（codec 已验）
        }
        return Color(argb)
    }
}