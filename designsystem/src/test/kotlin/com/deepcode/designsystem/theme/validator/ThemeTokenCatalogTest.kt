package com.deepcode.designsystem.theme.validator

import com.deepcode.designsystem.theme.AppColors
import com.deepcode.designsystem.theme.AppRadius
import kotlin.reflect.full.memberProperties
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * ThemeTokenCatalog 与真实令牌类型的反射对齐（防目录与类漂移）：
 * theme.json 认得的键必须与 AppColors / AppRadius 属性名一一对应，缺一即红。
 */
class ThemeTokenCatalogTest {

    @Test
    fun `color 目录与 AppColors 属性一一对应`() {
        val expected = AppColors::class.memberProperties.map { it.name }.toSet()
        assertEquals(expected, ThemeTokenCatalog.colorKeys)
    }

    @Test
    fun `radius 目录与 AppRadius 属性一一对应`() {
        val expected = AppRadius::class.memberProperties.map { it.name }.toSet()
        assertEquals(expected, ThemeTokenCatalog.radiusKeys)
    }
}