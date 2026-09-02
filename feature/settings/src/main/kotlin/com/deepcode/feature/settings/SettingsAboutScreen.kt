package com.deepcode.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.deepcode.designsystem.components.AppCard
import com.deepcode.designsystem.components.AppText
import com.deepcode.designsystem.components.scaffold.DetailScaffold
import com.deepcode.designsystem.theme.AppTextStyle
import com.deepcode.designsystem.theme.AppTextTone
import com.deepcode.designsystem.theme.Dimens

/**
 * 设置二级页 · 关于。
 *
 * 静态信息页：应用名、版本（由 :app 装配层传入，feature:settings 不依赖
 * BuildConfig）、简短描述。后续可挂数据/存储、开源许可等静态入口。
 */
@Composable
fun SettingsAboutScreen(
    onBack: (() -> Unit)?,
    appName: String = "Deepcore AI",
    appVersion: String? = null,
) {
    DetailScaffold(
        title = "关于",
        largeTitle = "关于",
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
            item(key = "app_info") {
                AppCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Dimens.spaceS),
                    ) {
                        AppText(appName, style = AppTextStyle.Title)
                        if (appVersion != null) {
                            AppText(appVersion, style = AppTextStyle.Body, tone = AppTextTone.Muted)
                        }
                        AppText(
                            "事件驱动 Agent 运行时 · 对齐开放标准（MCP × Agent Skills）",
                            style = AppTextStyle.Caption,
                            tone = AppTextTone.Muted,
                        )
                    }
                }
            }
        }
    }
}