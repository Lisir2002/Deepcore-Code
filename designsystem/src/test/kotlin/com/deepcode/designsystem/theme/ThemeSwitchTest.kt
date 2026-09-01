package com.deepcode.designsystem.theme

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 12.5 主题切换（§8.2）：解板纯函数 + StyleController 默认实现持久化。
 *
 *  - [resolveAppTokens] / [isDarkResolved]：darkMode 三态 × 系统深色 → 单一语义板与 dark 裁决；
 *  - [DefaultStyleController]：setDarkMode / setSpec 落 store，重读初值一致。
 */
class ThemeSwitchTest {

    @Test
    fun `外显浅色强制取浅板`() {
        val board = resolveAppTokens(ThemePacks.brand, DarkMode.LIGHT, isSystemDark = true)
        assertEquals(LightAppTokens, board)
        assertEquals(false, isDarkResolved(DarkMode.LIGHT, isSystemDark = true))
    }

    @Test
    fun `外显深色强制取深板`() {
        val board = resolveAppTokens(ThemePacks.brand, DarkMode.DARK, isSystemDark = false)
        assertEquals(DarkAppTokens, board)
        assertEquals(true, isDarkResolved(DarkMode.DARK, isSystemDark = false))
    }

    @Test
    fun `跟随系统由系统深色裁决`() {
        val light = resolveAppTokens(ThemePacks.brand, DarkMode.FOLLOW_SYSTEM, isSystemDark = false)
        val dark = resolveAppTokens(ThemePacks.brand, DarkMode.FOLLOW_SYSTEM, isSystemDark = true)
        assertEquals(LightAppTokens, light)
        assertEquals(DarkAppTokens, dark)
        assertEquals(false, isDarkResolved(DarkMode.FOLLOW_SYSTEM, false))
        assertEquals(true, isDarkResolved(DarkMode.FOLLOW_SYSTEM, true))
    }

    @Test
    fun `切换风格包与深色模式并持久化`() = runTest {
        val store = RecordingStore()
        val c = DefaultStyleController(initialSpec = ThemePacks.brand, store = store)

        c.setSpec(ThemePacks.console.id)
        c.setDarkMode(DarkMode.DARK)

        assertEquals(ThemePacks.console.id, store.record[DefaultStyleController.KEY_SPEC])
        assertEquals("DARK", store.record[DefaultStyleController.KEY_DARK_MODE])
        assertEquals(ThemePacks.console, c.spec.value)
        assertEquals(DarkMode.DARK, c.darkMode.value)
    }

    @Test
    fun `内置包已登记在packs`() {
        val c = DefaultStyleController(initialSpec = ThemePacks.brand).apply {
            ThemePacks.builtIn.forEach(::registerPack)
        }
        assertEquals(ThemePacks.builtIn, c.packs.value)
    }

    /** 内存态持久化替身：无表，直接记 KV。 */
    private class RecordingStore : StylePreferenceStore {
        val record = mutableMapOf<String, String>()
        override suspend fun read(key: String): String? = record[key]
        override suspend fun write(key: String, value: String) {
            record[key] = value
        }
    }
}