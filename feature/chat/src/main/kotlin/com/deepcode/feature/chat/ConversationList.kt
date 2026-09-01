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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

/**
 * 对话列表（tab 首屏）：骨架槽位填充，不新增样式。
 *
 * 顶栏 = 骨架 `AppTopAppBar` 槽位（左侧标题 + 右侧「新建对话」图标按钮）；
 * 列表项 = `AppCard` 包 `AppSwipeReveal`（左滑半卡露出 重命名/删除/查看 操作区）；
 * 整个页面由 `AppScaffold` 承载，顶栏/底栏 insets 骨架层统一消化。
 */
@Composable
fun ConversationList(
    onOpenConversation: (String) -> Unit,
    onNewConversation: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    var conversations by remember { mutableStateOf(sampleConversations) }
    var renameTarget by remember { mutableStateOf<ConversationSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<ConversationSummary?>(null) }

    AppScaffold(
        title = "对话",
        modifier = modifier.padding(contentPadding),
        actions = {
            IconButton(onClick = onNewConversation) {
                Icon(Icons.Filled.Add, contentDescription = "新建对话")
            }
        },
    ) { padding ->
        if (conversations.isEmpty()) {
            // 空态由骨架 AppEmptyState 承载，避免手拼布局。
            AppText(
                "还没有对话",
                style = AppTextStyle.Title,
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceS),
                contentPadding = PaddingValues(vertical = Dimens.spaceM, horizontal = Dimens.spaceL),
            ) {
                items(conversations, key = { it.id }) { conv ->
                    ConversationRow(
                        conv = conv,
                        onRename = { renameTarget = conv },
                        onDelete = { deleteTarget = conv },
                        onOpen = { onOpenConversation(conv.id) },
                    )
                }
            }
        }
    }

    // 重命名：模态面板承载输入 + 保存/取消。
    renameTarget?.let { conv ->
        var name by remember(conv) { mutableStateOf(conv.title) }
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
                        val newTitle = name.trim().ifBlank { conv.title }
                        conversations = conversations.map {
                            if (it.id == conv.id) it.copy(title = newTitle) else it
                        }
                        renameTarget = null
                    },
                )
            }
        }
    }

    // 删除：破坏性操作先确认。
    deleteTarget?.let { conv ->
        AppConfirmDialog(
            title = "删除对话",
            body = "确定删除「${conv.title}」吗？此操作不可恢复。",
            confirmText = "删除",
            danger = true,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                conversations = conversations.filterNot { it.id == conv.id }
                deleteTarget = null
            },
        )
    }
}

@Composable
private fun ConversationRow(
    conv: ConversationSummary,
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
                AppText(
                    conv.title,
                    style = AppTextStyle.Title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.padding(top = Dimens.spaceXXS))
                AppText(
                    conv.preview,
                    style = AppTextStyle.Caption,
                    tone = AppTextTone.Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 演示数据占位：接真实会话存储后替换。 */
private data class ConversationSummary(val id: String, val title: String, val preview: String)

private val sampleConversations = listOf(
    ConversationSummary("demo-1", "第一个对话", "你好，我是 DeepCode Agent。"),
    ConversationSummary("demo-2", "修复登录闪退", "正在分析崩溃日志…"),
)
