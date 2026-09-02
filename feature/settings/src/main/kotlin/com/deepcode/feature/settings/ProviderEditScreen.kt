package com.deepcode.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.deepcode.core.agent.spi.ModelProviderIds
import com.deepcode.designsystem.components.AppCard
import com.deepcode.designsystem.components.AppPrimaryButton
import com.deepcode.designsystem.components.AppText
import com.deepcode.designsystem.components.AppTextField
import com.deepcode.designsystem.components.form.AppRadioRow
import com.deepcode.designsystem.components.scaffold.FormScaffold
import com.deepcode.designsystem.theme.AppTextStyle
import com.deepcode.designsystem.theme.Dimens
import org.koin.androidx.compose.koinViewModel

/**
 * 添加供应商流程 · Step1 端点页（决策 P1：三协议全量落地）。
 *
 * - Provider 单选（来自注册表 [com.deepcode.core.agent.spi.ModelProviderRegistry]，决策 D1）。
 * - 选中需配置的协议时展开：Base URL / API Key / Max Tokens（Max Tokens 固定在 Step1）。
 * - 「下一步」把端点草稿暂存进内存 [ProviderEditViewModel.Draft]（不落盘），跳 Step2 选模型。
 * - 选中「演示模型」直接回调 Demo 回退。
 */
@Composable
fun ProviderEditScreen(
    onBack: (() -> Unit)?,
    onNext: ((providerId: String) -> Unit)? = null,
) {
    val viewModel: ProviderEditViewModel = koinViewModel()

    var selectedProviderId by remember {
        mutableStateOf(viewModel.initialProviderId().takeIf { it.isNotBlank() } ?: ModelProviderIds.DEMO)
    }
    var baseUrl by remember { mutableStateOf(viewModel.initialBaseUrl()) }
    var apiKey by remember { mutableStateOf(viewModel.initialApiKey()) }
    var maxTokens by remember { mutableIntStateOf(viewModel.initialMaxTokens()) }

    val selected = viewModel.descriptors.firstOrNull { it.id == selectedProviderId }
    val needsConfig = selected?.requiresConfig == true
    val fieldsReady = baseUrl.isNotBlank() && apiKey.isNotBlank()

    FormScaffold(
        title = "添加供应商",
        onBack = onBack,
        confirm = {
            AppPrimaryButton(
                text = if (needsConfig) "下一步：选择模型" else "使用演示模型",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.spaceL, vertical = Dimens.spaceM),
                enabled = !needsConfig || fieldsReady,
                onClick = {
                    if (needsConfig) {
                        viewModel.commitEndpoint(
                            providerId = selectedProviderId,
                            baseUrl = baseUrl,
                            apiKey = apiKey,
                            maxTokens = if (maxTokens > 0) maxTokens else viewModel.initialMaxTokens(),
                        )
                        onNext?.invoke(selectedProviderId)
                    } else {
                        viewModel.selectDemo()
                        onBack?.invoke()
                    }
                },
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceM),
        ) {
            ProviderSelector(
                descriptors = viewModel.descriptors,
                selectedProviderId = selectedProviderId,
                onSelect = { selectedProviderId = it },
            )

            if (needsConfig) {
                AppCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Dimens.spaceM),
                    ) {
                        AppText("端点与凭据", style = AppTextStyle.Body)
                        AppTextField(
                            label = "Base URL",
                            value = baseUrl,
                            onValueChange = { baseUrl = it },
                            placeholder = providerBaseUrlPlaceholder(selectedProviderId),
                            helperText = providerBaseUrlHint(selectedProviderId),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                        )
                        AppTextField(
                            label = "API Key",
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            placeholder = providerKeyPlaceholder(selectedProviderId),
                            helperText = "明文存储，加密存储排期 M2",
                        )
                        AppTextField(
                            label = "Max Tokens",
                            value = maxTokens.toString(),
                            onValueChange = { raw ->
                                maxTokens = raw.toIntOrNull() ?: 0
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            helperText = "单次生成最大 token 数，缺省按协议默认",
                        )
                    }
                }
            }
        }
    }
}

/** 协议单选组：无表单（如 Demo）与需配置（OpenAI / Anthropic / Gemini）并行展示。 */
@Composable
private fun ColumnScope.ProviderSelector(
    descriptors: List<com.deepcode.core.agent.spi.ModelProviderDescriptor>,
    selectedProviderId: String,
    onSelect: (String) -> Unit,
) {
    AppCard {
        Column(modifier = Modifier.fillMaxWidth()) {
            descriptors.forEachIndexed { index, d ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.spaceS, vertical = Dimens.spaceXXS),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppRadioRow(
                        label = d.displayName,
                        selected = d.id == selectedProviderId,
                        onClick = { onSelect(d.id) },
                    )
                    if (d.requiresConfig) {
                        AppText(
                            "需配置",
                            style = AppTextStyle.Caption,
                        )
                    }
                }
                if (index != descriptors.lastIndex) {
                    androidx.compose.material3.HorizontalDivider(
                        modifier = Modifier.padding(start = Dimens.spaceS, end = Dimens.spaceS),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

private fun providerBaseUrlPlaceholder(providerId: String): String = when (providerId) {
    ModelProviderIds.OPENAI_COMPATIBLE -> "https://api.deepseek.com/v1"
    ModelProviderIds.ANTHROPIC -> "https://api.anthropic.com"
    ModelProviderIds.GEMINI -> "https://generativelanguage.googleapis.com"
    else -> "https://…"
}

private fun providerBaseUrlHint(providerId: String): String = when (providerId) {
    ModelProviderIds.OPENAI_COMPATIBLE -> "OpenAI 兼容端点，需可直达 /chat/completions"
    ModelProviderIds.ANTHROPIC -> "Anthropic 端点，追加 /v1/messages"
    ModelProviderIds.GEMINI -> "Gemini 端点，追加 /v1beta/models/{model}:streamGenerateContent"
    else -> ""
}

private fun providerKeyPlaceholder(providerId: String): String = when (providerId) {
    ModelProviderIds.OPENAI_COMPATIBLE -> "sk-…"
    ModelProviderIds.ANTHROPIC -> "sk-ant-…"
    ModelProviderIds.GEMINI -> "AIza…"
    else -> "…"
}