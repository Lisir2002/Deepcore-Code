package com.deepcode.core.uistate

import kotlin.test.Test
import kotlin.test.assertEquals

class RelativeTimeTest {

    private val now = 1_700_000_000_000L // 基准：某整点

    @Test
    fun `一分钟内是刚刚`() {
        assertEquals("刚刚", formatRelativeTime(now - 30_000L, now))
    }

    @Test
    fun `未来时间也兜底为刚刚`() {
        assertEquals("刚刚", formatRelativeTime(now + 5_000L, now))
    }

    @Test
    fun `一小时内是分钟级`() {
        assertEquals("5分钟前", formatRelativeTime(now - 5 * 60_000L, now))
    }

    @Test
    fun `一天内是小时级`() {
        assertEquals("2小时前", formatRelativeTime(now - 2 * 3_600_000L, now))
    }

    @Test
    fun `昨天显示为昨天`() {
        assertEquals("昨天", formatRelativeTime(now - 30 * 3_600_000L, now))
    }

    @Test
    fun `今年内显示月日`() {
        val twoDaysAgo = now - 3 * 24 * 3_600_000L
        val result = formatRelativeTime(twoDaysAgo, now)
        // 依赖系统时区，只断言"不是昨天/刚刚"这类相对词
        assertEquals(false, result.contains("前"))
        assertEquals(false, result == "昨天")
    }
}
