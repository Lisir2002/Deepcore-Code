package com.deepcode.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.deepcode.designsystem.behavior.appSpring
import com.deepcode.designsystem.theme.Dimens
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 左滑露出操作区（§列表项）：整卡左滑 [revealFraction]（默认半卡）后，露出右侧操作区。
 *
 * 结构：底层 = 整卡高度的操作区（右半侧、surfaceVariant 底、圆角对齐卡片）；
 * 前景 = 内容整卡，随拖动左移 `revealFraction × 宽`。松手时已过半自动吸附展开，
 * 未过半回弹收起；拖动中再次拖动会先停掉上一次动画，避免互相抢值。
 * 点击 / 手势由前景内容自持（如 AppCard 的 onClick），与横向拖动互不干扰。
 *
 * 业务层禁止自拼该交互，统一走本组件（lint 拦截 M3 原生手写方案）。
 */
@Composable
fun AppSwipeReveal(
    modifier: Modifier = Modifier,
    revealFraction: Float = 0.5f,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val revealWidth = maxWidth * revealFraction
        val density = LocalDensity.current
        val revealWidthPx = with(density) { revealWidth.toPx() }
        val scope = rememberCoroutineScope()
        val offset = remember { Animatable(0f) } // 0 = 收起，-revealWidthPx = 展开
        val snapSpring = appSpring()

        // 底层操作区：整卡高度、右半侧；圆角与 AppCard 对齐，保证露出时边角一致。
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(Dimens.radiusM))
                .background(containerColor),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Row(
                modifier = Modifier.width(revealWidth),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) { actions() }
        }

        // 前景内容：整卡左滑。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .draggable(
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            offset.snapTo((offset.value + delta).coerceIn(-revealWidthPx, 0f))
                        }
                    },
                    orientation = Orientation.Horizontal,
                    onDragStarted = { offset.stop() },
                    onDragStopped = {
                        offset.animateTo(
                            targetValue = if (offset.value < -revealWidthPx / 2f) -revealWidthPx else 0f,
                            animationSpec = snapSpring,
                        )
                    },
                )
        ) { content() }
    }
}
