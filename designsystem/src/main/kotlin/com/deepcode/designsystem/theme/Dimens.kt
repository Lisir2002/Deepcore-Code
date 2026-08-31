package com.deepcode.designsystem.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 设计令牌：全 App 唯一的尺寸来源。
 *
 * 为什么要有这一层：不禁止硬编码，就一定会出现 13.dp / 15.dp / 17.dp 三种"差不多"的间距。
 * 有了这一层，写 `Dimens.spaceM` 比写 `16.dp` 还短，顺手就统一了。
 * （硬编码行为由 :lint 规则 DirectDesignTokenUsage 拦截）
 */
object Dimens {
    val spaceXXS = 2.dp
    val spaceXS = 4.dp
    val spaceS = 8.dp
    val spaceM = 12.dp
    val spaceL = 16.dp
    val spaceXL = 24.dp
    val spaceXXL = 32.dp

    /** 屏幕两侧统一留白。所有页面用同一个值，列表才不会一页宽一页窄。 */
    val screenPaddingHorizontal = 16.dp
    val screenPaddingVertical = 12.dp

    val radiusS = 8.dp
    val radiusM = 12.dp
    val radiusL = 16.dp
    val radiusXL = 24.dp
    /** 消息气泡的圆角要和页面卡片区分开，靠的就是这个值。 */
    val bubbleRadius = 18.dp

    val iconS = 16.dp
    val iconM = 20.dp
    val iconL = 24.dp

    val minTouchTarget = 48.dp

    /** 单条内容最大宽度。手机横屏/平板时避免一行文字拉到 900dp 那么长。 */
    val maxContentWidth = 720.dp
}

object TypeScale {
    val displaySmall = 24.sp
    val titleLarge = 20.sp
    val titleMedium = 17.sp
    val bodyLarge = 15.sp
    val bodyMedium = 14.sp
    val bodySmall = 13.sp
    val labelLarge = 14.sp
    val labelMedium = 12.sp
    val labelSmall = 11.sp
    /** 代码与命令输出：等宽且略小，一屏能多看几行。 */
    val code = 12.5.sp
    val codeLineHeight = 18.sp
}

/** 内容块的垂直节奏。消息之间永远用这个，不要各页面自定。 */
val BLOCK_GAP = 10.dp
