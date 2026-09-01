package com.deepcode.designsystem.components.messaging

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * 活光标（§6.8.5 / §6.8.8）：2dp×18dp 竖条 `primary` 色，脉冲 800ms。
 *
 * 语义：**流结束才移除**（非最后 token）——AI 还在吐 token 时它贴在最后，
 * 流结束（`streaming == false`）即消失。`active=false` 时渲染为空（返回无布局）。
 */
@Composable
fun StreamEmittedCursor(
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!active) return
    val transition = rememberInfiniteTransition(label = "streamCursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 800), RepeatMode.Reverse),
        label = "cursorPulse",
    )
    Box(
        modifier = modifier
            .width(CursorSpec.Width.dp)
            .height(CursorSpec.Height.dp)
            .graphicsLayer { this.alpha = alpha }
            .background(MaterialTheme.colorScheme.primary),
    )
}

private object CursorSpec {
    const val Width = 2
    const val Height = 18
}