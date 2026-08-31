package com.deepcode.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
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
import com.deepcode.designsystem.components.AppScaffold
import com.deepcode.designsystem.components.AppStatusChip
import com.deepcode.designsystem.components.AppSwitch
import com.deepcode.designsystem.components.AppText
import com.deepcode.designsystem.components.AppTextButton
import com.deepcode.designsystem.components.AppTextField
import com.deepcode.designsystem.components.AppTextStyle
import com.deepcode.designsystem.components.AppTextTone
import com.deepcode.designsystem.theme.Dimens
import org.koin.androidx.compose.koinViewModel

/**
 * MCP server 管理页。
 *
 * 与 ChatScreen 同一套路：页面只是把 ViewModel 状态接到 designsystem 组件上，
 * 自己不做布局、不碰 Material3（被 :lint 拦截）。
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel(),
    onBack: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var trusted by remember { mutableStateOf(false) }

    val servers by viewModel.servers.collectAsStateWithLifecycle()

    AppScaffold(
        title = "MCP 服务器",
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
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceM),
        ) {
            // ── 添加表单 ──
            item {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppText(
                                "受信任（允许按 annotations 降档风险）",
                                style = AppTextStyle.Body,
                            )
                            Spacer(Modifier.weight(1f))
                            AppSwitch(checked = trusted, onCheckedChange = { trusted = it })
                        }
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

            // ── 已配置列表 ──
            items(servers, key = { it.id }) { s ->
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppText("受信任", style = AppTextStyle.Body)
                            Spacer(Modifier.weight(1f))
                            AppSwitch(
                                checked = s.trusted,
                                onCheckedChange = { viewModel.setTrusted(s.id, it) },
                            )
                        }
                        Row {
                            Spacer(Modifier.weight(1f))
                            AppTextButton(
                                text = "删除",
                                onClick = { viewModel.removeServer(s.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}
