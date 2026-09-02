package com.deepcode.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.deepcode.core.agent.spi.ModelInfo
import com.deepcode.designsystem.components.AppCard
import com.deepcode.designsystem.components.AppLoadingIndicator
import com.deepcode.designsystem.components.AppPrimaryButton
import com.deepcode.designsystem.components.AppSecondaryButton
import com.deepcode.designsystem.components.AppText
import com.deepcode.designsystem.components.AppTextField
import com.deepcode.designsystem.components.scaffold.FormScaffold
import com.deepcode.designsystem.theme.AppTextStyle
import com.deepcode.designsystem.theme.AppTextTone
import com.deepcode.designsystem.theme.Dimens
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * 添加供应商流程 · Step2 模型页（决策 P1 / 优化一）。
 *
 * - 支持「一键拉取」：按 Step1 暂存的端点草稿构造临时 Provider，调用其 `listModels()`
 *   拉取官方模型目录；失败给出提示，仍可手输兜底。
 * - 支持手输：点拉取结果任一项回填输入框，也可直接输入模型 ID。
 * - 「完成」按协议分派遣 [ProviderEditViewModel.commitModel] 持久化，标记激活后回退。
 */
@Composable
fun ModelPickScreen(
    onBack: (() -> Unit)?,
    onDone: (() -> Unit)? = null,
) {
    val viewModel: ProviderEditViewModel = koinViewModel()
    val scope = rememberCoroutineScope()

    var model by remember { mutableStateOf("") }
    var fetching by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf<List<ModelInfo>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    val providerDisplay = viewModel.descriptors.firstOrNull { it.id == viewModel.draft?.providerId }?.displayName
        ?: viewModel.draft?.providerId
        ?: ""

    fun fetch() {
        scope.launch {
            fetching = true
            error = null
            viewModel.fetchModels()
                .onSuccess { fetched -> models = fetched }
                .onFailure { e ->
                    error = e.message ?: "拉取失败"
                    models = emptyList()
                }
            fetching = false
        }
    }

    FormScaffold(
        title = "选择模型",
        onBack = onBack,
        confirm = {
            AppPrimaryButton(
                text = "完成",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.spaceL, vertical = Dimens.spaceM),
                enabled = model.isNotBlank(),
                onClick = {
                    viewModel.commitModel(model)
                    onDone?.invoke()
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
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceM)) {
                    AppText(
                        if (providerDisplay.isNotBlank()) "协议：$providerDisplay" else "尚未配置端点",
                        style = AppTextStyle.Caption,
                        tone = AppTextTone.Muted,
                    )
                    AppTextField(
                        label = "模型 ID",
                        value = model,
                        onValueChange = { model = it },
                        placeholder = "如 gemini-2.0-flash / claude-sonnet …",
                        helperText = "可手输模型 ID，也可一键拉取后从下方点选",
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceM),
                    ) {
                        AppSecondaryButton(
                            text = if (fetching) "拉取中…" else "一键拉取模型",
                            onClick = ::fetch,
                            enabled = !fetching && viewModel.draft != null,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (fetching) {
                        AppLoadingIndicator(message = "正在拉取官方模型目录…")
                    }
                    error?.let {
                        AppText(
                            it,
                            style = AppTextStyle.Caption,
                            tone = AppTextTone.Error,
                        )
                    }
                    if (!fetching && models.isNotEmpty()) {
                        AppText(
                            "共 ${models.size} 个模型，点选填入：",
                            style = AppTextStyle.Caption,
                            tone = AppTextTone.Muted,
                        )
                        models.take(50).forEach { info ->
                            AppText(
                                text = info.displayName.ifBlank { info.id },
                                style = AppTextStyle.Body,
                                tone = if (info.id == model) AppTextTone.Primary else AppTextTone.Default,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { model = info.id }
                                    .padding(vertical = Dimens.spaceXXS),
                            )
                        }
                        if (models.size > 50) {
                            AppText(
                                "（其余 ${models.size - 50} 个模型略，可在对话框搜索上方 ID）",
                                style = AppTextStyle.Caption,
                                tone = AppTextTone.Muted,
                            )
                        }
                    }
                }
            }
        }
    }
}