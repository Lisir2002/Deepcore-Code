package com.deepcode.designsystem.behavior

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.currentAnimatorDurationScale
import kotlin.time.Duration

/**
 * 动效统一出口（§10.3）：所有转场模式与 pressed/dragged 缩放都过这里，
 * 系统 `ANIMATOR_DURATION_SCALE == 0`（reduce-motion）时一键直切。
 *
 *  - [resolve]：把 [AppTransitions.AppTransitionSpec] 解析为有效规格（reduce 时 duration→0）；
 *  - 组件侧用它取有效时长构造动画，而不是直接读令牌档位。
 */
object MotionResolver {

    /** 有效动效规格：reduce-motion 下 duration 归零（直切，曲线无关紧要）。 */
    data class Resolved(
        val duration: Duration,
        val easing: Easing,
    )

    /** 读系统动画缩放：==0 即用户开启"移除动画"（§10.3）。 */
    @Composable
    fun isReducedMotion(): Boolean = LocalView.current.currentAnimatorDurationScale == 0f

    @Composable
    fun resolve(mode: AppTransitions.Mode): Resolved {
        val base = AppTransitions.spec(mode)
        return if (isReducedMotion()) Resolved(Duration.ZERO, base.easing)
        else Resolved(base.duration, base.easing)
    }

    /** 把解析结果转成组件可用的 tween 规格（Float 类型）。 */
    fun <T> finiteSpec(resolved: Resolved, initial: T? = null): FiniteAnimationSpec<T> {
        @Suppress("UNCHECKED_CAST")
        val duration = resolved.duration.inWholeMilliseconds.toInt()
        return tween<T>(durationMillis = duration, easing = resolved.easing)
    }
}