package com.deepcode.designsystem.components.overlay

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deepcode.designsystem.theme.Dimens
import com.deepcode.designsystem.theme.appColors
import com.deepcode.designsystem.theme.appTokens

/**
 * 轻提示——AppBanner（§6.6.3）。页面内顶部常驻横幅，作「持久状态/引导」。
 *
 * 规则：**常驻**直到用户关闭或状态解除，不自动消失；`level: info/success/warning/danger`
 * 四色 + 40dp 图标位。形态：圆角 `radiusM`、level 容器色浅底 + level 色图标/按钮、左图标 24dp。
 *
 * 层级规则（§6.6 金字塔）：引导/持久 → banner；回执 → AppToast；校验 → inline；决策 → AppDialog。
 */
enum class AppBannerLevel { Info, Success, Warning, Danger }

data class AppBannerData(
    val title: String,
    val level: AppBannerLevel = AppBannerLevel.Info,
    val message: String? = null,
    val icon: ImageVector? = null,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

/** Banner 宿主状态：常驻直到 `clear()` 或用户关闭。 */
class BannerHostState {
    private val _current = mutableStateOf<AppBannerData?>(null)

    /** 当前横幅（组件内部读取）。 */
    val current: State<AppBannerData?> = _current

    fun show(data: AppBannerData) { _current.value = data }
    fun clear() { _current.value = null }
}

/** Banner 渲染位（内容区顶部骨架槽位，BannerHost 统一管理）。 */
@Composable
fun BannerHost(
    state: BannerHostState,
    modifier: Modifier = Modifier,
) {
    val data = state.current.value ?: return
    AppBanner(
        data = data,
        onDismiss = { state.clear() },
        modifier = modifier,
    )
}

/** 单条横幅展示。水平撑满、圆角 radiusM、level 容器色浅底。 */
@Composable
fun AppBanner(
    data: AppBannerData,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = appColors()
    val (containerColor, levelColor) = when (data.level) {
        AppBannerLevel.Info -> colors.primaryContainer to colors.info
        AppBannerLevel.Success -> colors.successContainer to colors.success
        AppBannerLevel.Warning -> colors.warningContainer to colors.warning
        AppBannerLevel.Danger -> colors.dangerContainer to colors.danger
    }
    val icon = data.icon ?: when (data.level) {
        AppBannerLevel.Info -> Icons.Filled.Info
        AppBannerLevel.Success -> Icons.Filled.CheckCircle
        AppBannerLevel.Warning -> Icons.Filled.Warning
        AppBannerLevel.Danger -> Icons.Filled.Error
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(appTokens().radius.card),
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.spaceM, vertical = Dimens.spaceM),
            verticalAlignment = Alignment.Top,
        ) {
            // 左图标位：24dp，level 色
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = levelColor,
                modifier = Modifier.size(Dimens.iconL),
            )
            Spacer(Modifier.width(Dimens.spaceS))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.spaceXS),
            ) {
                Text(
                    text = data.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (data.message != null) {
                    Text(
                        text = data.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (data.actionLabel != null && data.onAction != null) {
                    TextButton(
                        onClick = data.onAction,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) {
                        Text(
                            text = data.actionLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = levelColor,
                        )
                    }
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(Dimens.minTouchTarget)) {
                Icon(
                    imageVector = Icons.Filled.Cancel,
                    contentDescription = "关闭",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Dimens.iconS),
                )
            }
        }
    }
}