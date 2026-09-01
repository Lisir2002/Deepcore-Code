package com.deepcode.agent.logging

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * 外部存储权限统一处理（决策 D10/D11）。
 *
 * 目标：公共根目录 `/sdcard/deepcorefile` 实时双写镜像。
 *   · API ≤ 29：`WRITE_EXTERNAL_STORAGE` + `requestLegacyExternalStorage`（manifest 声明）
 *   · API 30+ ：`MANAGE_EXTERNAL_STORAGE`（系统设置手动授权一次）
 * 未授权时降级：只写私有目录，日志不丢；设置页提供引导入口与"立即同步"按钮。
 */
object StoragePermission {

    /** 当前是否具备"写公共根目录"能力。 */
    fun canWriteRoot(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        }

    /** 引导授权 Intent（API 30+ 直达"所有文件访问"；旧版本由运行时权限弹窗处理）。 */
    fun openSettings(context: Context): Intent {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            )
        } else {
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
