package com.deepcode.designsystem.components.scaffold

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.deepcode.designsystem.components.AppScaffold

/**
 * §6.5.1 存量兼容：现状 [AppScaffold] 直接变体化之前，作为中间层保持存量页面
 * （Chat/Settings）签名不变、零回归；骨架族完整落地后由业务页面逐步替换为变体。
 */
@Deprecated(
    message = "骨架族已上线：请改用 ChatScaffold/TabbedScaffold/NavScaffold/DetailScaffold/FormScaffold",
    replaceWith = ReplaceWith("DetailScaffold(title = title, largeTitle = title, onBack = onBack, actions = actions)"),
)
@Composable
fun AppScaffoldCompat(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    content: @Composable (PaddingValues) -> Unit,
) {
    AppScaffold(
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        onBack = onBack,
        actions = actions,
        snackbarHostState = snackbarHostState,
        content = content,
    )
}