package com.deepcode.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deepcode.designsystem.state.UiState
import com.deepcode.designsystem.theme.Dimens

/**
 * 全 App 唯一的页面骨架。
 *
 * 页面禁止自己写 Scaffold——一旦允许，就会出现有的页面有返回箭头有的没有、
 * 有的标题两行有的三行、snackbar 挂在不同位置这类问题。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            AppTopAppBar(
                title = title,
                subtitle = subtitle,
                onBack = onBack,
                actions = actions,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = bottomBar,
        floatingActionButton = floatingActionButton,
        content = content,
    )
}

/**
 * 页面骨架 + 统一状态处理。
 *
 * 这是绝大多数页面的写法：给一个 UiState，加载/空/错误/内容四种形态自动切换。
 * 页面作者只需要写"内容长什么样"。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> AppScaffoldWithState(
    title: String,
    state: UiState<T>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    emptyIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    content: @Composable (T) -> Unit,
) {
    AppScaffold(
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        onBack = onBack,
        actions = actions,
        bottomBar = bottomBar,
        floatingActionButton = floatingActionButton,
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (state) {
                is UiState.Idle -> Unit
                is UiState.Loading -> AppLoadingIndicator()
                is UiState.Empty -> AppEmptyState(
                    title = state.title,
                    message = state.message,
                    icon = emptyIcon,
                    actionLabel = state.actionLabel,
                    onAction = onRetry,
                )
                is UiState.Error -> AppErrorState(
                    message = state.message,
                    detail = state.detail,
                    retryable = state.retryable,
                    onRetry = onRetry,
                )
                is UiState.Content -> content(state.value)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopAppBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "返回",
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(),
    )
}

/** 底部输入区的统一分隔线。所有带输入框的页面用它，分隔线位置才一致。 */
@Composable
fun AppBottomBarDivider() {
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.fillMaxWidth(),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
fun AppSpacer(height: androidx.compose.ui.unit.Dp = Dimens.spaceM) {
    Spacer(modifier = Modifier.padding(vertical = height / 2))
}
