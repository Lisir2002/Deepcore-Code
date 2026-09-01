package com.deepcode.designsystem.theme.validator

import androidx.compose.ui.graphics.Color
import com.deepcode.designsystem.theme.ThemePacks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 12.3 loader 表驱动（§12 测试矩阵）：delta 合并全路径 + 导入报告内容。
 *
 * 覆盖：正常 delta / 未知键告警 / 类型冲突拒载 / 显式 null 拒载 / 版本过高拒载 /
 * 越界回退（A11y 硬约束）+ 报告字段（applied / warnings / fallbacks）。
 */
class ThemeLoaderTableTest {

    private fun load(json: String): ThemePackLoader.LoadResult = ThemePackLoader.load(json)

    // —— 拒载类 ——
    @Test
    fun `类型冲突 radius 传字符串 拒载`() {
        val r = load("""{"id":"x","name":"x","light":{"radius":{"card":"big"}}}""")
        assertIs<ThemePackLoader.LoadResult.Rejected>(r)
    }

    @Test
    fun `显式 null 拒载（回退语义为删键）`() {
        val r = load("""{"id":"x","name":"x","light":{"color":{"primary":null}}}""")
        assertIs<ThemePackLoader.LoadResult.Rejected>(r)
    }

    @Test
    fun `版本过高拒载不降级`() {
        val r = load("""{"id":"x","name":"x","schemaVersion":2}""")
        val rej = assertIs<ThemePackLoader.LoadResult.Rejected>(r)
        assertTrue(rej.errors.any { "schemaVersion 2" in it })
    }

    // —— 正常 / 告警 / 回退 ——
    @Test
    fun `正常 delta 合并 明暗独立继承`() {
        val r = load("""
            {
              "id": "midnight", "name": "午夜", "schemaVersion": 1,
              "light": { "color": { "toolRunning": "#7C5CFF" }, "radius": { "card": 20 } },
              "dark": { "color": { "toolRunning": "#9B85FF" } }
            }
        """.trimIndent())
        val ok = assertIs<ThemePackLoader.LoadResult.Success>(r)

        assertEquals("midnight", ok.spec.id)
        assertEquals("午夜", ok.spec.name)
        assertEquals(1, ok.spec.schemaVersion)

        // 覆盖生效（toolRunning 不参与 A11y 配对，稳妥验证纯合并路径）
        assertEquals(Color(0xFF7C5CFF), ok.spec.light.colors.toolRunning)
        assertEquals(Color(0xFF9B85FF), ok.spec.dark.colors.toolRunning)
        assertEquals(20.dpValue(), ok.spec.light.radius.card)

        // dark 缺 surface → 继承 brand.dark.surface；light 亦独立继承（§7.3.1 互不渗透）
        assertEquals(ThemePacks.brand.dark.colors.surface, ok.spec.dark.colors.surface)
        assertEquals(ThemePacks.brand.light.colors.surface, ok.spec.light.colors.surface)

        // 报告：applied 记录覆盖键；无前向兼容告警；无 A11y 回退
        assertTrue(ok.applied.contains("light.color.toolRunning"))
        assertTrue(ok.applied.contains("light.radius.card"))
        assertTrue(ok.applied.contains("dark.color.toolRunning"))
        assertTrue(ok.warnings.isEmpty())
        assertTrue(ok.report.fallbacks.isEmpty())
    }

    @Test
    fun `未知令牌 告警忽略 前向兼容`() {
        val r = load("""
            { "id": "x", "name": "x", "light": { "color": { "futureToken": "#000000", "toolRunning": "#7C5CFF" } } }
        """.trimIndent())
        val ok = assertIs<ThemePackLoader.LoadResult.Success>(r)
        assertTrue(ok.warnings.any { "futureToken" in it })
        // 已知键仍合并（toolRunning 不触发 A11y 回退）
        assertEquals(Color(0xFF7C5CFF), ok.spec.light.colors.toolRunning)
    }

    @Test
    fun `A11y 对比度过低 回退 brand（导入报告含 fallback）`() {
        val r = load("""
            { "id": "x", "name": "x", "light": { "color": { "primary": "#FFFFFF", "onPrimary": "#FFFFFF" } } }
        """.trimIndent())
        val ok = assertIs<ThemePackLoader.LoadResult.Success>(r)

        // 配对 onPrimary/primary 白对白 ≈1 <4.5 → 两成员回退 brand
        assertTrue("light.primary" in ok.report.fallbacks)
        assertTrue("light.onPrimary" in ok.report.fallbacks)

        assertEquals(ThemePacks.brand.light.colors.primary, ok.spec.light.colors.primary)
        assertEquals(ThemePacks.brand.light.colors.onPrimary, ok.spec.light.colors.onPrimary)
    }

    @Test
    fun `radius 越界 拒载并给键路径`() {
        val r = load("""{"id":"x","name":"x","light":{"radius":{"card":999}}}""")
        val rej = assertIs<ThemePackLoader.LoadResult.Rejected>(r)
        assertTrue(rej.errors.any { "light.radius.card" in it })
    }

    private fun Int.dpValue(): androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(this.toFloat())
}