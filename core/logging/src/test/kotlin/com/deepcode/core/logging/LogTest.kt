package com.deepcode.core.logging

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogTest {

    private class CapturingSink : LogSink {
        val entries = mutableListOf<LogEntry>()
        override fun log(entry: LogEntry) {
            entries.add(entry)
        }
    }

    @BeforeTest
    fun setup() {
        Log.resetForTest()
    }

    @AfterTest
    fun teardown() {
        Log.resetForTest()
    }

    @Test
    fun `sink 收到结构化条目且消息已脱敏`() {
        val sink = CapturingSink()
        Log.plant(sink)
        Log.log(LogLevel.WARN, LogCategory.SECURITY_CREDENTIAL, "McpClient", "token: abc123 fail")
        assertEquals(1, sink.entries.size)
        val e = sink.entries.first()
        assertEquals(LogCategory.SECURITY_CREDENTIAL, e.cat)
        assertEquals("McpClient", e.tag)
        // 凭据值连同紧邻描述整段替换（安全优先，宁可多替换不泄露）
        assertEquals("token: ***", e.msg)
        assertTrue(e.ts.isNotBlank())
        assertTrue(e.thr.isNotBlank())
    }

    @Test
    fun `低于最低级别不派发`() {
        val sink = CapturingSink()
        Log.plant(sink)
        Log.setMinLevel(LogLevel.WARN)
        Log.log(LogLevel.INFO, LogCategory.SYSTEM_FRAMEWORK, "t", "low")
        Log.log(LogLevel.ERROR, LogCategory.ERROR_FAILURE, "t", "high")
        assertEquals(listOf("high"), sink.entries.map { it.msg })
    }

    @Test
    fun `环形缓冲保留最近条目`() {
        Log.plant(CapturingSink())
        repeat(250) { i -> Log.log(LogLevel.INFO, LogCategory.OPERATION_AGENT, "t", "m$i") }
        val ring = Log.dumpRing()
        assertEquals(200, ring.size)
        assertEquals("m50", ring.first().msg)
    }

    @Test
    fun `异常堆栈被脱敏`() {
        val sink = CapturingSink()
        Log.plant(sink)
        val t = RuntimeException("token: secret leak")
        Log.log(LogLevel.ERROR, LogCategory.ERROR_EXCEPTION, "t", "boom", t)
        assertTrue(sink.entries.first().ex!!.contains("token: ***"))
    }

    @Test
    fun `sink 抛异常不影响其它 sink`() {
        val boom = object : LogSink {
            override fun log(entry: LogEntry) {
                throw IllegalStateException("sink down")
            }
        }
        val good = CapturingSink()
        Log.plant(boom)
        Log.plant(good)
        Log.log(LogLevel.INFO, LogCategory.SYSTEM_FRAMEWORK, "t", "ok")
        assertEquals(1, good.entries.size)
    }

    @Test
    fun `模块登记`() {
        Log.modules.register("core-agent", "AgentRuntime")
        assertEquals("AgentRuntime", Log.modules.prefixFor("core-agent"))
    }
}
