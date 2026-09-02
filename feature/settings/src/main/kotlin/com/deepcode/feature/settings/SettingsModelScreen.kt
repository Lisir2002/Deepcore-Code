package com.deepcode.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.deepcode.designsystem.components.AppCard
import com.deepcode.designsystem.components.AppStatusChip
import com.deepcode.designsystem.components.AppText
import com.deepcode.designsystem.components.AppTextButton
import com.deepcode.designsystem.components.form.AppSettingRow
import com.deepcode.designsystem.components.overlay.AppConfirmDialog
import com.deepcode.designsystem.components.scaffold.DetailScaffold
import com.deepcode.designsystem.theme.AppTextStyle
import com.deepcode.designsystem.theme.AppTextTone
import com.deepcode.designsystem.theme.Dimens
import com.deepcode.designsystem.theme.appColors
import org.koin.androidx.compose.koinViewModel

/**
 * 设置二级页 · 模型（多模型管理 + 当前接入）。
 *
 * - 顶部「当前接入」状态卡（激活 Provider + 会话模型）。
 * - 下方「已保存模型」列表：点击条目激活（标记"使用中"），每条可删除。
 * - "添加模型"压栈进 [ProviderEditScreen] 分步新增（端点 → 模型），保存后回退重读列表。
 */
@Composable
fun SettingsModelScreen(
    onBack: (() -> Unit)?,
    onOpenProvider: (() -> Unit)? = null,
) {
    val viewModel: SettingsModelViewModel = koinViewModel()
    // 每次进入/返回都重读：二级页保存后 popBack 回来要能看到最新列表
    val active = viewModel.activeState()
    var models by remember { mutableStateOf(viewModel.models()) }
    var pendingDelete by remember { mutableStateOf<SettingsModelViewModel.ItemUi?>(null) }

    DetailScaffold(
        title = "模型",
        onBack = onBack,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Dimens.spaceL),
            contentPadding = PaddingValues(vertical = Dimens.spaceM),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceM),
        ) {
            // —— 当前接入状态卡 ——
            item(key = "active") {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceXXS)) {
                        AppText("当前接入", style = AppTextStyle.Body)
                        AppStatusChip(
                            text = if (active.isComplete) "已配置 · ${active.providerName}" else "未配置（暂用演示模型）",
                            containerColor = if (active.isComplete) {
                                appColors().successContainer
                            } else {
                                appColors().surfaceElevated
                            },
                        )
                        AppText(
                            "会话模型：${active.modelId}",
                            style = AppTextStyle.Caption,
                            tone = AppTextTone.Muted,
                            modifier = Modifier.padding(top = Dimens.spaceXXS),
                        )
                    }
                }
            }

            // —— 已保存模型列表 ——
            item(key = "models_header") {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceXXS)) {
                        AppText(
                            "已保存模型（${models.size}）",
                            style = AppTextStyle.Body,
                        )
                        AppText(
                            "点击条目切换为当前使用模型；删除后可重新添加。",
                            style = AppTextStyle.Caption,
                            tone = AppTextTone.Muted,
                        )
                        if (models.isEmpty()) {
                            AppText(
                                "暂无已保存模型",
                                style = AppTextStyle.Caption,
                                tone = AppTextTone.Muted,
                                modifier = Modifier.padding(top = Dimens.spaceS),
                            )
                        }
                        models.forEach { item ->
                            AppModelRow(
                                item = item,
                                onActivate = {
                                    viewModel.activateModel(item.id)
                                    models = viewModel.models()
                                },
                                onDelete = { pendingDelete = item },
                            )
                        }
                    }
                }
            }

            // —— 添加入口 ——
            item(key = "add") {
                AppSettingRow(
                    label = "添加模型",
                    supporting = "选择协议 / 端点 / API Key / 模型",
                    onClick = onOpenProvider,
                )
            }
        }
    }

    // 删除确认对话框（破坏性操作，需双重确认）
    pendingDelete?.let { item ->
        AppConfirmDialog(
            title = "删除模型",
            body = "确定删除「${item.label}」吗？此操作不可撤销。",
            confirmText = "删除",
            danger = true,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                viewModel.removeModel(item.id)
                models = viewModel.models()
                pendingDelete = null
            },
        )
    }
}

/** 单条已保存模型行：点击激活，trailing 展示"使用中"或"删除"。 */
@Composable
private fun AppModelRow(
    item: SettingsModelViewModel.ItemUi,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
) {
    AppSettingRow(
        label = item.label,
        supporting = "${item.modelId} · ${item.providerName}",
        onClick = onActivate,
        trailing = {
            Row {
                if (item.isActive) {
                    AppStatusChip(text = "使用中")
                }
                AppTextButton(
                    text = "删除",
                    onClick = onDelete,
                    modifier = Modifier.padding(start = Dimens.spaceS),
                )
            }
        },
    )
}