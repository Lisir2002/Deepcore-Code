package com.deepcode.designsystem.render

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deepcode.core.model.ApprovalScope
import com.deepcode.core.model.RiskLevel
import com.deepcode.core.model.ToolCall
import com.deepcode.core.model.ToolKind
import com.deepcode.core.model.ToolOutput
import com.deepcode.core.uistate.AppBlockGroupReducer
import com.deepcode.core.uistate.NoticeKind
import com.deepcode.core.uistate.RenderBlock
import com.deepcode.core.uistate.ToolVisualStatus
import com.deepcode.designsystem.components.AppCodeBlock
import com.deepcode.designsystem.components.messaging.AppBlockGroup
import com.deepcode.designsystem.components.messaging.StreamEmittedCursor
import com.deepcode.designsystem.components.messaging.ToolCardRegistry
import com.deepcode.designsystem.components.messaging.defaultRegistry
import com.deepcode.designsystem.components.AppPrimaryButton
import com.deepcode.designsystem.components.AppSecondaryButton
import com.deepcode.designsystem.components.AppStatusChip
import com.deepcode.designsystem.components.AppTextButton
import com.deepcode.designsystem.theme.BLOCK_GAP
import com.deepcode.designsystem.theme.Dimens
import com.deepcode.designsystem.theme.TypeScale
import com.deepcode.designsystem.theme.appColors

/**
 * 渲染块的唯一可视化实现。
 *
 * 这一行是这个设计系统的核心承诺：
 *   **任何地方要展示 Agent 的执行过程，都调用 TranscriptList。**
 * 不允许某个页面自己写一遍工具卡片——那正是"UI 越写越乱"的起点。
 */
@Composable
fun TranscriptList(
    blocks: List<RenderBlock>,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    registry: ToolCardRegistry = defaultRegistry,
    onApprove: (ToolCall, ApprovalScope) -> Unit = { _, _ -> },
    onDeny: (ToolCall) -> Unit = {},
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    // §6.8.3 聚组：连续 thinking/tool 聚为 Group（纯 Kotlin），text 块截断分组。
    val grouped = remember(blocks) { AppBlockGroupReducer.group(blocks) }
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = Dimens.screenPaddingHorizontal,
            vertical = Dimens.screenPaddingVertical,
        ),
        verticalArrangement = Arrangement.spacedBy(BLOCK_GAP),
    ) {
        if (leading != null) {
            item("__leading") { leading() }
        }
        items(items = grouped, key = { it.key }) { block ->
            RenderBlockView(
                block = block,
                registry = registry,
                onApprove = onApprove,
                onDeny = onDeny,
            )
        }
        if (trailing != null) {
            item("__trailing") { trailing() }
        }
    }
}

@Composable
fun RenderBlockView(
    block: RenderBlock,
    modifier: Modifier = Modifier,
    registry: ToolCardRegistry = defaultRegistry,
    onApprove: (ToolCall, ApprovalScope) -> Unit = { _, _ -> },
    onDeny: (ToolCall) -> Unit = {},
) {
    when (block) {
        is RenderBlock.UserMessage -> UserMessageView(block, modifier)
        is RenderBlock.AssistantText -> AssistantTextView(block, modifier)
        is RenderBlock.Thinking -> ThinkingView(block, modifier)
        is RenderBlock.ToolInvocation -> ToolInvocationView(block, modifier, onApprove, onDeny)
        is RenderBlock.Group -> AppBlockGroup(
            blocks = block.blocks,
            registry = registry,
            onApprove = onApprove,
            onDeny = onDeny,
            modifier = modifier,
        )
        is RenderBlock.Notice -> NoticeView(block, modifier)
        is RenderBlock.TurnFooter -> TurnFooterView(block, modifier)
    }
}

// ─────────────────────────── 各类渲染 ───────────────────────────

@Composable
private fun UserMessageView(block: RenderBlock.UserMessage, modifier: Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(
            shape = RoundedCornerShape(Dimens.bubbleRadius),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Column(modifier = Modifier.padding(horizontal = Dimens.spaceM, vertical = Dimens.spaceS)) {
                Text(text = block.text, style = MaterialTheme.typography.bodyMedium)
                if (block.attachmentLabels.isNotEmpty()) {
                    Spacer(Modifier.height(Dimens.spaceXS))
                    Text(
                        text = block.attachmentLabels.joinToString("、"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistantTextView(block: RenderBlock.AssistantText, modifier: Modifier) {
    // §6.8.5 AI 全宽文档流：不使用气泡，直接铺满内容区渲染 Markdown。
    // 零 layout shift：代码块空壳 + 渐进填充；活光标贴文本末尾，流结束自动消失。
    Column(modifier = modifier.fillMaxWidth()) {
        MarkdownContent(markdown = block.text)
        if (block.streaming) {
            Spacer(Modifier.height(Dimens.spaceXS))
            StreamEmittedCursor(active = true)
        }
    }
}

@Composable
private fun ThinkingView(block: RenderBlock.Thinking, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val preview = remember(block.text) { block.text.replace('\n', ' ').take(80) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.radiusM),
        color = Color.Transparent,
        contentColor = appColors().thinking,
    ) {
        Column(modifier = Modifier.padding(horizontal = Dimens.spaceS, vertical = Dimens.spaceXS)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = Dimens.spaceXS),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.size(Dimens.spaceXS))
                Text(
                    text = if (expanded) "思考过程" else "思考过程：$preview…",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Text(
                    text = block.text,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = TypeScale.bodySmall),
                    modifier = Modifier.padding(bottom = Dimens.spaceXS),
                )
            }
        }
    }
}

@Composable
private fun ToolInvocationView(
    block: RenderBlock.ToolInvocation,
    modifier: Modifier,
    onApprove: (ToolCall, ApprovalScope) -> Unit,
    onDeny: (ToolCall) -> Unit,
) {
    val colors = appColors()
    val (statusText, statusColor) = when (block.status) {
        ToolVisualStatus.AWAITING_APPROVAL -> "待确认" to colors.toolAwaiting
        ToolVisualStatus.RUNNING -> "执行中" to colors.toolRunning
        ToolVisualStatus.SUCCEEDED -> "已完成" to colors.toolSuccess
        ToolVisualStatus.FAILED -> "失败" to colors.toolFailed
        ToolVisualStatus.DENIED -> "已拒绝" to colors.toolFailed
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.radiusM),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(Dimens.spaceM)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = block.toolName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.size(Dimens.spaceS))
                AppStatusChip(text = statusText, containerColor = statusColor.copy(alpha = 0.16f), contentColor = statusColor)
                if (block.risk != RiskLevel.READ_ONLY) {
                    Spacer(Modifier.size(Dimens.spaceXS))
                    AppStatusChip(text = riskLabel(block.risk))
                }
                Spacer(Modifier.weight(1f))
                if (block.durationMs > 0) {
                    Text(
                        text = "${block.durationMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (block.argumentsSummary.isNotBlank()) {
                Spacer(Modifier.height(Dimens.spaceXS))
                Text(
                    text = block.argumentsSummary,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                )
            }

            // 等待用户拍板：这是 Agent 安全模型在界面上的落点
            if (block.status == ToolVisualStatus.AWAITING_APPROVAL) {
                Spacer(Modifier.height(Dimens.spaceM))
                Text(
                    text = "这个操作需要你确认：${block.toolName}（${riskLabel(block.risk)}）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(Dimens.spaceS))
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
                    AppPrimaryButton(text = "允许一次", onClick = { onApprove(block.call, ApprovalScope.ONCE) })
                    AppSecondaryButton(text = "本次会话都允许", onClick = { onApprove(block.call, ApprovalScope.SESSION) })
                    AppTextButton(text = "拒绝", onClick = { onDeny(block.call) })
                }
            }

            if (block.progressText.isNotBlank() && block.status == ToolVisualStatus.RUNNING) {
                Spacer(Modifier.height(Dimens.spaceS))
                AppCodeBlock(text = block.progressText, maxLines = 12)
            }

            block.output?.let { output ->
                Spacer(Modifier.height(Dimens.spaceS))
                ToolOutputView(output)
            }

            block.error?.let { error ->
                Spacer(Modifier.height(Dimens.spaceS))
                Text(
                    text = "错误 · ${error.code}：${error.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** 工具产物的渲染。按产物类型分发，而不是按工具名 if/else。 */
@Composable
fun ToolOutputView(output: ToolOutput, modifier: Modifier = Modifier) {
    when (output) {
        is ToolOutput.Text -> AppCodeBlock(text = output.text, modifier = modifier, maxLines = 30)
        is ToolOutput.Diff -> DiffView(output, modifier)
        is ToolOutput.FileList -> FileListView(output, modifier)
        is ToolOutput.SearchHits -> SearchHitsView(output, modifier)
        is ToolOutput.KeyValues -> KeyValuesView(output, modifier)
        // 以下三种随 MCP 工具桥一起引入（T1）：MCP 的 content block 不止文本，
        // 图片/资源链接/结构化 JSON 都必须有落点，否则 when 不穷尽直接编译失败。
        is ToolOutput.Image -> ToolImageView(output, modifier)
        is ToolOutput.ResourceLink -> ResourceLinkView(output, modifier)
        is ToolOutput.Structured -> AppCodeBlock(
            text = output.json.toString(),
            modifier = modifier,
            maxLines = 30,
        )
        ToolOutput.Empty -> Unit
    }
}

/**
 * 图片产物（MCP content type=image）。
 *
 * base64 解码放进 [remember]：图片动辄几 MB，每次重组都重解一遍会直接把 UI 卡住。
 * 解码失败不崩页面——坏数据要能被看见（降级成一行提示），而不是让整个会话渲染失败。
 */
@Composable
private fun ToolImageView(image: ToolOutput.Image, modifier: Modifier) {
    val bitmap = remember(image.base64) {
        runCatching {
            val bytes = android.util.Base64.decode(image.base64, android.util.Base64.DEFAULT)
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
    if (bitmap == null) {
        AppCodeBlock(
            text = "[图片解析失败：${image.mimeType}]",
            modifier = modifier,
            maxLines = 4,
        )
    } else {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "工具产物图片（${image.mimeType}）",
            modifier = modifier.fillMaxWidth(),
        )
    }
}

/** 资源链接产物（MCP content type=resource_link）：名称 + URI，不做跳转（URI 未必是 http）。 */
@Composable
private fun ResourceLinkView(link: ToolOutput.ResourceLink, modifier: Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = link.name ?: "资源链接",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = link.uri,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
    }
}

@Composable
private fun DiffView(diff: ToolOutput.Diff, modifier: Modifier) {
    val colors = appColors()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.codeSurface, RoundedCornerShape(Dimens.radiusS))
            .padding(Dimens.spaceXS),
    ) {
        Text(
            text = diff.path,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Dimens.spaceXXS))
        diff.unified.lineSequence().take(120).forEach { line ->
            val background = when {
                line.startsWith("+") -> colors.diffAdd
                line.startsWith("-") -> colors.diffRemove
                else -> Color.Transparent
            }
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = TypeScale.code,
                    lineHeight = TypeScale.codeLineHeight,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(background)
                    .padding(horizontal = Dimens.spaceXS),
            )
        }
    }
}

@Composable
private fun FileListView(list: ToolOutput.FileList, modifier: Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        list.entries.take(30).forEach { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (entry.isDirectory) "📁" else "📄",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.size(Dimens.spaceXS))
                Text(
                    text = entry.path.removePrefix(list.root),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SearchHitsView(hits: ToolOutput.SearchHits, modifier: Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "匹配 ${hits.hits.size} 处：${hits.query}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Dimens.spaceXS))
        hits.hits.take(20).forEach { hit ->
            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                Text(
                    text = "${hit.path}:${hit.line}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(Dimens.spaceXS))
                Text(
                    text = hit.snippet,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 1,
                )
            }
        }
        if (hits.truncated) {
            Text(
                text = "（结果已截断）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun KeyValuesView(values: ToolOutput.KeyValues, modifier: Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        values.pairs.forEach { (key, value) ->
            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                Text(
                    text = key,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(min = 72.dp),
                )
                Text(text = value, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun NoticeView(block: RenderBlock.Notice, modifier: Modifier) {
    val (icon, color) = when (block.kind) {
        NoticeKind.INFO -> Icons.Filled.Info to MaterialTheme.colorScheme.primary
        NoticeKind.WARNING -> Icons.Filled.Warning to appColors().toolAwaiting
        NoticeKind.ERROR -> Icons.Filled.Close to MaterialTheme.colorScheme.error
        NoticeKind.COMPACTED -> Icons.Filled.Info to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spaceS, vertical = Dimens.spaceXS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.size(Dimens.spaceXS))
        Text(text = block.text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun TurnFooterView(block: RenderBlock.TurnFooter, modifier: Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spaceS, vertical = Dimens.spaceXS),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceM),
    ) {
        Text(
            text = "↑${block.inputTokens} ↓${block.outputTokens} tokens",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "${block.iterations} 步 · ${block.durationMs}ms",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun riskLabel(risk: RiskLevel): String = when (risk) {
    RiskLevel.READ_ONLY -> "只读"
    RiskLevel.WRITE -> "写入"
    RiskLevel.DESTRUCTIVE -> "危险"
    RiskLevel.NETWORK -> "联网"
    RiskLevel.PRIVILEGED -> "敏感权限"
}
