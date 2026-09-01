package com.deepcode.designsystem.components.scaffold

import androidx.compose.ui.graphics.vector.ImageVector

/** 顶栏 Tab 数据模型（AppTopTabs，§6.2）。 */
data class TabItem(
    val id: String,
    val text: String,
    val icon: ImageVector? = null,
    val badge: Int? = null,
)

/** 底栏导航数据模型（AppNavBar，§6.3，3–5 项）。 */
data class NavItem(
    val id: String,
    val text: String,
    val icon: ImageVector,
    val badge: Int? = null,
)