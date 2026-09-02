package com.deepcode.designsystem.components.scaffold

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deepcode.designsystem.components.AppBottomBarDivider
import com.deepcode.designsystem.components.AppTopAppBar
import com.deepcode.designsystem.components.overlay.ToastHost
import com.deepcode.designsystem.components.overlay.ToastHostState
import com.deepcode.designsystem.theme.Dimens

/** 骨架族（§6.5.1）公共基底：吃 Ime/导航 insets + 衬 background + 内容 PaddingValues。 */
@Composable
private fun ContentScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        contentWindowInsets = WindowInsets.ime,
        content = content,
    )
}

/**
 * 对话流骨架（§6.5.1）：紧凑顶栏 + 输入槽 + 消息内容区（中线居中）。
 * inputBar 自带 Ime 避让（骨架内容区已 windowInsetsPadding(ime)）。
 */
@Composable
fun ChatScaffold(
    topBar: @Composable () -> Unit,
    inputBar: @Composable () -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    progressSticky: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    ContentScaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                ) {
                    progressSticky()
                    AppBottomBarDivider()
                    inputBar()
                }
            }
        },
        content = content,
    )
}

/** 选项卡骨架：顶栏下方 AppTopTabs + 内容区跨 index crossfade。 */
@Composable
fun TabbedScaffold(
    tabs: List<TabItem>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    topBarExtra: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable (index: Int, PaddingValues) -> Unit,
) {
    ContentScaffold(
        modifier = modifier,
        topBar = {
            androidx.compose.foundation.layout.Column {
                topBarExtra()
                AppTopTabs(tabs = tabs, selectedIndex = selectedIndex, onSelected = onTabSelected)
            }
        },
        content = { padding -> content(selectedIndex, padding) },
    )
}

/** 底栏导航骨架：内容区跨 index crossfade（StateSwap）；`toastHostState` 非空时内置 AppToast 位（底栏上方 inset，业务零感知）。 */
@Composable
fun NavScaffold(
    tabs: List<NavItem>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    toastHostState: ToastHostState? = null,
    content: @Composable (index: Int, PaddingValues) -> Unit,
) {
    val bottomBar: @Composable () -> Unit = {
        Surface(color = MaterialTheme.colorScheme.surface) {
            AppNavBar(tabs = tabs, selectedIndex = selectedIndex, onSelected = onSelected)
        }
    }
    if (toastHostState != null) {
        Box(modifier = modifier) {
            ContentScaffold(
                bottomBar = bottomBar,
                content = { padding -> content(selectedIndex, padding) },
            )
            // Toast 内置位：底栏上方 inset，业务零感知（§6.6.3）
            ToastHost(
                state = toastHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = Dimens.spaceL, end = Dimens.spaceL, bottom = Dimens.minTouchTarget + 8.dp),
            )
        }
    } else {
        ContentScaffold(
            modifier = modifier,
            bottomBar = bottomBar,
            content = { padding -> content(selectedIndex, padding) },
        )
    }
}

/** 详情骨架：顶栏标题 + 可选操作条（§6.5.1）。顶栏为唯一标题，内容区不再重复放大标题。 */
@Composable
fun DetailScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomActions: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    ContentScaffold(
        modifier = modifier,
        topBar = {
            AppTopAppBar(title = title, onBack = onBack, actions = actions)
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                    AppBottomBarDivider()
                    bottomActions()
                }
            }
        },
        content = content,
    )
}

/** 表单骨架：确认条 + group 间距，Expanded 双列短字段由内容自行排。 */
@Composable
fun FormScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    confirm: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable (ColumnScope.() -> Unit),
) {
    ContentScaffold(
        modifier = modifier,
        topBar = {
            AppTopAppBar(title = title, onBack = onBack)
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                    AppBottomBarDivider()
                    confirm()
                }
            }
        },
        content = { padding ->
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = Dimens.spaceL, vertical = Dimens.spaceM),
                content = content,
            )
        },
    )
}