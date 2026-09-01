package com.deepcode.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import com.deepcode.agent.logging.CrashVault
import com.deepcode.agent.logging.LogExporter
import com.deepcode.agent.nav.AppNavRoot
import com.deepcode.designsystem.components.AppPrimaryButton
import com.deepcode.designsystem.components.AppText
import com.deepcode.designsystem.components.AppTextButton
import com.deepcode.designsystem.components.overlay.AppNoticeDialog
import com.deepcode.designsystem.theme.AppTextStyle
import com.deepcode.designsystem.theme.AppTheme
import com.deepcode.designsystem.theme.Dimens
import com.deepcode.designsystem.theme.LocalStyleController
import com.deepcode.designsystem.theme.StyleController
import kotlinx.coroutines.launch
import org.koin.android.ext.android.getKoin

class MainActivity : ComponentActivity() {

    private val styleController: StyleController by lazy { getKoin().get() }
    private val logExporter: LogExporter by lazy { getKoin().get() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AgentIdeRoot(styleController, logExporter)
        }
    }
}

/**
 * 整个 App 唯一的 Composable 根节点。
 *
 * 主题只在这里设置一次，页面不允许自己包 MaterialTheme。
 * 页面结构由 `AppNavRoot`（Navigation Compose 图）承载：底部 tab 壳
 * （对话 / 工作 / 设置）+ 对话流全屏路由。
 *
 * [CrashPendingDialog] 悬浮在最上层：上次运行崩溃过（决策 D13 崩溃标记）
 * 就弹窗询问是否立即导出日志包，不拦截任何页面。
 */
@Composable
private fun AgentIdeRoot(styleController: StyleController, logExporter: LogExporter) {
    CompositionLocalProvider(LocalStyleController provides styleController) {
        AppTheme {
            AppNavRoot(styleController)
            CrashPendingDialog(logExporter)
        }
    }
}

/**
 * 崩溃后分享弹窗（决策 D13）：下次启动检测到崩溃标记即询问"导出日志？"。
 *
 * 动作：
 *   · "导出日志" → LogExporter.exportAndShare() 生成四层 zip 并调起系统分享
 *   · "忽略"     → 仅消费标记，不导出
 * 无论选哪个，确认后都消费标记，避免每次启动都弹。
 */
@Composable
private fun CrashPendingDialog(logExporter: LogExporter) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var show by remember { mutableStateOf(CrashVault.hasPendingCrash(context)) }

    if (show) {
        AppNoticeDialog(
            title = "上次运行崩溃了",
            onDismiss = {
                CrashVault.consumePendingCrash(context)
                show = false
            },
            acknowledgeText = null,
            richContent = {
                AppText(
                    "已捕获崩溃现场并写入日志。是否导出四层日志包" +
                        "（崩溃栈 / 上下文 / 环境 / 事件流）以便反馈？",
                    style = AppTextStyle.Body,
                )
            },
            actions = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.spaceXL),
                    horizontalArrangement = Arrangement.End,
                ) {
                    AppTextButton(
                        text = "忽略",
                        onClick = {
                            CrashVault.consumePendingCrash(context)
                            show = false
                        },
                    )
                    Spacer(Modifier.size(Dimens.spaceS))
                    AppPrimaryButton(
                        text = "导出日志",
                        onClick = {
                            scope.launch {
                                runCatching { logExporter.exportAndShare() }
                                CrashVault.consumePendingCrash(context)
                                show = false
                            }
                        },
                    )
                }
            },
        )
    }
}
