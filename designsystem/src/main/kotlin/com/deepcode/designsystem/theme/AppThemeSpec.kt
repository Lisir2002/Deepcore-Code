package com.deepcode.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp

/**
 * 主题包运行时形态（§7.1）：明/暗两套取值 + 标识。
 *
 * 具名属性 = 编译期完整性：加令牌 = 加属性 + 明暗值 + 配对声明（§10.1 矩阵同步登记）。
 * `AppTokens` 本体**不允许空字段**；可空的差别只允许出现在 `TokenPair` 语义上——
 * `AppColors` 只存解析完成的定值。
 */

/** 明/暗两套值的载体；缺省 = 回退 brand 对应模式（§7.3.1）。 */
@Immutable
data class TokenPair<T>(val light: T, val dark: T)

/** 用户深色模式三态（§8.2）。 */
enum class DarkMode { FOLLOW_SYSTEM, LIGHT, DARK }

/**
 * 圆角五档（theme.json radius 覆盖组入口）。原 AppShapes 由它驱动，卡片/折叠泡泡档位即令牌。
 */
@Immutable
data class AppRadius(
    val card: Dp,
    val listItem: Dp,
    val chip: Dp,
    val sheet: Dp,
    val bubble: Dp,
) {
    fun toShapes(): Shapes = Shapes(
        extraSmall = RoundedCornerShape(card),
        small = RoundedCornerShape(card),
        medium = RoundedCornerShape(listItem),
        large = RoundedCornerShape(sheet),
        extraLarge = RoundedCornerShape(bubble),
    )
}

/** brand 常驻圆角档位（§3.4）。 */
@Immutable
val AppRadiusTokens = AppRadius(
    card = Dimens.radiusM,
    listItem = Dimens.radiusM,
    chip = Dimens.radiusS,
    sheet = Dimens.radiusXL,
    bubble = Dimens.radiusL,
)

/**
 * 一套完整主题 = 明暗两套 AppTokens。`registerPack`（§7.1）：风格包 id 进程内全局唯一。
 */
@Immutable
data class AppThemeSpec(
    val id: String,
    val name: String,
    val schemaVersion: Int = 1,
    val light: AppTokens,
    val dark: AppTokens,
    val source: Source = Source.BUILT_IN,   // BUILT_IN | USER_IMPORTED
) {
    enum class Source { BUILT_IN, USER_IMPORTED }
}