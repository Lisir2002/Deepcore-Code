package com.deepcode.designsystem.theme.validator

/**
 * theme.json v1 认得的组/令牌名（§3.2 颜色面板、§3.4 圆角四档 + bubble）。
 *
 * 唯一事实源约束：theme.json 只允许覆盖这里的键，未知键 → 告警忽略（§7.3.2，前向兼容）。
 * 与 `AppColors` / `AppRadius` 属性一一对应并由 `ThemeTokenCatalogTest` 反射对齐，
 * 防止本目录与实际令牌漂移（新增令牌漏登记即红）。
 */
internal object ThemeTokenCatalog {

    /** §3.2 语义色面板 29 键。 */
    val colorKeys: Set<String> = setOf(
        // 品牌
        "primary", "onPrimary", "primaryContainer", "onPrimaryContainer",
        // 表面
        "surface", "surfaceVariant", "surfaceElevated",
        // 文本
        "textPrimary", "textSecondary", "textTertiary", "textInverse",
        // 边线
        "divider", "border",
        // 状态
        "success", "successContainer", "warning", "warningContainer",
        "danger", "dangerContainer", "info",
        // 业务保留（聊天场景）
        "diffAdd", "diffRemove", "toolRunning", "toolSuccess",
        "toolFailed", "toolAwaiting", "thinking", "codeSurface", "codeBorder",
    )

    /** §3.4 圆角可覆盖组（theme.json radius）：card/bubble 等 5 档。 */
    val radiusKeys: Set<String> = setOf(
        "card", "listItem", "chip", "sheet", "bubble",
    )

    /** theme.json 覆盖组的 JSON 字段名（BoardJson.color / radius）。 */
    const val GROUP_COLOR = "color"
    const val GROUP_RADIUS = "radius"
}