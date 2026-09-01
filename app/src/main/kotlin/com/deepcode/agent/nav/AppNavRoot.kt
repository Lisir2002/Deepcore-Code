package com.deepcode.agent.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.deepcode.designsystem.components.scaffold.NavItem
import com.deepcode.designsystem.components.scaffold.NavScaffold
import com.deepcode.designsystem.theme.StyleController
import com.deepcode.feature.chat.ConversationList
import com.deepcode.feature.chat.ChatScreen
import com.deepcode.feature.settings.SettingsScreen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Work

/**
 * App 级页面骨架重建后的根导航壳（Navigation Compose 图）。
 *
 * 结构（§导航重建，D14 槽位化）：
 *   shell root（NavScaffold 底栏：对话 / 工作 / 设置）
 *     ├─ 对话 = ConversationList（对话列表首屏，右“更多”菜单）
 *     └─（对话流 ChatScreen 全屏，由列表进入时压栈、隐藏底栏）
 *
 * 顶栏 / 底栏一律复用 designsystem 骨架槽位（AppTopAppBar / AppNavBar），
 * 页面不独立写样式；单一依赖 :designsystem。
 */
object AppRoutes {
    const val SHELL = "shell"
    const val CHAT = "chat/{conversationId}"

    fun chat(conversationId: String) = "chat/$conversationId"
}

@Composable
fun AppNavRoot(styleController: StyleController) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = AppRoutes.SHELL,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable(AppRoutes.SHELL) {
            HomeShell(
                onOpenConversation = { id -> navController.navigate(AppRoutes.chat(id)) },
                onNewConversation = { navController.navigate(AppRoutes.chat("new")) },
            )
        }
        composable(AppRoutes.CHAT) {
            // 对话流全屏：复用现有 ChatScreen（自带 AppScaffold 顶栏），隐藏底栏。
            ChatScreen(onBack = { navController.popBackStack() })
        }
    }
}

/** 底部 tab 壳：底栏三入口 + 选中内容区。 */
@Composable
internal fun HomeShell(
    onOpenConversation: (String) -> Unit,
    onNewConversation: () -> Unit,
) {
    val tabs = remember {
        listOf(
            NavItem(id = "chat", text = "对话", icon = Icons.AutoMirrored.Filled.Chat),
            NavItem(id = "work", text = "工作", icon = Icons.Filled.Work),
            NavItem(id = "settings", text = "设置", icon = Icons.Filled.Settings),
        )
    }
    var selected by rememberSaveable { mutableIntStateOf(0) }

    NavScaffold(
        tabs = tabs,
        selectedIndex = selected,
        onSelected = { selected = it },
    ) { index, padding ->
        when (index) {
            0 -> ConversationList(
                onOpenConversation = onOpenConversation,
                onNewConversation = onNewConversation,
                contentPadding = padding,
            )
            1 -> WorkPlaceholder(contentPadding = padding)
            2 -> SettingsScreen(
                onBack = null,
                modifier = Modifier.padding(padding),
            )
        }
    }
}