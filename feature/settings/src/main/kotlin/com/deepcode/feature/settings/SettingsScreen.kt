package com.deepcode.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.deepcode.designsystem.components.AppCard
import com.deepcode.designsystem.components.AppScaffold
import com.deepcode.designsystem.components.form.AppSettingRow
import com.deepcode.designsystem.theme.Dimens

/**
 * 设置页入口（底部「设置」tab 首屏）。
 *
 * 骨架（设计定稿）：**入口列表 + 二级 Detail**。本页只做分组的行条目，
 * 不承载任何内容；点击经 Navigation Compose 子路由压栈进独立的二级页
 * （外观 / 模型 / 技能 / 日志 / 关于），由 DetailScaffold 自带返回。
 *
 * 行条目统一走 :designsystem 的泛型 [AppSettingRow]（跳转类：右侧 chevron），
 * 不在业务层自造 Row；后续带 Switch/分段控件的高频设置项直接在二级页复用
 * 同一组件 + trailing 槽。
 */
@Composable
fun SettingsScreen(
    onOpenAppearance: () -> Unit,
    onOpenModel: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenLogging: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppScaffold(
        title = "设置",
        modifier = modifier,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceS),
            contentPadding = PaddingValues(
                vertical = Dimens.spaceM,
                horizontal = Dimens.spaceL,
            ),
        ) {
            item(key = "group_settings") {
                AppGroup(
                    entries = listOf(
                        GroupEntry("外观", "深色模式 / 风格包 / 主题导入", onOpenAppearance),
                        GroupEntry("模型", "端点 / API Key / 流式开关", onOpenModel),
                        GroupEntry("技能", "MCP 服务器 / 技能目录", onOpenSkills),
                        GroupEntry("日志", "导出日志包 / 根目录授权 / 同步", onOpenLogging),
                    ),
                )
            }
            item(key = "group_about") {
                AppGroup(
                    entries = listOf(
                        GroupEntry("关于", "版本 / 应用信息", onOpenAbout),
                    ),
                )
            }
        }
    }
}

/** 一个设置的入口条目：标题 + 说明，点击进二级页。 */
private data class GroupEntry(
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit,
)

/** 分组卡片：包若干入口条目，条目间用分隔行展示。 */
@Composable
private fun AppGroup(entries: List<GroupEntry>) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.Top) {
            entries.forEachIndexed { index, entry ->
                AppSettingRow(
                    label = entry.title,
                    supporting = entry.subtitle,
                    onClick = entry.onClick,
                    modifier = Modifier.padding(horizontal = Dimens.spaceS),
                )
                if (index != entries.lastIndex) {
                    GroupDivider()
                }
            }
        }
    }
}

@Composable
private fun GroupDivider() {
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(start = Dimens.spaceS, end = Dimens.spaceS),
        color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant,
    )
}