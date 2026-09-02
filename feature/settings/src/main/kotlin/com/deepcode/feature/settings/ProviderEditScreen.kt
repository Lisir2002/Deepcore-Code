package com.deepcode.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.deepcode.core.agent.spi.ModelProviderDescriptor
import com.deepcode.core.agent.spi.ModelProviderIds
import com.deepcode.designsystem.components.AppCard
import com.deepcode.designsystem.components.AppPrimaryButton
import com.deepcode.designsystem.components.AppText
import com.deepcode.designsystem.components.AppTextField
import com.deepcode.designsystem.components.form.AppSettingRow
import com.deepcode.designsystem.components.scaffold.FormScaffold
import com.deepcode.designsystem.theme.AppTextStyle
import com.deepcode.designsystem.theme.Dimens
import org.koin.androidx.compose.koinViewModel

/**
 * 添加供应商流程 · Step1 端点页（决策 P1：三协议全量落地）。
 *
 * 协议选择 = **下拉式手风琴**：每条协议是一行可展开项，点击展开其配置表单
 * （Base URL / API Key / Max Tokens，Max Tokens 固定在 Step1），点其它协议收起当前。
 * 「下一步」把端点草稿暂存进共享单例 [ProviderEditFlow]，跳 Step2 选模型。
 * 忘选需配置的协议而只留「演示模型」直接回调 Demo 回退。
 */
@Composable
fun ProviderEditScreen(
    onBack: (() -> Unit)?,
    onNext: ((providerId: String) -> Unit)? = null,
) {
    val viewModel: ProviderEditViewModel = koinViewModel()

    var expandedProviderId by remember {
        mutableStateOf(viewModel.initialProviderId().takeIf { it != ModelProviderIds.DEMO && it.isNotBlank() } ?: "")
    }
    var baseUrl by remember { mutableStateOf(viewModel.initialBaseUrl()) }
    var apiKey by remember { mutableStateOf(viewModel.initialApiKey()) }
    var maxTokens by remember { mutableIntStateOf(viewModel.initialMaxTokens()) }

    val expanded = viewModel.descriptors.firstOrNull { it.id == expandedProviderId }
    val needsConfig = expanded?.requiresConfig == true
    val fieldsReady = baseUrl.isNotBlank() && apiKey.isNotBlank()
    // 演示模型始终可用；需配置的协议必须填齐 Base URL + API Key 才放行去选模型
    val canContinue = !needsConfig || fieldsReady

    FormScaffold(
        title = "添加供应商",
        onBack = onBack,
        confirm = {
            AppPrimaryButton(
                text = if (needsConfig) "下一步：选择模型" else "使用演示模型",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.spaceL, vertical = Dimens.spaceM),
                enabled = canContinue,
                onClick = {
                    if (needsConfig) {
                        viewModel.commitEndpoint(
                            providerId = expandedProviderId,
                            baseUrl = baseUrl,
                            apiKey = apiKey,
                            maxTokens = if (maxTokens > 0) maxTokens else viewModel.initialMaxTokens(),
                        )
                        onNext?.invoke(expandedProviderId)
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
            AppCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    viewModel.descriptors.forEach { d ->
                        ProviderAccordionItem(
                            descriptor = d,
                            expanded = d.id == expandedProviderId,
                            onToggle = { expandedProviderId = if (d.id == expandedProviderId) "" else d.id },
                        ) {
                            if (d.requiresConfig) {
                                ProviderConfigFields(
                                    baseUrl = baseUrl,
                                    apiKey = apiKey,
                                    maxTokens = maxTokens,
                                    providerId = d.id,
                                    onBaseUrl = { baseUrl = it },
                                    onApiKey = { apiKey = it },
                                    onMaxTokens = { maxTokens = it },
                                )
                            } else {
                                AppText(
                                    "无需配置，直接使用内置演示行为。",
                                    style = AppTextStyle.Caption,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 单个协议的手风琴一行：表头可点，展开态下渲染 [content]（对应协议表单）。 */
@Composable
private fun ColumnScope.ProviderAccordionItem(
    descriptor: ModelProviderDescriptor,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    AppSettingRow(
        label = descriptor.displayName,
        supporting = if (descriptor.requiresConfig) "需配置端点与凭据" else "内置演示模型",
        onClick = onToggle,
    )
    AnimatedVisibility(visible = expanded) {
        Column(modifier = Modifier.padding(start = Dimens.spaceS, end = Dimens.spaceS)) {
            content()
        }
    }
}

/** 某协议展开后的配置表单（Base URL / API Key / Max Tokens）。 */
@Composable
private fun ProviderConfigFields(
    baseUrl: String,
    apiKey: String,
    maxTokens: Int,
    providerId: String,
    onBaseUrl: (String) -> Unit,
    onApiKey: (String) -> Unit,
    onMaxTokens: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceM)) {
        AppText("端点与凭据", style = AppTextStyle.Body)
        AppTextField(
            label = "Base URL",
            value = baseUrl,
            onValueChange = onBaseUrl,
            placeholder = providerBaseUrlPlaceholder(providerId),
            helperText = providerBaseUrlHint(providerId),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
        )
        AppTextField(
            label = "API Key",
            value = apiKey,
            onValueChange = onApiKey,
            placeholder = providerKeyPlaceholder(providerId),
            helperText = "明文存储，加密存储排期 M2",
        )
        AppTextField(
            label = "Max Tokens",
            value = maxTokens.toString(),
            onValueChange = { raw -> onMaxTokens(raw.toIntOrNull() ?: 0) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            helperText = "单次生成最大 token 数，缺省按协议默认",
        )
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