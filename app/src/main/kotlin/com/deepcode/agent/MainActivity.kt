package com.deepcode.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.deepcode.agent.nav.AppNavRoot
import com.deepcode.designsystem.theme.AppTheme
import com.deepcode.designsystem.theme.LocalStyleController
import com.deepcode.designsystem.theme.StyleController
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
 * 页面结构由 `AppNavRoot`（Navigation Compose 图）承载：底部 tab 壳
 * （对话 / 工作 / 设置）+ 对话流全屏路由。
 */
@Composable
private fun AgentIdeRoot(styleController: StyleController) {
    CompositionLocalProvider(LocalStyleController provides styleController) {
        AppTheme {
            AppNavRoot(styleController)
        }
    }
}
