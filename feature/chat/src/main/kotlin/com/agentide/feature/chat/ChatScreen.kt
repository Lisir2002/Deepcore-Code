package com.agentide.feature.chat

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agentide.designsystem.components.AppEmptyState
import com.agentide.designsystem.components.AppInputBar
import com.agentide.designsystem.components.AppScaffold
import com.agentide.designsystem.components.AppTextButton
import com.agentide.designsystem.render.TranscriptList
import org.koin.androidx.compose.koinViewModel

/**
 * 会话页。
 *
 * 看这个页面的代码量——它没有自己的布局、没有自己的卡片、没有自己的状态处理。
 * 它只是把 ViewModel 的状态接到 designsystem 的组件上。
 * **这就是 UI 不会走样的原因：页面里根本没有"设计"的机会。**
 */
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = koinViewModel(),
    onBack: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
) {
    val blocks by viewModel.blocks.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // 有新内容就贴到底部。只在块数量变化时触发，避免流式更新时疯狂滚动。
    LaunchedEffect(blocks.size) {
        if (blocks.isNotEmpty()) {
            runCatching { listState.animateScrollToItem(blocks.lastIndex) }
        }
    }

    AppScaffold(
        title = "会话",
        subtitle = when {
            running -> "运行中…"
            blocks.isEmpty() -> "空闲"
            else -> "${blocks.size} 个块"
        },
        onBack = onBack,
        actions = {
            if (running) {
                AppTextButton(text = "停止", onClick = viewModel::stop)
            } else {
                AppTextButton(text = "设置", onClick = { onOpenSettings?.invoke() })
            }
        },
        bottomBar = {
            AppInputBar(
                value = draft,
                onValueChange = viewModel::onDraftChange,
                onSend = viewModel::send,
                onStop = if (running) viewModel::stop else null,
                hint = if (running) "正在执行，点右侧方块可中断" else null,
            )
        },
    ) { padding ->
        if (blocks.isEmpty()) {
            AppEmptyState(
                title = "还没有对话",
                message = "描述一个任务，Agent 会拆解成工具调用逐步执行。",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        } else {
            TranscriptList(
                blocks = blocks,
                listState = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onApprove = viewModel::approve,
                onDeny = viewModel::deny,
            )
        }
    }
}
