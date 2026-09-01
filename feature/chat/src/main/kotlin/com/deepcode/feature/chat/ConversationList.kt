package com.deepcode.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deepcode.designsystem.components.AppCard
import com.deepcode.designsystem.components.AppScaffold
import com.deepcode.designsystem.components.AppText
import com.deepcode.designsystem.components.overlay.AppDropdownMenu
import com.deepcode.designsystem.components.overlay.AppDropdownMenuItem
import com.deepcode.designsystem.theme.AppTextStyle
import com.deepcode.designsystem.theme.AppTextTone
import com.deepcode.designsystem.theme.Dimens

/**
 * 对话列表（tab 首屏）：骨架槽位填充，不新增样式。
 *
 * 顶栏 = 骨架 `AppTopAppBar` 槽位（左侧标题 + 右侧「更多」菜单按钮）；
 * 列表项 = `AppCard`；整个页面由 `AppScaffold` 承载，顶栏/底栏 insets 骨架层统一消化。
 */
@Composable
fun ConversationList(
    onOpenConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    var moreExpanded by remember { mutableStateOf(false) }
    val conversations = remember { sampleConversations }

    AppScaffold(
        title = "对话",
        modifier = modifier.padding(contentPadding),
        actions = {
            AppDropdownMenu(
                expanded = moreExpanded,
                onDismissRequest = { moreExpanded = false },
                anchor = {
                    IconButton(onClick = { moreExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                    }
                },
            ) {
                AppDropdownMenuItem(text = "刷新", onClick = { moreExpanded = false }, leadingIcon = Icons.Filled.Refresh)
                AppDropdownMenuItem(text = "清空对话", onClick = { moreExpanded = false }, leadingIcon = Icons.Filled.Delete)
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
                    ConversationRow(conv) { onOpenConversation(conv.id) }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(
    conv: ConversationSummary,
    onClick: () -> Unit,
) {
    AppCard(onClick = onClick) {
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

/** 演示数据占位：接真实会话存储后替换。 */
private data class ConversationSummary(val id: String, val title: String, val preview: String)

private val sampleConversations = listOf(
    ConversationSummary("demo-1", "第一个对话", "你好，我是 DeepCode Agent。"),
    ConversationSummary("demo-2", "修复登录闪退", "正在分析崩溃日志…"),
)