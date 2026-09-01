package com.deepcode.agent.logging

import android.content.Context
import com.deepcode.feature.settings.LoggingActions

/**
 * [LoggingActions] 的 :app 实现（P5）。
 *
 * 把设置页要的四个动作接到真实的 LogExporter / StoragePermission 上；
 * 仅做转发，不含业务规则。
 */
class AndroidLoggingActions(
    private val context: Context,
    private val exporter: LogExporter,
) : LoggingActions {

    override val canAccessRoot: Boolean
        get() = StoragePermission.canWriteRoot(context)

    override fun openPermissionSettings() {
        context.startActivity(StoragePermission.openSettings(context))
    }

    override suspend fun exportLogs() {
        exporter.exportAndShare()
    }

    override fun syncToRoot() {
        exporter.syncToRoot()
    }
}
