package com.deepcode.designsystem.components.scaffold

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.deepcode.designsystem.behavior.appStateLayer
import com.deepcode.designsystem.behavior.rememberNoInkIndication
import com.deepcode.designsystem.theme.Dimens
import com.deepcode.designsystem.theme.appColors

/** §6.2 顶栏选项卡：与内容区 crossfade（TabSwitch）解耦，指示器滑动由页面按需接。 */
@Composable
fun AppTopTabs(
    tabs: List<TabItem>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceXS),
    ) {
        tabs.forEachIndexed { index, tab ->
            val interaction = remember { MutableInteractionSource() }
            TabChip(
                text = tab.text,
                icon = tab.icon,
                badge = tab.badge,
                selected = index == selectedIndex,
                onClick = { onSelected(index) },
                modifier = Modifier.weight(1f),
                interactionSource = interaction,
            )
        }
    }
}

@Composable
private fun TabChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    badge: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource,
) {
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = modifier
            .appStateLayer(interactionSource, selected = selected, shape = RoundedCornerShape(Dimens.radiusS))
            .height(36.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = rememberNoInkIndication(),
                onClick = onClick,
            ),
        shape = RoundedCornerShape(Dimens.radiusS),
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.spaceM, vertical = Dimens.spaceXS),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(Dimens.iconS))
                Spacer(Modifier.width(Dimens.spaceXXS))
            }
            Text(text = text, style = MaterialTheme.typography.labelLarge)
            if (badge != null) {
                Spacer(Modifier.width(Dimens.spaceXXS))
                BadgeDot(badge)
            }
        }
    }
}

@Composable
private fun BadgeDot(count: Int) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError,
        modifier = Modifier.size(18.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = if (count > 99) "99+" else count.toString(),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** §6.3 底栏导航：3–5 项均布全宽，图标 24dp；选中项主色 12% 圆角底 + 主色图标/标签（颜色/缩放平滑过渡）。 */
@Composable
fun AppNavBar(
    tabs: List<NavItem>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = appColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spaceS, vertical = Dimens.spaceS),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, item ->
            val interaction = remember { MutableInteractionSource() }
            val selected = index == selectedIndex
            val contentColor by animateColorAsState(
                targetValue = if (selected) colors.primary else colors.textTertiary,
                label = "navBarItemColor",
            )
            val iconScale by animateFloatAsState(
                targetValue = if (selected) 1.15f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "navBarIconScale",
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .appStateLayer(
                        interaction,
                        selected = selected,
                        overlayColor = colors.primary,
                        shape = RoundedCornerShape(Dimens.radiusL),
                    )
                    .height(Dimens.minTouchTarget)
                    .clickable(
                        interactionSource = interaction,
                        indication = rememberNoInkIndication(),
                        onClick = { onSelected(index) },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.text,
                        modifier = Modifier.size(Dimens.iconL).scale(iconScale),
                        tint = contentColor,
                    )
                    item.badge?.let {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 8.dp, y = (-6).dp),
                        ) {
                            BadgeDot(it)
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                )
            }
        }
    }
}