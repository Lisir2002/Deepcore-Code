package com.deepcode.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepcode.designsystem.components.AppCard
import com.deepcode.designsystem.components.AppPrimaryButton
import com.deepcode.designsystem.components.AppStatusChip
import com.deepcode.designsystem.components.AppText
import com.deepcode.designsystem.components.AppTextButton
import com.deepcode.designsystem.components.AppTextField
import com.deepcode.designsystem.components.overlay.AppNoticeDialog
import com.deepcode.designsystem.components.scaffold.DetailScaffold
import com.deepcode.designsystem.theme.AppTextStyle
import com.deepcode.designsystem.theme.AppTextTone
import com.deepcode.designsystem.theme.DarkMode
import com.deepcode.designsystem.theme.Dimens
import com.deepcode.designsystem.theme.LocalStyleController
import com.deepcode.designsystem.theme.appColors
import com.deepcode.designsystem.theme.validator.ThemePackLoader
import kotlinx.coroutines.launch

/**
 * 设置二级页 · 外观。
 *
 * 原设置页「外观」区块独立成页（设计定稿：入口列表 + 二级 Detail）。
 * 承载深色模式三态 / 风格包切换 / theme.json 主题包导入。
 */
@Composable
fun SettingsAppearanceScreen(
    onBack: (() -> Unit)?,
) {
    val style = LocalStyleController.current
    val activeSpec by style.spec.collectAsStateWithLifecycle()
    val darkMode by style.darkMode.collectAsStateWithLifecycle()
    val packs by style.packs.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // —— 主题包导入（P4 T8.3）：粘贴 theme.json v1 → loader → registerPack → setSpec ——
    var importOpen by remember { mutableStateOf(false) }
    var importJson by remember { mutableStateOf("") }
    var importReport by remember { mutableStateOf<ThemePackLoader.LoadResult?>(null) }

    if (importOpen) {
        AppNoticeDialog(
            title = "导入主题包",
            onDismiss = { importOpen = false },
            acknowledgeText = null,
            richContent = {
                AppText(
                    "粘贴 theme.json v1 增量包。导入走 Codec→Merger→Validator：结构/值非法会整包拒载，A11y 不合规自动回退品牌值。",
                    style = AppTextStyle.Caption,
                    tone = AppTextTone.Muted,
                )
                AppTextField(
                    label = "theme.json",
                    value = importJson,
                    onValueChange = { importJson = it },
                    singleLine = false,
                    maxLines = 12,
                    placeholder = """{ "id": "midnight", "name": "午夜", "light": { "color": { "primary": "#7C5CFF" } }, "dark": {...} }""",
                )
            },
            actions = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.spaceXL),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                ) {
                    AppTextButton(text = "取消", onClick = { importOpen = false })
                    Spacer(Modifier.size(Dimens.spaceS))
                    AppPrimaryButton(
                        text = "导入",
                        enabled = importJson.isNotBlank(),
                        onClick = {
                            importReport = ThemePackLoader.load(importJson.trim())
                            importOpen = false
                            if (importReport is ThemePackLoader.LoadResult.Success) {
                                val ok = importReport as ThemePackLoader.LoadResult.Success
                                style.registerPack(ok.spec)
                                scope.launch { style.setSpec(ok.spec.id) }
                            }
                        },
                    )
                }
            },
        )
    }

    if (importReport != null) {
        AppNoticeDialog(
            title = "导入结果",
            onDismiss = { importReport = null },
            richContent = {
                ImportResultBody(importReport!!)
            },
        )
    }

    DetailScaffold(
        title = "外观",
        onBack = onBack,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Dimens.spaceL),
            contentPadding = PaddingValues(vertical = Dimens.spaceM),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.spaceM),
        ) {
            item(key = "appearance") {
                AppCard {
                    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.spaceS)) {
                        AppText("深色模式", style = AppTextStyle.Body)
                        DarkMode.entries.forEach { mode ->
                            SelectableRow(
                                label = when (mode) {
                                    DarkMode.FOLLOW_SYSTEM -> "跟随系统"
                                    DarkMode.LIGHT -> "浅色"
                                    DarkMode.DARK -> "深色"
                                },
                                selected = darkMode == mode,
                                onClick = { scope.launch { style.setDarkMode(mode) } },
                            )
                        }
                        AppText("风格", style = AppTextStyle.Body)
                        packs.forEach { pack ->
                            SelectableRow(
                                label = pack.name,
                                selected = activeSpec.id == pack.id,
                                onClick = { scope.launch { style.setSpec(pack.id) } },
                            )
                        }
                        AppTextButton(
                            text = "导入主题包…",
                            onClick = { importOpen = true },
                        )
                    }
                }
            }
        }
    }
}

/** 可点击单行选项：darkMode 三态 / 风格包列表共用，选中态右侧打勾。 */
@Composable
private fun SelectableRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Dimens.spaceXS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(
            label,
            style = AppTextStyle.Body,
            tone = if (selected) AppTextTone.Primary else AppTextTone.Default,
        )
        Spacer(Modifier.weight(1f))
        if (selected) {
            AppStatusChip(
                text = "使用中",
                containerColor = appColors().primaryContainer,
            )
        }
    }
}

/** 导入结果展示：拒载列 errors；成功列 applied / warnings / fallbacks（§7.4 报告）。 */
@Composable
private fun ImportResultBody(result: ThemePackLoader.LoadResult) {
    val items = when (result) {
        is ThemePackLoader.LoadResult.Success -> {
            buildList {
                if (result.report.fallbacks.isEmpty()) {
                    add("✓ 已导入并应用「${result.spec.name}」。")
                } else {
                    add("✓ 已导入，但下列 A11y 硬约束不合规项已回退品牌值：")
                    addAll(result.report.fallbacks.keys.map { "· $it" })
                }
                if (result.warnings.isNotEmpty()) {
                    add("告警：")
                    addAll(result.warnings.map { "· $it" })
                }
                add("已覆盖（${result.applied.size}）：${result.applied.joinToString(", ")}")
            }
        }
        is ThemePackLoader.LoadResult.Rejected -> {
            listOf("✕ 整包拒载：") + result.errors.map { "· $it" }
        }
    }
    items.forEach {
        AppText(it, style = AppTextStyle.Body)
    }
}