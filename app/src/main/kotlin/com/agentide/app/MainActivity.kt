package com.agentide.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import com.agentide.designsystem.theme.AppTheme
import com.agentide.feature.chat.ChatScreen

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
        ChatScreen()
    }
}
