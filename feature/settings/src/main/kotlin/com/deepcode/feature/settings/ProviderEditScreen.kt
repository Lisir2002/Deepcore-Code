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
 * 设置三级页 · Provider 编辑（决策 D5/D7）。
 *
 * - 顶部 Provider 单选（来自注册表 [ProviderRegistry]，决策 D1），
 *   选中 `requiresConfig` 的 Provider 时展开动态字段表单（当前：OpenAI 兼容四字段）。
 * - 模型字段 = 手输兜底（决策 D6 的下拉拉取 listModels() 留到接入真实端点后增强）。
 * - 保存按钮：表单字段齐全才可点；教练 Provider 保存后回 [FormScaffold] popBack。
 */
@Composable
fun ProviderEditScreen(
    onBack: (() -> Unit)?,
    onSaved: (() -> Unit)? = null,
) {
    val viewModel: ProviderEditViewModel = koinViewModel()

    var selectedProviderId by remember {
        mutableStateOf(viewModel.initialProviderId().takeIf { it.isNotBlank() } ?: ModelProviderIds.DEMO)
    }

    val openAi = remember { viewModel.initialConfig() }
    var baseUrl by remember { mutableStateOf(openAi.baseUrl) }
    var apiKey by remember { mutableStateOf(openAi.apiKey) }
    var model by remember { mutableStateOf(openAi.model) }
    var maxTokens by remember { mutableIntStateOf(openAi.maxTokens) }

    val selected = viewModel.descriptors.firstOrNull { it.id == selectedProviderId }
    val needsConfig = selected?.requiresConfig == true
    val fieldsReady = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

    fun onSave() {
        if (needsConfig) {
            viewModel.saveOpenAi(baseUrl = baseUrl, apiKey = apiKey, model = model, maxTokens = maxTokens)
        } else {
            viewModel.selectDemo()
        }
        onSaved?.invoke()
    }

    FormScaffold(
        title = "配置 Provider",
        onBack = onBack,
        confirm = {
            AppPrimaryButton(
                text = if (needsConfig) "保存" else "使用演示模型",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.spaceL, vertical = Dimens.spaceM),
                enabled = !needsConfig || fieldsReady,
                onClick = ::onSave,
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
                            placeholder = "https://api.deepseek.com/v1",
                            helperText = "OpenAI 兼容端点，需可直达 /chat/completions",
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                        )
                        AppTextField(
                            label = "API Key",
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            placeholder = "sk-…",
                            helperText = "明文存储，加密存储排期 M2",
                        )
                        AppTextField(
                            label = "模型",
                            value = model,
                            onValueChange = { model = it },
                            placeholder = "deepseek-chat",
                            helperText = "手输模型 ID（接入真实端点后支持从端点下拉拉取）",
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                        )
                        AppTextField(
                            label = "Max Tokens",
                            value = maxTokens.toString(),
                            onValueChange = { raw ->
                                maxTokens = raw.toIntOrNull() ?: 0
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            helperText = "单次生成最大 token 数，缺省 8192",
                        )
                    }
                }
            }
        }
    }
}

/** Provider 单选组：无表单（如 Demo）与需配置（如 OpenAI 兼容）并行展示。 */
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