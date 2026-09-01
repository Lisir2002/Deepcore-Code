package com.deepcode.agent.nav

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.deepcode.designsystem.components.AppEmptyState
import com.deepcode.designsystem.components.AppScaffold

/** 底部 tab：「工作」占位页。后续接工作台/最近活动时替换内容区，骨架槽位不动。 */
@Composable
internal fun WorkPlaceholder(contentPadding: PaddingValues) {
    AppScaffold(title = "工作", modifier = Modifier.padding(contentPadding)) { padding ->
        AppEmptyState(
            title = "工作台搭建中",
            message = "这里将展示最近的工具执行与文件变更。",
            modifier = Modifier.padding(padding),
        )
    }
}