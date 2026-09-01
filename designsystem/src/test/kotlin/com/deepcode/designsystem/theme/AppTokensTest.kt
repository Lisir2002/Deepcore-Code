package com.deepcode.designsystem.theme

import androidx.compose.ui.graphics.Color
import kotlin.reflect.full.memberProperties
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 12.1 语义面板完整性：不做反射漏参、不漏配明暗板。
 *
 * 语义令牌 = 具名属性（§3.1）。本测试反射枚举 `AppColors` 全属性，断言明暗两板
 * 每个色令牌都配了「不透明、非占位」的实值——漏配一处即红，杜绝"某个语义色忘了给"。
 */
class AppTokensTest {

    @Test
    fun allSemanticColors_lightAreRealValues() {
        assertBoard(LightAppTokens.colors)
    }

    @Test
    fun allSemanticColors_darkAreRealValues() {
        assertBoard(DarkAppTokens.colors)
    }

    private fun assertBoard(colors: AppColors) {
        val props = AppColors::class.memberProperties.map { it.getter.call(colors) as Color }
        assertTrue(props.isNotEmpty(), "AppColors 不应为空")
        for (name in appColorPropertyNames) {
            val c = AppColors::class.memberProperties
                .first { it.name == name }.getter.call(colors) as Color
            assertTrue(c != Color.Unspecified, "$name 未配置（Unspecified）")
            assertTrue(c != Color.Transparent, "$name 不应为 Transparent")
            assertTrue(c.alpha == 1f, "$name 应不透明，实际 alpha=${c.alpha}")
        }
    }

    @Test
    fun typeRoles_allFilled() {
        val t = LightAppTokens.type
        assertTrue(t.fontSans != androidx.compose.ui.text.font.FontFamily.Default)
        assertTrue(t.title.fontSize.value > 0f)
        assertTrue(t.sectionHeader.fontSize.value > 0f)
        assertTrue(t.body.fontSize.value > 0f)
        assertTrue(t.label.fontSize.value > 0f)
        assertTrue(t.caption.fontSize.value > 0f)
        assertTrue(t.code.fontSize.value > 0f)
    }

    @Test
    fun motion_durationsOrdered() {
        val m = LightAppTokens.motion
        assertTrue(m.fast < m.normal, "fast 应短于 normal")
        assertTrue(m.normal < m.slow, "normal 应短于 slow")
    }

    // 反射用显式名称清单，便于定位漏配的令牌。
    private val appColorPropertyNames: List<String> = listOf(
        "primary", "onPrimary", "primaryContainer", "onPrimaryContainer",
        "surface", "surfaceVariant", "surfaceElevated",
        "textPrimary", "textSecondary", "textTertiary", "textInverse",
        "divider", "border",
        "success", "warning", "danger", "info",
        "diffAdd", "diffRemove",
        "toolRunning", "toolSuccess", "toolFailed", "toolAwaiting",
        "thinking", "codeSurface", "codeBorder",
    )
}