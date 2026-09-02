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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.deepcode.designsystem.components.scaffold.NavItem
import com.deepcode.designsystem.components.scaffold.NavScaffold
import com.deepcode.designsystem.theme.StyleController
import com.deepcode.feature.chat.ConversationList
import com.deepcode.feature.chat.ChatScreen
import com.deepcode.feature.settings.SettingsAboutScreen
import com.deepcode.feature.settings.SettingsAppearanceScreen
import com.deepcode.feature.settings.SettingsLoggingScreen
import com.deepcode.feature.settings.SettingsModelScreen
import com.deepcode.feature.settings.ProviderEditScreen
import com.deepcode.feature.settings.SettingsScreen
import com.deepcode.feature.settings.SettingsSkillsScreen
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
 *     ├─（对话流 ChatScreen 全屏，由列表进入时压栈、隐藏底栏）
 *     └─ 设置 = SettingsScreen（分组入口列表，四级分组各自压栈进二级 Detail 页）
 *
 * 顶栏 / 底栏一律复用 designsystem 骨架槽位（AppTopAppBar / AppNavBar），
 * 页面不独立写样式；单一依赖 :designsystem。
 */
object AppRoutes {
    const val SHELL = "shell"
    const val CHAT = "chat/{conversationId}"

    // —— 设置三级页（Provider 编辑，由模型页压栈进入）——
    const val PROVIDER_EDIT = "settings/provider"

    // —— 设置二级页（入口列表压栈进入，DetailScaffold 自带返回，隐藏底栏）——
    // 带参路由：`settings/{page}?reason=` 支持直达（首次引导 / 连接异常跳转），
    // page 枚举见 [AppNavRoot.settingsDestination]。reason 为可选直达意图标签。
    const val SETTINGS_PATTERN = "settings/{page}?reason={reason}"

    fun chat(conversationId: String) = "chat/$conversationId"

    // —— 设置子页枚举 + 直达路由构造（拍板点 4：支持带参直达）——
    enum class SettingsPage(val segment: String) {
        APPEARANCE("appearance"),
        MODEL("model"),
        SKILLS("skills"),
        LOGGING("logging"),
        ABOUT("about"),
    }

    fun settings(page: SettingsPage, reason: String? = null): String {
        val base = "settings/${page.segment}"
        return reason?.takeIf { it.isNotBlank() }?.let { "$base?reason=$it" } ?: base
    }
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
                onNewConversation = { id -> navController.navigate(AppRoutes.chat(id)) },
                onOpenSettingsAppearance = { navController.navigate(AppRoutes.settings(AppRoutes.SettingsPage.APPEARANCE)) },
                onOpenSettingsModel = { navController.navigate(AppRoutes.settings(AppRoutes.SettingsPage.MODEL)) },
                onOpenSettingsSkills = { navController.navigate(AppRoutes.settings(AppRoutes.SettingsPage.SKILLS)) },
                onOpenSettingsLogging = { navController.navigate(AppRoutes.settings(AppRoutes.SettingsPage.LOGGING)) },
                onOpenSettingsAbout = { navController.navigate(AppRoutes.settings(AppRoutes.SettingsPage.ABOUT)) },
            )
        }
        // —— 设置二级页：单一带参路由 `settings/{page}?reason=`（拍板点 4：支持带参直达）——
        composable(
            route = AppRoutes.SETTINGS_PATTERN,
            arguments = listOf(
                navArgument("page") { type = NavType.StringType },
                navArgument("reason") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { backStackEntry ->
            val page = backStackEntry.arguments?.getString("page") ?: ""
            val reason = backStackEntry.arguments?.getString("reason")
            // 直达意图标签（首次引导 / 连接异常等）目前落日志；页面高亮后续接线
            if (!reason.isNullOrBlank()) {
                com.deepcode.core.logging.Log.log(
                    com.deepcode.core.logging.LogLevel.INFO,
                    com.deepcode.core.logging.LogCategory.OPERATION_USER,
                    "Nav",
                    "直达设置页 $page（reason=$reason）",
                )
            }
            when (page) {
                AppRoutes.SettingsPage.APPEARANCE.segment -> SettingsAppearanceScreen(
                    onBack = { navController.popBackStack() },
                )
                AppRoutes.SettingsPage.MODEL.segment -> SettingsModelScreen(
                    onBack = { navController.popBackStack() },
                    onOpenProvider = { navController.navigate(AppRoutes.PROVIDER_EDIT) },
                )
                AppRoutes.SettingsPage.SKILLS.segment -> SettingsSkillsScreen(
                    onBack = { navController.popBackStack() },
                )
                AppRoutes.SettingsPage.LOGGING.segment -> SettingsLoggingScreen(
                    onBack = { navController.popBackStack() },
                )
                AppRoutes.SettingsPage.ABOUT.segment -> SettingsAboutScreen(
                    appVersion = "${com.deepcode.agent.BuildConfig.VERSION_NAME}（${com.deepcode.agent.BuildConfig.VERSION_CODE}）",
                    onBack = { navController.popBackStack() },
                )
            }
        }
        // —— 设置三级页：Provider 编辑（模型页压栈进入，保存后回退到模型页刷新）——
        composable(AppRoutes.PROVIDER_EDIT) {
            ProviderEditScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
        composable(
            route = AppRoutes.CHAT,
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: "default"
            // 对话流全屏：复用现有 ChatScreen（自带 AppScaffold 顶栏），隐藏底栏。
            ChatScreen(
                conversationId = conversationId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

/** 底部 tab 壳：底栏三入口 + 选中内容区。 */
@Composable
internal fun HomeShell(
    onOpenConversation: (String) -> Unit,
    onNewConversation: (String) -> Unit,
    onOpenSettingsAppearance: () -> Unit,
    onOpenSettingsModel: () -> Unit,
    onOpenSettingsSkills: () -> Unit,
    onOpenSettingsLogging: () -> Unit,
    onOpenSettingsAbout: () -> Unit,
) {
    val tabs = remember {
        listOf(
            NavItem(id = "chat", text = "对话", icon = Icons.AutoMirrored.Filled.Chat),
            NavItem(id = "terminal", text = "终端", icon = Icons.Filled.Work),
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
            1 -> TerminalPlaceholder(contentPadding = padding)
            2 -> SettingsScreen(
                modifier = Modifier.padding(padding),
                onOpenAppearance = onOpenSettingsAppearance,
                onOpenModel = onOpenSettingsModel,
                onOpenSkills = onOpenSettingsSkills,
                onOpenLogging = onOpenSettingsLogging,
                onOpenAbout = onOpenSettingsAbout,
            )
        }
    }
}