package com.deepcode.designsystem.behavior

import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.node.DelegatableNode

/**
 * 交互态系统（§4）：全 App 唯一的 State Overlay 模型，弃用 M3 ripple（D12）。
 *
 * 设计契约（4.1 / 4.2）：
 *  - **行为常量只在这一个文件出现一次**，改走评审（非颜色令牌，不进 theme）；
 *  - overlay 色 = 前景(onColor) α 叠加，交互态**不进颜色面板**——任何主题包底色下自动成立；
 *  - [appStateLayer] 一个 modifier 同时产出 overlay 绘制 + 缩放，组件只调一次；
 *  - 屏蔽 ripple：组件在 M3 组件的 `indication` 参数传入 [noInkIndication]。
 *
 * 八态（4.1）取 **default / hovered / pressed / focused / selected / dragged** 六种有视觉的：
 *  hovered 8%、pressed 12%＋scale(0.98)、focused 主色描边、
 *  selected 主色12%底、dragged 抬升 scale(1.02)；disabled/loading 由组件自行处理。
 */

// §4.2 行为常量（唯一出处，改走评审）
const val OVERLAY_HOVER: Float = 0.08f
const val OVERLAY_PRESS: Float = 0.12f
const val OVERLAY_SELECTED: Float = 0.12f
const val ALPHA_DISABLED: Float = 0.38f
const val SCALE_PRESS: Float = 0.98f
const val SCALE_DRAG: Float = 1.02f

/**
 * §4.2 统一入口：交互 overlay + pressed/dragged 缩放，一次封装。
 *
 * @param interactionSource 组件的 InteractionSource（需 remember 稳定）
 * @param shape 组件形状：overlay 按此形状裁剪——圆角组件按压/长按时不会露方角黑边（§4.1）
 * @param overlayColor 默认 null = 取组件前景（onColor）α 叠加；仅特殊组可传具体色
 * @param selected 显式选中态：主色 12% 底
 * @param scalePressed 拖拽组件可关 scalePressed = 1f
 */
@Composable
fun Modifier.appStateLayer(
    interactionSource: InteractionSource,
    shape: Shape = RectangleShape,
    overlayColor: Color? = null,
    selected: Boolean = false,
    scalePressed: Float = SCALE_PRESS,
): Modifier {
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val dragged by interactionSource.collectIsDraggedAsState()

    // overlay 二值叠加表（§4.1）：优先级 pressed > hovered
    val overlay = if (selected) {
        (overlayColor ?: Color.Black).copy(alpha = OVERLAY_SELECTED)
    } else if (pressed) {
        (overlayColor ?: Color.Black).copy(alpha = OVERLAY_PRESS)
    } else if (hovered) {
        (overlayColor ?: Color.Black).copy(alpha = OVERLAY_HOVER)
    } else {
        Color.Unspecified
    }

    val scale = if (pressed || dragged) {
        if (dragged) SCALE_DRAG else scalePressed
    } else 1f

    return this
        .drawBehind {
            if (overlay != Color.Unspecified) {
                // 关键：overlay 按组件形状裁剪，圆角组件按压/长按时不再露方角黑边（§4.1 四角外露修复）
                // 注意：DrawScope 内 `density` 是 Float 标量，Density 对象要用 `this`（DrawScope 实现 Density）。
                val outline = shape.createOutline(size, layoutDirection, this)
                clipPath(Path().apply { addOutline(outline) }) { drawRect(overlay) }
            }
        }
        .scale(scale)
}

/**
 * 供 M3 组件的 `indication` 参数使用：一个不绘制任何墨迹的 [Indication] 占位。
 * overlay/描边全交给 [appStateLayer]，这里仅用于吃语法上必须传的 indication、清掉 ripple。
 */
@Composable
fun rememberNoInkIndication(): Indication = noInkIndication

/** 无墨迹 Indication：视觉交给 appStateLayerModifier 的 overlay。 */
private val noInkIndication: Indication = object : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = NoInkIndicationNode()
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = 7
}

private fun NoInkIndicationNode() = object : androidx.compose.ui.Modifier.Node() {}