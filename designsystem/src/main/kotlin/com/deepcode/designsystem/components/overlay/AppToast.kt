package com.deepcode.designsystem.components.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deepcode.designsystem.theme.Dimens
import com.deepcode.designsystem.theme.appColors
import com.deepcode.designsystem.theme.appTokens
import kotlinx.coroutines.delay

/**
 * 轻提示——AppToast（§6.6.3）。底部悬浮短提示，作「操作结果回执」。
 *
 * 规则：
 * - 4s 自动消失（M3 LENGTH_SHORT 官方值）；带「撤销」类动作钮延长至 6s；
 * - 同屏最多 1 条（新 toast 直接顶替旧条）；
 * - 支持横滑关闭（swipe-to-dismiss）；
 * - `level: neutral/success/danger` 三色。形态：圆角 `radiusM`、反相深底（inverseSurface）、
 *   高 ≥44dp、外边距 8dp、海拔 6dp、多行截断 2 行。
 *
 * **禁平台 Android Toast / Compose Snackbar**（lint ForbiddenPlatformToast）。
 */
enum class AppToastLevel { Neutral, Success, Danger }

data class AppToastData(
    val message: String,
    val level: AppToastLevel = AppToastLevel.Neutral,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
) {
    /** 带动作钮 6s，否则 4s（§6.6.3 对齐 M3 LENGTH_SHORT）。 */
    val durationMillis: Long
        get() = if (actionLabel != null) 6000L else 4000L
}

/** 单条展示引用：id 用于「新顶替旧」时重启计时。 */
internal data class ToastShowRef(
    val id: Long,
    val data: AppToastData,
)

/** Toast 宿主状态：同屏最多 1 条，`show()` 即顶替旧条。由骨架槽位持有。 */
class ToastHostState {
    private val _current = mutableStateOf<ToastShowRef?>(null)
    private var nextId: Long = 0L

    /** 当前展示条（组件内部读取）。 */
    val current: State<ToastShowRef?> = _current

    /** 弹出一条回执；已有条会直接顶替。 */
    fun show(data: AppToastData) {
        _current.value = ToastShowRef(nextId++, data)
    }

    /** 仅当 id 仍是当前条时才清除（防止旧计时顶掉新条）。 */
    internal fun clear(id: Long) {
        if (_current.value?.id == id) _current.value = null
    }
}

/**
 * Toast 渲染位：NavScaffold 内置在底栏上方 inset（业务零感知）。
 * 放一个 [BoxWithConstraints]/顶层 Box 内、`Alignment.BottomCenter` 即可。
 */
@Composable
fun ToastHost(
    state: ToastHostState,
    modifier: Modifier = Modifier,
) {
    val ref = state.current.value
    val colors = appColors()
    val fast = appTokens().motion.fast.inWholeMilliseconds.toInt()

    // 顶替语义：退场期间保留最后一条以便 fade-out，不闪空白。
    var lastRef by remember { mutableStateOf(ref) }
    lastRef = ref ?: lastRef
    val show = lastRef

    // 新条目顶替旧条：keyed on id 重启自动消失计时
    LaunchedEffect(ref?.id) {
        val current = ref ?: return@LaunchedEffect
        delay(current.data.durationMillis)
        if (state.current.value?.id == current.id) {
            state.clear(current.id)
        }
    }

    AnimatedVisibility(
        visible = ref != null,
        modifier = modifier.fillMaxWidth(),
        enter = toastEnter(fast),
        exit = toastExit(fast),
    ) {
        if (show == null) return@AnimatedVisibility
        var offsetX by androidx.compose.runtime.remember { mutableStateOf(0f) }
        var dismissed by androidx.compose.runtime.remember { mutableStateOf(false) }
        val alpha by animateFloatAsState(if (dismissed) 0f else 1f)

        Surface(
            modifier = Modifier
                .pointerInput(show.id) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, dragAmount -> offsetX += dragAmount },
                        onDragEnd = {
                            if (offsetX < -120f) {
                                dismissed = true
                                state.clear(show.id)
                            } else offsetX = 0f
                        },
                        onDragCancel = { offsetX = 0f },
                    )
                }
                .graphicsLayer { translationX = offsetX; this.alpha = alpha },
            shape = RoundedCornerShape(appTokens().radius.card),
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .padding(horizontal = Dimens.spaceM, vertical = Dimens.spaceS),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val (levelIcon, levelTint) = when (show.data.level) {
                    AppToastLevel.Success -> Icons.Filled.CheckCircle to colors.success
                    AppToastLevel.Danger -> Icons.Filled.Error to colors.danger
                    AppToastLevel.Neutral -> null to null
                }
                if (levelIcon != null && levelTint != null) {
                    Icon(
                        imageVector = levelIcon,
                        contentDescription = null,
                        tint = levelTint,
                        modifier = Modifier.size(Dimens.iconM),
                    )
                    Spacer(Modifier.width(Dimens.spaceS))
                }
                Text(
                    text = show.data.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (show.data.actionLabel != null && show.data.onAction != null) {
                    Spacer(Modifier.width(Dimens.spaceM))
                    TextButton(
                        onClick = {
                            state.clear(show.id)
                            show.data.onAction()
                        },
                    ) {
                        Text(
                            text = show.data.actionLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.inversePrimary,
                        )
                    }
                }
            }
        }
    }
}

private fun toastEnter(durationMillis: Int): EnterTransition {
    val spec = tween<Int>(durationMillis)
    val fspec = tween<Float>(durationMillis)
    return fadeIn(animationSpec = fspec) + slideInVertically(animationSpec = spec) { it / 2 }
}

private fun toastExit(durationMillis: Int): ExitTransition {
    val spec = tween<Int>(durationMillis)
    val fspec = tween<Float>(durationMillis)
    return fadeOut(animationSpec = fspec) + slideOutVertically(animationSpec = spec) { it / 2 }
}