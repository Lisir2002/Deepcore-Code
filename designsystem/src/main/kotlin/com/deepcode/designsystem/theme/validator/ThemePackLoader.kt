package com.deepcode.designsystem.theme.validator

import com.deepcode.designsystem.theme.AppColors
import com.deepcode.designsystem.theme.AppThemeSpec
import com.deepcode.designsystem.theme.AppTokens
import com.deepcode.designsystem.theme.ThemePacks

/**
 * theme.json v1 主题包加载管线（§14 T8.3）：`ThemeJsonCodec` → `ThemePackMerger` →
 * `ThemeValidator`，并把 A11y 违规令牌在合并结果上回退 brand 值（§7.3 第 5 步 / §7.4 第 3 行）。
 * 纯 Kotlin、可 JVM 单测（12.3 loader 表驱动）。
 *
 * 双根（assets / filesDir）D5 复用 `SkillLoader` 模式：designsystem 只做纯管线，
 * 具体读字节由 :app 装配（补 `ThemeJsonSource`）后喂给 [load]。
 */
object ThemePackLoader {

    sealed interface LoadResult {
        /** 通过并产出合并 spec；报告含软告警 + 已应用覆盖 + 前向兼容告警。 */
        data class Success(
            val spec: AppThemeSpec,
            val report: ThemeValidator.Report,
            val applied: List<String>,
            val warnings: List<String>,
        ) : LoadResult

        /** 整包拒载（结构/值，§7.4 上前两行）。 */
        data class Rejected(val errors: List<String>) : LoadResult
    }

    fun load(
        json: String,
        brand: AppThemeSpec = ThemePacks.brand,
    ): LoadResult {
        return when (val codec = ThemeJsonCodec.decode(json)) {
            is ThemeJsonCodec.CodecResult.Reject -> LoadResult.Rejected(codec.errors)

            is ThemeJsonCodec.CodecResult.Success -> {
                val merged = ThemePackMerger.merge(codec.pack, brand)
                val report = ThemeValidator.validate(merged.spec, brand)
                val finalSpec = if (report.fallbacks.isEmpty()) merged.spec
                else report.fallbacks.keys.fold(merged.spec) { s, path -> revertToken(s, path, brand) }
                LoadResult.Success(
                    spec = finalSpec,
                    report = report,
                    applied = merged.applied,
                    warnings = codec.warnings + merged.skippedUnknown,
                )
            }
        }
    }

    /**
     * 把单个 A11y 硬约束违规令牌回退 brand 对应模式值。
     * 报告路径形如 `light.onPrimary` / `dark.type.body`。
     */
    private fun revertToken(spec: AppThemeSpec, path: String, brand: AppThemeSpec): AppThemeSpec {
        val mode = when {
            path.startsWith("light.") -> "light"
            path.startsWith("dark.") -> "dark"
            else -> return spec
        }
        val token = path.removePrefix("$mode.")
        val isLight = mode == "light"
        val board: AppTokens = if (isLight) spec.light else spec.dark
        val brandBoard: AppTokens = if (isLight) brand.light else brand.dark

        val newBoard = when {
            token == "type.body" -> board.copy(type = brandBoard.type)
            token in ThemeTokenCatalog.colorKeys ->
                board.copy(colors = revertColor(board.colors, token, brandBoard.colors))
            else -> board
        }
        return if (isLight) spec.copy(light = newBoard) else spec.copy(dark = newBoard)
    }

    /** 覆盖 A11y 配对成员（§10.1 配对表）为 brand 对值；其余路径不动。 */
    private fun revertColor(colors: AppColors, token: String, brand: AppColors): AppColors = when (token) {
        "primary" -> colors.copy(primary = brand.primary)
        "onPrimary" -> colors.copy(onPrimary = brand.onPrimary)
        "primaryContainer" -> colors.copy(primaryContainer = brand.primaryContainer)
        "onPrimaryContainer" -> colors.copy(onPrimaryContainer = brand.onPrimaryContainer)
        "surface" -> colors.copy(surface = brand.surface)
        "surfaceElevated" -> colors.copy(surfaceElevated = brand.surfaceElevated)
        "textPrimary" -> colors.copy(textPrimary = brand.textPrimary)
        "textSecondary" -> colors.copy(textSecondary = brand.textSecondary)
        "textTertiary" -> colors.copy(textTertiary = brand.textTertiary)
        else -> colors
    }
}