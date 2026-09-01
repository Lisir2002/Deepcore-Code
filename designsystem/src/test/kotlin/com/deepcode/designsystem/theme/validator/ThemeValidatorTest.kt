package com.deepcode.designsystem.theme.validator

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.deepcode.designsystem.theme.ThemePacks
import kotlin.test.Test
import kotlin.test.assertTrue

/** ThemeValidator（§7.4 A11y 硬约束回退 + 可用性软约束告警），合并后 Spec 上跑。 */
class ThemeValidatorTest {

    @Test
    fun `brand 包 A11y 全部达标 无回退`() {
        val report = ThemeValidator.validate(ThemePacks.brand)
        assertTrue(report.fallbacks.isEmpty(), "brand 不应触发任何回退：$report")
    }

    @Test
    fun `对比度过低 配对双方回退 brand`() {
        val spec = ThemePacks.brand.copy(
            light = ThemePacks.brand.light.copy(
                colors = ThemePacks.brand.light.colors.copy(primary = Color.White, onPrimary = Color.White),
            ),
        )
        val report = ThemeValidator.validate(spec)
        assertTrue("light.primary" in report.fallbacks)
        assertTrue("light.onPrimary" in report.fallbacks)
        assertTrue(report.fallbacks["light.primary"]!!.contains("onPrimary/primary"))
    }

    @Test
    fun `body 字号低于下限 回退 brand`() {
        val spec = ThemePacks.brand.copy(
            light = ThemePacks.brand.light.copy(
                type = ThemePacks.brand.light.type.copy(
                    body = ThemePacks.brand.light.type.body.copy(fontSize = 11.sp),
                ),
            ),
        )
        val report = ThemeValidator.validate(spec)
        assertTrue("light.type.body" in report.fallbacks)
    }

    @Test
    fun `表面三色趋同 可用性软约束告警`() {
        val spec = ThemePacks.brand.copy(
            light = ThemePacks.brand.light.copy(
                colors = ThemePacks.brand.light.colors.copy(
                    surfaceElevated = ThemePacks.brand.light.colors.surface,
                ),
            ),
        )
        val report = ThemeValidator.validate(spec)
        assertTrue(report.warnings.any { "surfaceElevated" in it && "软约束" in it })
    }

    @Test
    fun `深色板 textTertiary 使用更强下限`() {
        // dark 用 ≥3.0；构造 primaryContainer 与 onPrimaryContainer 趋同，验证 pair 失败记到 dark 路径
        val spec = ThemePacks.brand.copy(
            dark = ThemePacks.brand.dark.copy(
                colors = ThemePacks.brand.dark.colors.copy(
                    primaryContainer = Color.Black, onPrimaryContainer = Color.Black,
                ),
            ),
        )
        val report = ThemeValidator.validate(spec)
        assertTrue("dark.primaryContainer" in report.fallbacks)
        assertTrue("dark.onPrimaryContainer" in report.fallbacks)
    }
}