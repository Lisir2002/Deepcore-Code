package com.deepcode.designsystem.components.messaging

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deepcode.core.model.ApprovalScope
import com.deepcode.core.model.ToolCall
import com.deepcode.core.uistate.RenderBlock
import com.deepcode.core.uistate.ToolVisualStatus
import com.deepcode.designsystem.theme.Dimens
import com.deepcode.designsystem.theme.appColors

/**
 * 执行组（§6.8.3）：连续的 thinking/tool_use 块聚为一组（AppBlockGroupReducer 输出，
 * text 块独立截断分组）。
 *
 * 组壳：左缘 2dp `thinking` 紫条（AI 在场合计）+ 「N 步」徽标。
 * 折叠态 = 全部子块折叠后的堆叠摘要；**任一子块 RUNNING → 组自动展开**；
 * 用户手动展开则不自动收起（尊重显式意图，§6.8.2）。
 *
 * 作用：会话流被 10+ 工具卡撑爆的问题由聚组兜底（默认一屏 = N 个折叠组）。
 */
@Composable
fun AppBlockGroup(
    blocks: List<RenderBlock>,
    registry: ToolCardRegistry,
    onApprove: (ToolCall, ApprovalScope) -> Unit = { _, _ -> },
    onDeny: (ToolCall) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = appColors()
    val anyRunning = blocks.any { (it as? RenderBlock.ToolInvocation)?.status == ToolVisualStatus.RUNNING }

    var userExpanded by rememberSaveable(blocks.map { it.key }.joinToString()) {
        mutableStateOf(false)
    }
    val expanded = userExpanded || anyRunning

    Box(modifier = modifier.fillMaxWidth()) {
        // 组壳左缘：2dp 紫条（AI 在场标记），贴住内容同高
        Box(
            modifier = Modifier
                .width(2.dp)
                .matchParentSize()
                .background(colors.thinking, androidx.compose.foundation.shape.RoundedCornerShape(1.dp)),
        )
        Spacer(Modifier.width(Dimens.spaceS))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Dimens.spaceS + 2.dp),
            shape = MaterialTheme.shapes.small,
            color = androidx.compose.ui.graphics.Color.Transparent,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 折叠摘要头：步数徽标 + 箭头。折叠态 = 全子块摘要堆叠。
                Row(
                    modifier = Modifier
                        .appClickable { userExpanded = !userExpanded }
                        .padding(vertical = Dimens.spaceXS),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${blocks.size} 步",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.thinking,
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                        contentDescription = "展开/折叠",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                AnimatedVisibility(visible = expanded) {
                    Column {
                        for (block in blocks) {
                            when (block) {
                                is RenderBlock.Thinking -> AppThinkingCollapsed(block)
                                is RenderBlock.ToolInvocation -> {
                                    Spacer(Modifier.height(Dimens.spaceXS))
                                    AppToolCard(block = block, registry = registry)
                                }
                                else -> {}
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 思考块折叠摘要：「✦ 已思考 Ns」+ 点开全文（§6.8.8 状态机）。 */
@Composable
private fun AppThinkingCollapsed(block: RenderBlock.Thinking) {
    val seconds = (block.text.length.coerceAtLeast(1) / 30).coerceIn(1, 99)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .appClickable {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "\u2726 已思考 ${seconds}s",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun Modifier.appClickable(onClick: () -> Unit): Modifier =
    clickable(interactionSource = null, indication = null, onClick = onClick)