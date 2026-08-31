package com.deepcode.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.deepcode.designsystem.theme.AppTheme
import com.deepcode.feature.chat.ChatScreen
import com.deepcode.feature.settings.SettingsScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AgentIdeRoot()
        }
    }
}

/**
 * 整个 App 唯一的 Composable 根节点。
 *
 * 主题只在这里设置一次，页面不允许自己包 MaterialTheme。
 */
@Composable
private fun AgentIdeRoot() {
    AppTheme {
        var showSettings by remember { mutableStateOf(false) }
        if (showSettings) {
            SettingsScreen(onBack = { showSettings = false })
        } else {
            ChatScreen(onOpenSettings = { showSettings = true })
        }
    }
}
