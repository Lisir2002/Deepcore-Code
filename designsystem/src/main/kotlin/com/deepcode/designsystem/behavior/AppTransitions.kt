package com.deepcode.designsystem.behavior

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import com.deepcode.designsystem.theme.appTokens
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * 动效编排表（§5.2）：**转场模式 × 档位绑定的唯一接线处**。
 *
 * 原则（§5.1）：模式（怎么动）与参数（多快、什么曲线）分离——参数只引用 §3.5 档位，
 * 任何页面不许自造第五种转场模式；选中一个模式就获得 [AppTransitionSpec]（时长+曲线），
 * 由 [MotionResolver] 过一遍（reduced-motion 时直切）。
 *
 * 常量对象：改一处全局生效；Navigation graph 与骨架组件全部引用这里。
 */
object AppTransitions {

    /** 转场模式枚举（§5.2 表）。新增模式 = 扩展此枚举 + 在 [spec] 登记 + 评审。 */
    enum class Mode {
        PagePush, PagePop, ModalSheet, DialogShow, MenuShow,
        ToastShow, TabSwitch, ListInsert, ListRemove, ThoughtExpand, StateSwap,
    }

    /** 转场规格：时长 + 缓动，全部经 MotionResolver。 */
    data class AppTransitionSpec(
        val duration: Duration,
        val easing: Easing,
    )

    /** §5.2 绑定表唯一实现（@Composable：档位来自当前主题 Board）。 */
    @Composable
    fun spec(mode: Mode): AppTransitionSpec {
        val motion = appTokens().motion
        return when (mode) {
            Mode.PagePush -> AppTransitionSpec(motion.slow, motion.easingEmphasized)
            Mode.PagePop -> AppTransitionSpec(motion.slow, motion.easingEmphasized)
            Mode.ModalSheet -> AppTransitionSpec(motion.normal, motion.easingEmphasized)
            Mode.DialogShow -> AppTransitionSpec(motion.fast, motion.easingStandard)
            Mode.MenuShow -> AppTransitionSpec(motion.fast, motion.easingStandard)
            Mode.ToastShow -> AppTransitionSpec(motion.fast, motion.easingStandard)
            Mode.TabSwitch -> AppTransitionSpec(motion.fast, motion.easingStandard)
            Mode.ListInsert -> AppTransitionSpec(motion.normal, motion.easingStandard)
            Mode.ListRemove -> AppTransitionSpec(motion.normal, motion.easingStandard)
            Mode.ThoughtExpand -> AppTransitionSpec(motion.normal, motion.easingStandard)
            Mode.StateSwap -> AppTransitionSpec(motion.fast, motion.easingStandard)
        }
    }

    /** 顶栏 tab 指示器（fast/emphasized，§5.2 末段），独立于通用表。 */
    val TabIndicator = AppTransitionSpec(
        duration = 140.milliseconds,
        easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f),
    )
}

/** spring 键值（§3.5 spring 双轨；@Composable 因 damp 从主题取）。 */
@Composable
fun appSpring(): androidx.compose.animation.core.SpringSpec<Float> =
    spring<Float>(dampingRatio = appTokens().motion.easingEmphasized.let { 0.72f }, stiffness = 260f)