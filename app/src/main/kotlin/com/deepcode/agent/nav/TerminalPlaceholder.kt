package com.deepcode.agent.nav

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.deepcode.designsystem.components.AppEmptyState
import com.deepcode.designsystem.components.AppScaffold

/**
 * 底部 tab：「终端」占位页。
 *
 * 原「工作」tab 改名为「终端」（设置骨架定稿拍板点 #3）。终端详细设计
 * （命令面板 / shell 会话 / 远端执行）在设置骨架完成后单独议题，此处仅占位改名。
 */
@Composable
internal fun TerminalPlaceholder(contentPadding: PaddingValues) {
    AppScaffold(title = "终端", modifier = Modifier.padding(contentPadding)) { padding ->
        AppEmptyState(
            title = "终端搭建中",
            message = "这里将提供命令执行与远端会话。",
            modifier = Modifier.padding(padding),
        )
    }
}