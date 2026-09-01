package com.deepcode.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.deepcode.designsystem.theme.AppTheme
import com.deepcode.designsystem.theme.LocalStyleController
import com.deepcode.designsystem.theme.StyleController
import com.deepcode.feature.chat.ChatScreen
import com.deepcode.feature.settings.SettingsScreen
import org.koin.android.ext.android.getKoin

class MainActivity : ComponentActivity() {

    private val styleController: StyleController by lazy { getKoin().get() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AgentIdeRoot(styleController)
        }
    }
}

/**
 * 整个 App 唯一的 Composable 根节点。
 *
 * 主题只在这里设置一次，页面不允许自己包 MaterialTheme。
 */
@Composable
private fun AgentIdeRoot(styleController: StyleController) {
    CompositionLocalProvider(LocalStyleController provides styleController) {
        AppTheme {
            var showSettings by remember { mutableStateOf(false) }
            if (showSettings) {
                SettingsScreen(onBack = { showSettings = false })
            } else {
                ChatScreen(onOpenSettings = { showSettings = true })
            }
        }
    }
}
