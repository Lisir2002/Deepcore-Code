package com.deepcode.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepcode.designsystem.components.AppCard
import com.deepcode.designsystem.components.AppPrimaryButton
import com.deepcode.designsystem.components.AppStatusChip
import com.deepcode.designsystem.components.AppSwitch
import com.deepcode.designsystem.components.AppText
import com.deepcode.designsystem.components.AppTextButton
import com.deepcode.designsystem.components.AppTextField
import com.deepcode.designsystem.components.form.AppSettingRow
import com.deepcode.designsystem.components.scaffold.DetailScaffold
import com.deepcode.designsystem.theme.AppTextStyle
import com.deepcode.designsystem.theme.AppTextTone
import com.deepcode.designsystem.theme.Dimens
import org.koin.androidx.compose.koinViewModel

/**
 * 设置二级页 · 技能。
 *
 * 承载「连接」能力的真实消费面：MCP 服务器（增删改 + trusted 开关）+ 技能目录
 * （骨架占位，后续接 SkillLoader 的目录浏览）。原「模型与连接」页的 MCP 部分整体迁入。
 *
 * MCP 服务器行用 :designsystem 泛型 [AppSettingRow] + trailing 槽（AppSwitch /
 * trusted），在同一行把「受信任」布尔收敛，不再业务层自造 Row。
 */
@Composable
fun SettingsSkillsScreen(
    onBack: (() -> Unit)?,
    onRequestSelection: (() -> Unit)? = null,
) {
    val viewModel: SettingsViewModel = koinViewModel()
    val servers by viewModel.servers.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var trusted by remember { mutableStateOf(false) }

    DetailScaffold(
        title = "技能",
        onBack = onBack,
        actions = {
            AppTextButton(text = "重连", onClick = viewModel::reconnectAll)
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Dimens.spaceL),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.spaceM),
        ) {
            // ── 技能目录（骨架占位，后续 SkillLoader 目录浏览 + 渐进披露开关）──
            item(key = "skill_catalog") {
                AppCard {
                    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.spaceXXS)) {
                        AppText("技能目录", style = AppTextStyle.Body)
                        AppText(
                            "按 Agent Skills 开放标准（agentskills.io）装载的 SKILL.md 目录。骨架已就位，浏览与启用/禁用开关排期实现。",
                            style = AppTextStyle.Caption,
                            tone = AppTextTone.Muted,
                        )
                        AppSettingRow(
                            label = "浏览技能包",
                            supporting = "SKILL.md 目录 / 渐进披露",
                            onClick = onRequestSelection,
                        )
                    }
                }
            }

            // ── MCP 服务器列表 ──
            items(servers, key = { it.id }) { s ->
                AppCard {
                    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.spaceXXS)) {
                        Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = Dimens.spaceXXS),
                        ) {
                            AppText(
                                s.displayName.ifBlank { s.id },
                                style = AppTextStyle.Title,
                            )
                            Spacer(Modifier.weight(1f))
                            AppStatusChip(
                                text = if (s.connected) "已连接 · ${s.toolCount} 工具" else "未连接",
                            )
                        }
                        AppText(s.url, style = AppTextStyle.Caption, tone = AppTextTone.Muted)
                        s.error?.let {
                            AppText(
                                "错误：$it",
                                style = AppTextStyle.Caption,
                                tone = AppTextTone.Error,
                            )
                        }
                        // 受信任开关：泛型行内条目 + trailing 槽（AppSettingRow 展现示例）
                        AppSettingRow(
                            label = "受信任",
                            supporting = "允许按 annotations 降档风险",
                            onClick = null,
                            trailing = {
                                AppSwitch(
                                    checked = s.trusted,
                                    onCheckedChange = { viewModel.setTrusted(s.id, it) },
                                )
                            },
                        )
                        Row(
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            AppTextButton(
                                text = "删除",
                                onClick = { viewModel.removeServer(s.id) },
                            )
                        }
                    }
                }
            }

            // ── 添加表单 ──
            item(key = "add_form") {
                AppCard {
                    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.spaceS)) {
                        AppText("添加服务器", style = AppTextStyle.Title)
                        AppTextField(
                            label = "名称（唯一标识，可空则取 URL 哈希）",
                            value = name,
                            onValueChange = { name = it },
                        )
                        AppTextField(
                            label = "URL（Streamable HTTP）",
                            value = url,
                            onValueChange = { url = it },
                        )
                        AppSettingRow(
                            label = "受信任",
                            supporting = "允许按 annotations 降档风险",
                            onClick = null,
                            trailing = {
                                AppSwitch(checked = trusted, onCheckedChange = { trusted = it })
                            },
                        )
                        AppPrimaryButton(
                            text = "添加",
                            enabled = url.isNotBlank(),
                            onClick = {
                                viewModel.addServer(name, url, trusted)
                                name = ""
                                url = ""
                                trusted = false
                            },
                        )
                    }
                }
            }
        }
    }
}