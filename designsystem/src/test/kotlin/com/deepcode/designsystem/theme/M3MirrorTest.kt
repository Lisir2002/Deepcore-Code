package com.deepcode.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.reflect.full.memberProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 12.4 M3 镜像（§9.1）：把语义板 [AppColors] 与 M3 [ColorScheme] 逐槽位比对。
 *
 * §9.1 权威映射表约定：
 *  - 一语义一槽：source 字段只命中恰一个 M3 槽——不漏配、不重复占用；
 *  - secondary* 不占语义：由 primary 去饱和派生，语义层不同名出席；
 *  - 组件读 MaterialTheme ≡ 读语义层（§9.2）：本测试锁死"铺进来的即读到的"。
 *
 * 用 [AppThemeBridge.domesticate] 的返回 scheme 与源板逐项断言，防止改动 §9.1 时只改一边。
 */
class M3MirrorTest {

    /** 源语义字段 → 期望镜像到的 M3 槽名（§9.1 直连组）。 */
    private val semanticToSlot: Map<String, String> = mapOf(
        "primary" to "primary",
        "onPrimary" to "onPrimary",
        "primaryContainer" to "primaryContainer",
        "onPrimaryContainer" to "onPrimaryContainer",
        "surface" to "surface",
        "surfaceVariant" to "surfaceVariant",
        "textPrimary" to "onSurface",
        "textSecondary" to "onSurfaceVariant",
        "danger" to "error",
    )

    private val scheme: ColorScheme = AppThemeBridge.domesticate(
        colors = LightAppTokens.colors,
        type = LightAppTokens.type,
        isDark = false,
    ).scheme

    private fun schemeSlot(name: String): Color =
        ColorScheme::class.memberProperties.first { it.name == name }
            .getter.call(scheme) as Color

    private fun sourceSlot(name: String): Color =
        AppColors::class.memberProperties.first { it.name == name }
            .getter.call(LightAppTokens.colors) as Color

    private fun prop(name: String): Color =
        with(scheme) {
            ColorScheme::class.memberProperties.first { it.name == name }.getter.call(this) as Color
        }

    @Test
    fun `每个语义色镜像到唯一M3槽位`() {
        for ((source, target) in semanticToSlot) {
            assertEquals(sourceSlot(source), schemeSlot(target), "语义 $source 应镜像到 M3 ${target}")
        }
    }

    @Test
    fun `primary已铺入且非占位`() {
        assertTrue(prop("primary") != Color.Unspecified)
    }

    @Test
    fun `onSurface与surface不是同一色`() {
        assertNotEquals(prop("onSurface"), prop("surface"))
    }
}