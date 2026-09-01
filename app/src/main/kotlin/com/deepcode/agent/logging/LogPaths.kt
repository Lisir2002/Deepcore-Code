package com.deepcode.agent.logging

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * 日志目录路径统一收口（决策 D8/D9）：
 *   · 私有目录 `filesDir/logs`（始终可写）
 *   · 公共根目录 `/sdcard/deepcorefile/logs`（授权后实时双写镜像）
 * 所有日志相关类（Sink / CrashVault / Exporter）都从这里取路径，避免各自拼错。
 */
object LogPaths {
    const val ROOT_NAME = "deepcorefile"

    fun privateLogDir(context: Context): File = File(context.filesDir, "logs")

    fun rootBaseDir(): File = File(Environment.getExternalStorageDirectory(), ROOT_NAME)

    fun rootLogDir(): File = File(rootBaseDir(), "logs")

    fun rootReadme(): File = File(rootBaseDir(), "README.txt")
}
