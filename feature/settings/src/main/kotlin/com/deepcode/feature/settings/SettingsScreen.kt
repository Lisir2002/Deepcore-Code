package com.deepcode.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.deepcode.designsystem.components.AppSwitch
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
import com.deepcode.designsystem.theme.validator.ThemePackLoader
import kotlinx.coroutines.launch
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
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var trusted by remember { mutableStateOf(false) }

    val servers by viewModel.servers.collectAsStateWithLifecycle()

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
                    horizontalArrangement = Arrangement.End,
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
                                // §7.1 进程内登记 + 立即切换到导入包
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
        title = "MCP 服务器",
        largeTitle = "MCP 服务器",
        onBack = onBack,
        modifier = modifier,
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
            // ── 外观（§8.2 主题切换入口）──
            item(key = "appearance") {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
                        AppText("外观", style = AppTextStyle.Title)
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

            // ── 日志（P5：导出 / 同步 / 权限引导）──
            item(key = "logging") {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
                        AppText("日志", style = AppTextStyle.Title)
                        AppText(
                            "崩溃捕获已常驻；日志实时写私有目录，授权后可双写到 /sdcard/deepcorefile 方便真机调试。",
                            style = AppTextStyle.Caption,
                            tone = AppTextTone.Muted,
                        )
                        AppStatusChip(
                            text = if (viewModel.loggingActions.canAccessRoot) {
                                "根目录授权：已开启"
                            } else {
                                "根目录授权：未开启（仅写私有）"
                            },
                        )
                        Row {
                            if (!viewModel.loggingActions.canAccessRoot) {
                                AppTextButton(
                                    text = "去授权",
                                    onClick = viewModel.loggingActions::openPermissionSettings,
                                )
                                Spacer(Modifier.size(Dimens.spaceS))
                            }
                            AppTextButton(
                                text = "立即同步",
                                enabled = viewModel.loggingActions.canAccessRoot,
                                onClick = viewModel.loggingActions::syncToRoot,
                            )
                            Spacer(Modifier.weight(1f))
                            AppPrimaryButton(
                                text = "导出日志",
                                onClick = {
                                    scope.launch {
                                        runCatching { viewModel.loggingActions.exportLogs() }
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // ── 添加表单 ──
            item(key = "add_form") {
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
                containerColor = com.deepcode.designsystem.theme.appColors().primaryContainer,
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
