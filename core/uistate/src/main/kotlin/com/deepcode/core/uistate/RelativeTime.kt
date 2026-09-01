package com.deepcode.core.uistate

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * epoch millis → 人性化相对时间（纯函数，JVM 可测）。
 *
 * 只做「跨会话排序之外的展示」：刚刚 / N分钟前 / N小时前 / 昨天 / M月d日 / YYYY年M月d日。
 */
fun formatRelativeTime(
    epochMillis: Long,
    now: Long = System.currentTimeMillis(),
): String {
    val diff = now - epochMillis
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    return when {
        diff < 0 -> "刚刚"
        diff < minute -> "刚刚"
        diff < hour -> "${diff / minute}分钟前"
        diff < day -> "${diff / hour}小时前"
        diff < 2 * day -> "昨天"
        diff < 365 * day -> SimpleDateFormat("M月d日", Locale.getDefault()).format(Date(epochMillis))
        else -> SimpleDateFormat("yyyy年M月d日", Locale.getDefault()).format(Date(epochMillis))
    }
}
