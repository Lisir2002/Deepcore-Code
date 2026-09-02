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

/**
 * 设置二级页 · 模型。
 *
 * 独立承载「真实模型」配置（从原「模型与连接」页拆出）：端点 / API Key /
 * 流式开关。本轮只搭骨架——持久化（EncryptedSharedPreferences）与 OpenAI 兼容
 * Provider 接线排期实现；当前 :app DI 仍绑定 DemoProvider。
 */
@Composable
fun SettingsModelScreen(
    onBack: (() -> Unit)?,
    onOpenProvider: (() -> Unit)? = null,
) {
    DetailScaffold(
        title = "模型",
        largeTitle = "模型",
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
            item(key = "model_provider") {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceXXS)) {
                        AppText("真实模型", style = AppTextStyle.Body)
                        AppText(
                            "即将支持配置模型端点、API Key 与流式开关。骨架已就位，持久化与 OpenAI 兼容 Provider 接线排期实现。",
                            style = AppTextStyle.Caption,
                            tone = AppTextTone.Muted,
                        )
                        AppStatusChip(text = "默认 DemoProvider（未接入真实模型）")
                        AppSettingRow(
                            label = "配置 Provider",
                            supporting = "端点 / API Key / 流式开关",
                            onClick = onOpenProvider,
                        )
                    }
                }
            }
        }
    }
}