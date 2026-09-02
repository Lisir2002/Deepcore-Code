package com.deepcode.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.deepcode.designsystem.components.AppCard
import com.deepcode.designsystem.components.AppPrimaryButton
import com.deepcode.designsystem.components.AppStatusChip
import com.deepcode.designsystem.components.AppText
import com.deepcode.designsystem.components.AppTextButton
import com.deepcode.designsystem.components.scaffold.DetailScaffold
import com.deepcode.designsystem.theme.AppTextStyle
import com.deepcode.designsystem.theme.AppTextTone
import com.deepcode.designsystem.theme.Dimens
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * 设置二级页 · 日志。
 *
 * 原设置页「日志」区块独立成页（P5：导出 / 同步 / 权限引导）。契约仍由
 * [LoggingActions] 提供，具体实现（LogExporter + StoragePermission + CrashVault）
 * 由 :app 装配层注入，页面不感知。
 */
@Composable
fun SettingsLoggingScreen(
    onBack: (() -> Unit)?,
) {
    val viewModel: SettingsViewModel = koinViewModel()
    val scope = rememberCoroutineScope()

    DetailScaffold(
        title = "日志",
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
            item(key = "logging") {
                AppCard {
                    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.spaceS)) {
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
        }
    }
}