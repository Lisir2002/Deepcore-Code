package com.deepcode.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepcode.core.model.SessionStatus
import com.deepcode.core.uistate.formatRelativeTime
import com.deepcode.designsystem.components.AppCard
import com.deepcode.designsystem.components.AppPrimaryButton
import com.deepcode.designsystem.components.AppScaffold
import com.deepcode.designsystem.components.AppSwipeReveal
import com.deepcode.designsystem.components.AppText
import com.deepcode.designsystem.components.AppTextButton
import com.deepcode.designsystem.components.AppTextField
import com.deepcode.designsystem.components.AppTextFieldVariant
import com.deepcode.designsystem.components.overlay.AppConfirmDialog
import com.deepcode.designsystem.components.scaffold.AppModalSheet
import com.deepcode.designsystem.theme.AppTextStyle
import com.deepcode.designsystem.theme.AppTextTone
import com.deepcode.designsystem.theme.Dimens
import com.deepcode.designsystem.theme.appColors
import org.koin.androidx.compose.koinViewModel

/**
 * 对话列表（tab 首屏）：数据来自会话索引 + 事件归约，不新增样式。
 *
 * 顶栏 = 骨架 `AppTopAppBar` 槽位（左侧标题 + 右侧「新建对话」图标按钮）；
 * 列表项 = `AppCard` 包 `AppSwipeReveal`（整卡横满，左滑半卡露出 重命名/删除/查看 操作区）；
 * 列表项信息加强：标题 + 预览 + 相对时间 + 状态角标 + 模型标识（数据源补上后显示）。
 * 整个页面由 `AppScaffold` 承载，顶栏/底栏 insets 骨架层统一消化。
 */
@Composable
fun ConversationList(
    onOpenConversation: (String) -> Unit,
    onNewConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: ConversationViewModel = koinViewModel(),
) {
    val conversations by viewModel.items.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val created by viewModel.created.collectAsStateWithLifecycle()
    var renameTarget by remember { mutableStateOf<ConversationItem?>(null) }
    var deleteTarget by remember { mutableStateOf<ConversationItem?>(null) }

    // 新建成功 → 清空标记并跳转进新会话
    LaunchedEffect(created) {
        val id = created ?: return@LaunchedEffect
        viewModel.consumeCreated()
        onNewConversation(id.value)
    }

    AppScaffold(
        title = "对话",
        modifier = modifier.padding(contentPadding),
        actions = {
            IconButton(onClick = viewModel::create) {
                Icon(Icons.Filled.Add, contentDescription = "新建对话")
            }
        },
    ) { padding ->
        when {
            loading -> AppText(
                "加载中…",
                style = AppTextStyle.Body,
                tone = AppTextTone.Muted,
                modifier = Modifier.padding(padding),
            )

            conversations.isEmpty() -> AppText(
                "还没有对话",
                style = AppTextStyle.Title,
                modifier = Modifier.padding(padding),
            )

            else -> LazyColumn(
                modifier = Modifier.padding(padding),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceS),
                contentPadding = PaddingValues(vertical = Dimens.spaceM, horizontal = Dimens.spaceL),
            ) {
                items(conversations, key = { it.id.value }) { item ->
                    ConversationRow(
                        item = item,
                        onRename = { renameTarget = item },
                        onDelete = { deleteTarget = item },
                        onOpen = { onOpenConversation(item.id.value) },
                    )
                }
            }
        }
    }

    // 重命名：模态面板承载输入 + 保存/取消。
    renameTarget?.let { item ->
        var name by remember(item) { mutableStateOf(item.title) }
        AppModalSheet(onDismiss = { renameTarget = null }) {
            AppText("重命名对话", style = AppTextStyle.Title)
            Spacer(Modifier.size(Dimens.spaceM))
            AppTextField(
                label = "对话名称",
                value = name,
                onValueChange = { name = it },
                variant = AppTextFieldVariant.Filled,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.spaceL),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppTextButton(text = "取消", onClick = { renameTarget = null })
                Spacer(Modifier.size(Dimens.spaceS))
                AppPrimaryButton(
                    text = "保存",
                    onClick = {
                        viewModel.rename(item.id, name)
                        renameTarget = null
                    },
                )
            }
        }
    }

    // 删除：破坏性操作先确认。
    deleteTarget?.let { item ->
        AppConfirmDialog(
            title = "删除对话",
            body = "确定删除「${item.title}」吗？此操作不可恢复。",
            confirmText = "删除",
            danger = true,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                viewModel.delete(item.id)
                deleteTarget = null
            },
        )
    }
}

@Composable
private fun ConversationRow(
    item: ConversationItem,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
) {
    AppSwipeReveal(
        actions = {
            IconButton(onClick = onRename) {
                Icon(Icons.Filled.Edit, contentDescription = "重命名")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "删除", tint = appColors().danger)
            }
            IconButton(onClick = onOpen) {
                Icon(Icons.Filled.Visibility, contentDescription = "查看")
            }
        },
    ) {
        AppCard(onClick = onOpen) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppText(
                        item.title,
                        style = AppTextStyle.Title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    StatusBadge(item.status)
                    Spacer(Modifier.size(Dimens.spaceS))
                    AppText(
                        formatRelativeTime(item.updatedAt),
                        style = AppTextStyle.Caption,
                        tone = AppTextTone.Muted,
                    )
                }
                if (item.preview.isNotBlank()) {
                    Spacer(Modifier.padding(top = Dimens.spaceXXS))
                    AppText(
                        item.preview,
                        style = AppTextStyle.Caption,
                        tone = AppTextTone.Muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                item.modelRef?.let { modelRef ->
                    Spacer(Modifier.padding(top = Dimens.spaceXXS))
                    AppText(
                        modelRef.toString(),
                        style = AppTextStyle.Label,
                        tone = AppTextTone.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 状态角标：只有「值得看」的状态才显示，空闲不打扰。 */
@Composable
private fun StatusBadge(status: SessionStatus) {
    when (status) {
        SessionStatus.RUNNING -> AppText("运行中", style = AppTextStyle.Label, tone = AppTextTone.Primary)
        SessionStatus.AWAITING_APPROVAL -> AppText("待授权", style = AppTextStyle.Label, tone = AppTextTone.Primary)
        SessionStatus.FAILED -> AppText("失败", style = AppTextStyle.Label, tone = AppTextTone.Error)
        SessionStatus.IDLE, SessionStatus.COMPLETED, SessionStatus.CANCELLED -> Unit
    }
}
