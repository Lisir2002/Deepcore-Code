package com.deepcode.core.logging

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogEntryTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `序列化为 JSON 行并还原`() {
        val entry = LogEntry(
            ts = "2026-09-01T10:00:00Z",
            lvl = LogLevel.WARN,
            cat = LogCategory.SECURITY_PERMISSION,
            tag = "Sandbox",
            msg = "命令被白名单拒绝",
            thr = "main",
            ex = null,
        )
        val encoded = json.encodeToString(LogEntry.serializer(), entry)
        assertTrue(encoded.contains("\"cat\":\"SECURITY_PERMISSION\""), encoded)
        val decoded = json.decodeFromString(LogEntry.serializer(), encoded)
        assertEquals(entry, decoded)
    }

    @Test
    fun `displayName 与危险类判断`() {
        assertEquals("SECURITY.PERMISSION", LogCategory.SECURITY_PERMISSION.displayName)
        assertTrue(LogCategory.SECURITY_CRASH_CAUSE.isSecurity)
        assertTrue(!LogCategory.OPERATION_DATA.isSecurity)
    }

    @Test
    fun `group 构造完整分类`() {
        assertEquals(LogCategory.SECURITY_PERMISSION, LogGroup.SECURITY.sub(LogSubCategory.PERMISSION))
    }
}
