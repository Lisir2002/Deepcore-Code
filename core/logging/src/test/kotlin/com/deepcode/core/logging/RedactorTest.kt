package com.deepcode.core.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RedactorTest {

    private val redactor = Redactor()

    @Test
    fun `凭据字段值被替换`() {
        assertEquals("Authorization: ***", redactor.redact("Authorization: Bearer xyz123"))
        assertEquals("api-key=***", redactor.redact("api-key=abcdef"))
        assertEquals("token: ***", redactor.redact("token: s3cr3t"))
        assertEquals("password=***", redactor.redact("password=123456"))
    }

    @Test
    fun `URL 凭据被隐藏`() {
        assertEquals("https://***:***@host.com/path", redactor.redact("https://user:pass@host.com/path"))
    }

    @Test
    fun `绝对路径被相对化`() {
        val out = redactor.redact("write to /data/user/0/com.deepcode.agent/files/logs/app.log")
        assertTrue(out!!.contains("[path]"), out)
        assertTrue(!out.contains("/data/user/0/com.deepcode.agent"), out)
        assertTrue(out.contains("app.log"), out)
    }

    @Test
    fun `query 中的凭据被替换`() {
        assertEquals("?token=***&x=1", redactor.redact("?token=abc123&x=1"))
    }

    @Test
    fun `设备标识 hash 化`() {
        val a = redactor.redactDeviceId("abc123")
        val b = redactor.redactDeviceId("abc123")
        assertEquals(a, b)
        assertTrue(a.startsWith("id:"))
        assertTrue(!a.contains("abc123"))
    }

    @Test
    fun `普通文本不受影响`() {
        assertEquals("hello world 2026", redactor.redact("hello world 2026"))
    }

    @Test
    fun `null 安全`() {
        assertEquals(null, redactor.redact(null))
    }
}
