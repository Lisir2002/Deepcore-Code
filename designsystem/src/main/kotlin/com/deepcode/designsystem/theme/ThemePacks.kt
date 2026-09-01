package com.deepcode.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * 编译期内置风格包注册表（§7.1）：brand 常驻 + console 演示包。
 * 页面级局部换肤例（§8.2）：`AppTheme(ThemePacks.console) { ... }`，组件层零改动。
 */
@Immutable
object ThemePacks {

    /** 品牌默认包：明暗双板 = 语义 source of truth。 */
    val brand = AppThemeSpec(
        id = "brand",
        name = "品牌默认",
        light = LightAppTokens,
        dark = DarkAppTokens,
        source = AppThemeSpec.Source.BUILT_IN,
    )

    /**
     * console 演示包：换一套品牌 accent（青 teal），灰阶复用 brand 底座。
     * 仅用于演示「风格包切换路径已通」，非正式视觉定稿。
     */
    val console = AppThemeSpec(
        id = "console",
        name = "Console 演示",
        light = LightAppTokens.copy(colors = LightAppTokens.colors.copy(
            primary = Color(0xFF0F766E),           // teal-700
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFCCFBF1),
            onPrimaryContainer = Color(0xFF134E4A),
            info = Color(0xFF0F766E),
            toolRunning = Color(0xFF0F766E),
        )),
        dark = DarkAppTokens.copy(colors = DarkAppTokens.colors.copy(
            primary = Color(0xFF5EEAD4),           // teal-300
            onPrimary = Color(0xFF004F4A),
            primaryContainer = Color(0xFF115E59),
            onPrimaryContainer = Color(0xFF99F6E4),
            info = Color(0xFF5EEAD4),
            toolRunning = Color(0xFF5EEAD4),
        )),
        source = AppThemeSpec.Source.BUILT_IN,
    )

    /** 全部内置包（:app 装配时注册进 StyleController）。 */
    val builtIn: List<AppThemeSpec> = listOf(brand, console)
}