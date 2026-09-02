package com.deepcode.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.deepcode.designsystem.components.AppCard
import com.deepcode.designsystem.components.AppStatusChip
import com.deepcode.designsystem.components.AppText
import com.deepcode.designsystem.components.form.AppSettingRow
import com.deepcode.designsystem.components.scaffold.DetailScaffold
import com.deepcode.designsystem.theme.AppTextStyle
import com.deepcode.designsystem.theme.AppTextTone
import com.deepcode.designsystem.theme.Dimens
import com.deepcode.designsystem.theme.appColors
import org.koin.androidx.compose.koinViewModel

/**
 * 设置二级页 · 模型（决策 D5：状态 + 入口）。
 *
 * 只读展示当前生效 Provider 与会话模型；配置在 [ProviderEditScreen] 编辑，保存后
 * popBack 回来重读快照。展示位：
 *  `providerName` 当前 Provider、(OpenAI) `detail` 端点、(Demo) 「未接真实端点」、
 *  `modelId` 会话模型、`isComplete` 是否可用（缺字段则按 Demo 兜底）。
 */
@Composable
fun SettingsModelScreen(
    onBack: (() -> Unit)?,
    onOpenProvider: (() -> Unit)? = null,
) {
    val viewModel: SettingsModelViewModel = koinViewModel()
    val state = viewModel.currentState()

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
            item(key = "model_status") {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceXXS)) {
                        AppText("当前接入", style = AppTextStyle.Body)
                        AppStatusChip(
                            text = if (state.isComplete) "已配置 · ${state.providerName}" else "未配置完整（暂用演示模型）",
                            containerColor = if (state.isComplete) {
                                appColors().successContainer
                            } else {
                                appColors().surfaceElevated
                            },
                        )
                        AppText(
                            "端点：${state.detail}",
                            style = AppTextStyle.Caption,
                            tone = AppTextTone.Muted,
                            modifier = Modifier.padding(top = Dimens.spaceXXS),
                        )
                        AppText(
                            "会话模型：${state.modelId}",
                            style = AppTextStyle.Caption,
                            tone = AppTextTone.Muted,
                        )
                        AppSettingRow(
                            label = "配置 Provider",
                            supporting = "选择服务 / 端点 / API Key / 模型",
                            onClick = onOpenProvider,
                            modifier = Modifier.padding(top = Dimens.spaceS),
                        )
                    }
                }
            }
        }
    }
}