package com.deepcode.core.logging

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RingBufferTest {

    private fun entry(n: Int) = LogEntry("t$n", LogLevel.INFO, LogCategory.OPERATION_AGENT, "tag", "msg$n")

    @Test
    fun `保持容量上限，最旧条目被移除`() {
        val buf = RingBuffer(capacity = 3)
        repeat(5) { buf.append(entry(it)) }
        assertEquals(3, buf.size)
        assertEquals(listOf(2, 3, 4), buf.dump().map { it.msg.removePrefix("msg").toInt() })
    }

    @Test
    fun `dump 保持时间顺序`() {
        val buf = RingBuffer(capacity = 10)
        repeat(4) { buf.append(entry(it)) }
        assertEquals(listOf(0, 1, 2, 3), buf.dump().map { it.msg.removePrefix("msg").toInt() })
    }

    @Test
    fun `并发 append 不丢失且不越界`() = runTest {
        val buf = RingBuffer(capacity = 100)
        repeat(8) { job ->
            launch {
                repeat(500) { i -> buf.append(entry(job * 1000 + i)) }
            }
        }
        testScheduler.advanceUntilIdle()
        assertEquals(100, buf.size)
        assertTrue(buf.dump().map { it.msg }.distinct().size == 100)
    }
}
