package com.deepcode.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.deepcode.core.agent.spi.ModelInfo
import com.deepcode.core.agent.spi.ModelTestResult
import com.deepcode.designsystem.components.AppCard
import com.deepcode.designsystem.components.AppLoadingIndicator
import com.deepcode.designsystem.components.AppPrimaryButton
import com.deepcode.designsystem.components.AppSecondaryButton
import com.deepcode.designsystem.components.AppStatusChip
import com.deepcode.designsystem.components.AppText
import com.deepcode.designsystem.components.AppTextButton
import com.deepcode.designsystem.components.AppTextField
import com.deepcode.designsystem.components.form.AppCheckboxRow
import com.deepcode.designsystem.components.form.AppSearchField
import com.deepcode.designsystem.components.scaffold.FormScaffold
import com.deepcode.designsystem.theme.AppTextStyle
import com.deepcode.designsystem.theme.AppTextTone
import com.deepcode.designsystem.theme.Dimens
import com.deepcode.designsystem.theme.appColors
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * 添加供应商流程 · Step2 模型页（决策 P1 / 优化：借鉴 deepcode-R 模型页）。
 *
 * - 「一键拉取」：按 Step1 暂存的端点草稿拉取官方模型目录（含 failover 兜底）。
 * - 搜索筛选 + 批量勾选：可一次选多个模型保存进同一个供应商。
 * - 手输兜底：可手动输入模型 ID 加入，重复添加被拦截提示。
 * - 逐模型「测试」：对每个模型发一条最小请求验证连通性，行下显示耗时与结果。
 * - 「完成」把勾选的模型列表整体保存为**一个**供应商并激活。
 */
@Composable
fun ModelPickScreen(
    onBack: (() -> Unit)?,
    onDone: (() -> Unit)? = null,
) {
    val viewModel: ProviderEditViewModel = koinViewModel()
    val scope = rememberCoroutineScope()
    val colors = appColors()

    var query by remember { mutableStateOf("") }
    var fetching by remember { mutableStateOf(false) }
    var candidates by remember { mutableStateOf<List<ModelInfo>>(emptyList()) }
    var fetchError by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var manualInput by remember { mutableStateOf("") }
    var manualError by remember { mutableStateOf<String?>(null) }
    var testingId by remember { mutableStateOf<String?>(null) }
    var testResults by remember { mutableStateOf<Map<String, ModelTestResult>>(emptyMap()) }

    val providerDisplay = viewModel.descriptors.firstOrNull { it.id == viewModel.draft?.providerId }?.displayName
        ?: viewModel.draft?.providerId
        ?: ""

    fun fetch() {
        scope.launch {
            fetching = true
            fetchError = null
            viewModel.fetchModels()
                .onSuccess { fetched -> candidates = fetched }
                .onFailure { e ->
                    fetchError = e.message ?: "拉取失败"
                    candidates = emptyList()
                }
            fetching = false
        }
    }

    fun addManual() {
        val id = manualInput.trim()
        if (id.isEmpty()) return
        if (selected.contains(id) || candidates.any { it.id == id }) {
            manualError = "「$id」已在列表中，无需重复添加"
            return
        }
        selected = selected + id
        manualInput = ""
        manualError = null
    }

    fun test(id: String) {
        scope.launch {
            testingId = id
            val result = viewModel.testModel(id)
                .getOrElse { e -> ModelTestResult(success = false, latencyMs = 0, message = e.message ?: "测试失败") }
            testResults = testResults + (id to result)
            testingId = null
        }
    }

    // 展示列表 = 拉取目录 ∪ 手动已选（未在目录中的），并按搜索词过滤、排序。
    val displayModels = remember(candidates, selected, query) {
        val extras = selected
            .filter { id -> candidates.none { it.id == id } }
            .map { id -> ModelInfo(id = id, displayName = id, contextWindowTokens = 0, maxOutputTokens = 0) }
        (candidates + extras)
            .filter { it.id.contains(query.trim(), ignoreCase = true) }
            .sortedBy { it.id }
    }

    FormScaffold(
        title = "选择模型",
        onBack = onBack,
        confirm = {
            AppPrimaryButton(
                text = "完成（已选 ${selected.size}）",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.spaceL, vertical = Dimens.spaceM),
                enabled = selected.isNotEmpty(),
                onClick = {
                    viewModel.commitModels(selected.toList())
                    onDone?.invoke()
                },
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = Dimens.spaceM),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceM),
        ) {
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceM)) {
                    AppText(
                        if (providerDisplay.isNotBlank()) "协议：$providerDisplay" else "尚未配置端点",
                        style = AppTextStyle.Caption,
                        tone = AppTextTone.Muted,
                    )
                    AppSearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "搜索模型 ID",
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
                    fetchError?.let {
                        AppText(it, style = AppTextStyle.Caption, tone = AppTextTone.Error)
                    }
                    if (selected.isNotEmpty()) {
                        AppText(
                            "已选（${selected.size}）：${selected.sorted().joinToString(" / ")}",
                            style = AppTextStyle.Caption,
                            tone = AppTextTone.Muted,
                        )
                    }
                }
            }

            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceM)) {
                    AppText("批量勾选下面模型，或手输添加", style = AppTextStyle.Body)
                    if (displayModels.isEmpty()) {
                        AppText(
                            "暂无模型，请先「一键拉取」或手输添加",
                            style = AppTextStyle.Caption,
                            tone = AppTextTone.Muted,
                        )
                    }
                    displayModels.forEach { m ->
                        ModelPickRow(
                            id = m.id,
                            checked = selected.contains(m.id),
                            testing = testingId == m.id,
                            result = testResults[m.id],
                            onToggle = { checked ->
                                selected = if (checked) selected + m.id else selected - m.id
                            },
                            onTest = { test(m.id) },
                            testEnabled = testingId == null && viewModel.draft != null,
                        )
                    }
                }
            }

            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceM)) {
                    AppText("手动添加模型", style = AppTextStyle.Body)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceM),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppTextField(
                            label = "模型 ID",
                            value = manualInput,
                            onValueChange = { manualInput = it },
                            placeholder = "如 gemini-2.0-flash",
                            modifier = Modifier.weight(1f),
                            isError = manualError != null,
                            helperText = manualError,
                        )
                        AppPrimaryButton(text = "添加", onClick = ::addManual)
                    }
                }
            }
        }
    }
}

/** 单个可勾选模型行：勾选加入/移出已选，右侧提供「测试」连通性按钮与结果。 */
@Composable
private fun ModelPickRow(
    id: String,
    checked: Boolean,
    testing: Boolean,
    result: ModelTestResult?,
    onToggle: (Boolean) -> Unit,
    onTest: () -> Unit,
    testEnabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceXXS)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppCheckboxRow(
                label = id,
                checked = checked,
                onCheckedChange = onToggle,
                modifier = Modifier.weight(1f),
            )
            AppTextButton(
                text = if (testing) "测试中…" else "测试",
                onClick = onTest,
                enabled = testEnabled,
            )
        }
        result?.let { r ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = Dimens.spaceL),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppStatusChip(
                    text = if (r.success) "连通 · ${r.latencyMs}ms" else r.message,
                    containerColor = if (r.success) appColors().successContainer else appColors().surfaceElevated,
                )
            }
        }
    }
}