package com.deepcode.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/**
 * 全 App 唯一的主题入口。
 *
 * 页面里只允许写 AppTheme { ... }，不允许自己组装 MaterialTheme——
 * 一旦各页面自己配主题，深色模式必然有页面漏掉。
 *
 * v4.2.1（P2）：
 *  - 签名升级为 `AppTheme(spec)`，读取 StyleController（§7.1）→ 按 darkMode 决出单板
 *    [resolveAppTokens] → 语义板经 [AppThemeBridge.domesticate] 填满 M3 全槽位；
 *  - 移除 dynamicColor（D8）；三态 darkMode 由 StyleController + 系统裁决；
 *  - 页面级局部换肤：`AppTheme(ThemePacks.console) { ... }`（§8.2）。
 */
@Composable
fun AppTheme(
    spec: AppThemeSpec = LocalStyleController.current.spec.collectAsState().value,
    content: @Composable () -> Unit,
) {
    val controller = LocalStyleController.current
    val darkMode by controller.darkMode.collectAsState()
    val isSystemDark = isSystemInDarkTheme()
    val isDark = isDarkResolved(darkMode, isSystemDark)
    val board = resolveAppTokens(spec, darkMode, isSystemDark)

    val domesticated = AppThemeBridge.domesticate(board.colors, board.type, isDark)

    CompositionLocalProvider(
        LocalAppTokens provides board,
        LocalAppColors provides board.colors,
    ) {
        MaterialTheme(
            colorScheme = domesticated.scheme,
            typography = domesticated.typography,
            shapes = board.radius.toShapes(),
            content = content,
        )
    }
}