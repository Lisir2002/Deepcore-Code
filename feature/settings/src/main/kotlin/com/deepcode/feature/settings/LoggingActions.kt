package com.deepcode.feature.settings

/**
 * 日志区操作契约（P5：设置页"日志"区块）。
 *
 * feature:settings 不依赖 :app / :core:logging，只认这份接口；
 * 具体实现（LogExporter + StoragePermission + CrashVault）由 :app 装配层注入。
 */
interface LoggingActions {

    /** 是否已具备写公共根目录 /sdcard/deepcorefile 的权限（未授权则只写私有目录降级）。 */
    val canAccessRoot: Boolean

    /** 打开系统授权引导（API 30+ 直达"所有文件访问"设置页；旧版本走运行时权限弹窗）。 */
    fun openPermissionSettings()

    /** 导出四层日志包（zip）并调起系统分享（suspend：内部含 zip 打包）。 */
    suspend fun exportLogs()

    /** 授权后立即把私有日志补齐到根目录（决策 D11）。 */
    fun syncToRoot()
}
