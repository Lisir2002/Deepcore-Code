package com.deepcode.feature.chat

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepcode.designsystem.components.AppEmptyState
import com.deepcode.designsystem.components.AppInputBar
import com.deepcode.designsystem.components.AppTextButton
import com.deepcode.designsystem.components.AppTopAppBar
import com.deepcode.designsystem.components.overlay.AppDropdownMenu
import com.deepcode.designsystem.components.overlay.AppDropdownMenuDivider
import com.deepcode.designsystem.components.overlay.AppDropdownMenuHeader
import com.deepcode.designsystem.components.overlay.AppDropdownMenuItem
import com.deepcode.designsystem.components.scaffold.ChatScaffold
import com.deepcode.designsystem.render.TranscriptList
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 会话页。
 *
 * 它只是把 ViewModel 的状态接到 designsystem 的组件上，没有自己的布局/卡片。
 * 顶栏右上：模型下拉（切换当前会话使用的模型）+ 停止/设置。
 */
@Composable
fun ChatScreen(
    conversationId: String,
    viewModel: ChatViewModel = koinViewModel(
        // 用会话 id 作为 ViewModel key：即便 ViewModelStoreOwner 被复用（如 Activity 级），
        // 每个会话也拿到独立实例，绝不串到上一个会话的历史。
        key = conversationId,
        parameters = { parametersOf(conversationId) },
    ),
    onBack: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
) {
    val blocks by viewModel.blocks.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    var modelMenu by remember { mutableStateOf(false) }
    val models = viewModel.availableModels()
    val activeModelName = viewModel.activeLabel

    // 有新内容就贴到底部。只在块数量变化时触发，避免流式更新时疯狂滚动。
    LaunchedEffect(blocks.size) {
        if (blocks.isNotEmpty()) {
            runCatching { listState.animateScrollToItem(blocks.lastIndex) }
        }
    }

    ChatScaffold(
        onBack = onBack,
        topBar = {
            AppTopAppBar(
                title = "会话",
                subtitle = when {
                    running -> "运行中… · $activeModelName"
                    blocks.isEmpty() -> "空闲 · $activeModelName"
                    else -> "${blocks.size} 个块 · $activeModelName"
                },
                onBack = onBack,
                actions = {
                    if (running) {
                        AppTextButton(text = "停止", onClick = viewModel::stop)
                    } else {
                        AppDropdownMenu(
                            expanded = modelMenu,
                            onDismissRequest = { modelMenu = false },
                            anchor = {
                                AppTextButton(
                                    text = activeModelName,
                                    onClick = { modelMenu = true },
                                )
                            },
                        ) {
                            AppDropdownMenuHeader("选择模型 · 供应商 / 模型")
                            models.forEach { choice ->
                                AppDropdownMenuItem(
                                    text = "${choice.providerName} / ${choice.modelId}",
                                    selected = viewModel.isActive(choice),
                                    onClick = {
                                        modelMenu = false
                                        viewModel.switchModel(choice)
                                    },
                                )
                            }
                            if (models.isEmpty()) {
                                AppDropdownMenuItem(
                                    text = "暂无已保存模型，请到设置添加",
                                    enabled = false,
                                    onClick = { modelMenu = false },
                                )
                            }
                            AppDropdownMenuDivider()
                            AppDropdownMenuItem(
                                text = "演示模型",
                                selected = !viewModel.hasActiveProvider,
                                onClick = {
                                    modelMenu = false
                                    viewModel.switchToDemo()
                                },
                            )
                        }
                        AppTextButton(text = "设置", onClick = { onOpenSettings?.invoke() })
                    }
                },
            )
        },
        inputBar = {
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