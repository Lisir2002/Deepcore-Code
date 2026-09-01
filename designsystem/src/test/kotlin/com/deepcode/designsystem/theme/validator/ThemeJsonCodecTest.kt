package com.deepcode.designsystem.theme.validator

import com.deepcode.designsystem.theme.validator.ThemeJsonCodec.CodecResult
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * ThemeJsonCodec 结构/值层校验（§7.4 前两行 + §7.5 版本），12.3 loader 表驱动的编解码侧用例。
 */
class ThemeJsonCodecTest {

    @Test
    fun `最小合法 delta 包解析成功`() {
        val raw = """
            {
              "id": "midnight",
              "name": "午夜",
              "schemaVersion": 1,
              "light": {
                "color": { "primary": "#7C5CFF", "surface": "#F7F7FA" },
                "radius": { "card": 20, "bubble": 22 }
              },
              "dark": { "color": { "primary": "#9B85FF" } }
            }
        """.trimIndent()

        val result = ThemeJsonCodec.decode(raw)
        val ok = assertIs<CodecResult.Success>(result)
        assertEquals("midnight", ok.pack.id)
        assertEquals(1, ok.pack.schemaVersion)
        assertEquals(20, ok.pack.light?.radius?.get("card"))
        assertTrue(ok.warnings.isEmpty())
    }

    @Test
    fun `空 id 拒载`() {
        val r = ThemeJsonCodec.decode("""
            { "id": "", "name": "无id", "light": { "color": { "primary": "#000000" } } }
        """.trimIndent())
        val rej = assertIs<CodecResult.Reject>(r)
        assertContains(rej.errors, "id 必填（非空）")
    }

    @Test
    fun `schemaVersion 过高拒载不降级`() {
        val r = ThemeJsonCodec.decode("""
            { "id": "x", "name": "x", "schemaVersion": 2 }
        """.trimIndent())
        val rej = assertIs<CodecResult.Reject>(r)
        assertTrue(rej.errors.any { "schemaVersion 2" in it && "高于" in it })
    }

    @Test
    fun `JSON 非法拒载`() {
        val r = ThemeJsonCodec.decode("{ not json }}")
        assertIs<CodecResult.Reject>(r)
    }

    @Test
    fun `非法 hex 拒载并给键路径`() {
        val r = ThemeJsonCodec.decode("""
            { "id": "x", "name": "x", "light": { "color": { "primary": "abc" } } }
        """.trimIndent())
        val rej = assertIs<CodecResult.Reject>(r)
        assertTrue(rej.errors.any { "light.color.primary" in it && "非法 hex" in it })
    }

    @Test
    fun `radius 越界拒载并给键路径`() {
        val r = ThemeJsonCodec.decode("""
            { "id": "x", "name": "x", "light": { "radius": { "card": 999 } } }
        """.trimIndent())
        val rej = assertIs<CodecResult.Reject>(r)
        assertTrue(rej.errors.any { "light.radius.card" in it && "越界" in it })
    }

    @Test
    fun `radius 组内类型冲突拒载`() {
        val r = ThemeJsonCodec.decode("""
            { "id": "x", "name": "x", "light": { "radius": { "card": "big" } } }
        """.trimIndent())
        assertIs<CodecResult.Reject>(r)
    }

    @Test
    fun `未知颜色令牌告警忽略前向兼容`() {
        val r = ThemeJsonCodec.decode("""
            { "id": "x", "name": "x", "light": { "color": { "brandNew": "#000000", "primary": "#7C5CFF" } } }
        """.trimIndent())
        val ok = assertIs<CodecResult.Success>(r)
        assertTrue(ok.warnings.any { "brandNew" in it })
        assertEquals("#7C5CFF", ok.pack.light?.color?.get("primary"))
    }

    @Test
    fun `encode 往返一致`() {
        val pack = ThemePackJson(
            id = "midnight", name = "午夜", schemaVersion = 1,
            light = BoardJson(color = mapOf("primary" to "#7C5CFF"), radius = mapOf("card" to 20)),
        )
        val encoded = ThemeJsonCodec.encode(pack)
        val round = ThemeJsonCodec.decode(encoded)
        val ok = assertIs<CodecResult.Success>(round)
        assertEquals(pack, ok.pack)
    }
}