package com.deepcode.designsystem.render

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepcode.designsystem.components.AppCodeBlock
import com.deepcode.designsystem.theme.Dimens
import com.deepcode.designsystem.theme.TypeScale
import com.deepcode.designsystem.theme.appColors

/**
 * Markdown 流式渲染（§6.8.5）：AI 全宽文档流的正文渲染器。
 *
 * 满足"零 layout shift"：代码块在起始 token 即渲染空壳（`AppCodeBlock` 稳定 min-height），内容渐进填充，
 * 不出现"文本流重排跳变"。支持聚焦子集：
 *   - 围栏代码块 ` ``` `
 *   - 标题 `#`…`####`
 *   - 有序/无序列表、引用 `> `、分割线 `---`
 *   - 段落内 **加粗** 与 `` `行内代码` ``
 * 表格/复杂块 v1 不进（需布局回流的虚拟化表格留待完善），避免过度设计。
 */
@Composable
fun MarkdownContent(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(markdown) { parse(markdown) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceS),
    ) {
        for (block in blocks) {
            when (block) {
                is MdBlock.Paragraph -> InlineText(block.text, MaterialTheme.typography.bodyMedium)

                is MdBlock.Heading -> InlineText(
                    block.text,
                    if (block.level == 1) MaterialTheme.typography.titleLarge
                    else MaterialTheme.typography.titleMedium,
                    bold = true,
                )

                is MdBlock.Code -> AppCodeBlock(
                    text = block.text,
                    modifier = Modifier.heightIn(min = 24.dp), // 空壳 min-height，防 layout shift
                )

                is MdBlock.BulletList -> block.items.forEach { item ->
                    Row {
                        Text(
                            "•  ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        InlineText(item, MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                }

                is MdBlock.OrderedList -> block.items.forEachIndexed { index, item ->
                    Row {
                        Text(
                            "${index + 1}.  ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        InlineText(item, MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                }

                is MdBlock.Quote -> Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Dimens.radiusS),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    InlineText(
                        block.text,
                        MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(Dimens.spaceS),
                    )
                }

                MdBlock.Rule -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

/** 段落内联渲染：加粗与行内代码走 AnnotatedString span，避免片段重排，且不打断流式光标。 */
@Composable
private fun InlineText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
) {
    val colors = appColors()
    val base = if (bold) style.copy(fontWeight = FontWeight.Bold) else style
    val annotated = remember(text, base, colors.codeSurface, colors.codeBorder) {
        buildInline(text, base, colors.codeSurface, colors.codeBorder)
    }
    Text(text = annotated, style = base, modifier = modifier)
}

private fun buildInline(text: String, style: TextStyle, codeSurface: Color, codeBorder: Color): AnnotatedString {
    val base = style.toSpanStyle()
    val code = base.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = TypeScale.code,
        background = codeSurface,
    )
    val bold = base.copy(fontWeight = FontWeight.Bold)
    var i = 0
    return buildAnnotatedString {
        while (i < text.length) {
            // 行内代码 `...`
            if (text[i] == '`') {
                val close = text.indexOf('`', i + 1)
                if (close > i) {
                    withStyle(code) { append(text.substring(i, close + 1)) }
                    i = close + 1
                    continue
                }
            }
            // 加粗 **...**
            if (i + 1 < text.length && text[i] == '*' && text[i + 1] == '*') {
                val close = text.indexOf("**", i + 2)
                if (close > i + 1) {
                    withStyle(bold) { append(text.substring(i + 2, close)) }
                    i = close + 2
                    continue
                }
            }
            // 找到下一特殊位置，一次吞掉普通段（样式由外层 Text 的 base 提供）
            val nextBold = text.indexOf("**", i).let { if (it < 0) text.length else it }
            val nextCode = text.indexOf('`', i).let { if (it < 0) text.length else it }
            val next = minOf(nextBold, nextCode)
            append(text.substring(i, next))
            i = next
        }
    }
}

// ─────────────────────────── 解析 ───────────────────────────

private sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Code(val text: String) : MdBlock
    data class BulletList(val items: List<String>) : MdBlock
    data class OrderedList(val items: List<String>) : MdBlock
    data class Quote(val text: String) : MdBlock
    data object Rule : MdBlock
}

private fun parse(markdown: String): List<MdBlock> {
    val lines = markdown.split("\n")
    val blocks = mutableListOf<MdBlock>()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        if (line.trim().isEmpty()) { i++; continue }

        // 围栏代码块
        if (isFence(line)) {
            val buf = mutableListOf<String>()
            var j = i + 1
            while (j < lines.size && !isFence(lines[j])) {
                buf.add(lines[j]); j++
            }
            blocks.add(MdBlock.Code(buf.joinToString("\n")))
            i = j + 1
            continue
        }

        // 标题
        val level = headingLevel(line)
        if (level > 0) {
            blocks.add(MdBlock.Heading(level, line.trim().trimStart('#').trim()))
            i++; continue
        }

        // 分割线
        if (isRule(line)) {
            blocks.add(MdBlock.Rule); i++; continue
        }

        // 引用
        if (line.startsWith(">")) {
            blocks.add(MdBlock.Quote(line.trim().substringAfter('>').trim())); i++; continue
        }

        // 列表（连续行聚成一块）
        val ordered = isOrderedItem(line)
        if (isUnorderedItem(line) || ordered) {
            val items = mutableListOf(trimItem(line))
            i++
            while (i < lines.size && (isUnorderedItem(lines[i]) || isOrderedItem(lines[i]))) {
                items.add(trimItem(lines[i])); i++
            }
            if (ordered) blocks.add(MdBlock.OrderedList(items))
            else blocks.add(MdBlock.BulletList(items))
            continue
        }

        // 段落：聚合到下一个结构性行
        val para = mutableListOf(line)
        i++
        while (i < lines.size) {
            val l = lines[i]
            if (l.trim().isEmpty() || isFence(l) || headingLevel(l) > 0 || isRule(l) ||
                isUnorderedItem(l) || isOrderedItem(l) || l.startsWith(">")
            ) break
            para.add(l); i++
        }
        blocks.add(MdBlock.Paragraph(para.joinToString("\n")))
    }
    return blocks
}

private fun isFence(line: String) = line.trim().startsWith("```")

private fun headingLevel(line: String): Int = when {
    line.trim().startsWith("#### ") -> 4
    line.trim().startsWith("### ") -> 3
    line.trim().startsWith("## ") -> 2
    line.trim().startsWith("# ") -> 1
    else -> 0
}

private fun isRule(line: String): Boolean {
    val t = line.trim()
    return t.matches(Regex("-{3,}"))
}

private fun isUnorderedItem(line: String) = line.trim().let { it.startsWith("- ") || it.startsWith("* ") }

private fun isOrderedItem(line: String) = line.trim().matches(Regex("\\d+\\.\\s+.+"))

private fun trimItem(line: String): String =
    line.trim().replaceFirst(Regex("^([-*]|\\d+\\.)\\s+"), "")