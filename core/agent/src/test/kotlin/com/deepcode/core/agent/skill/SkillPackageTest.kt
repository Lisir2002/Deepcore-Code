package com.deepcode.core.agent.skill

import com.deepcode.core.agent.spi.Entry
import com.deepcode.core.agent.spi.FileRead
import com.deepcode.core.agent.spi.FileStat
import com.deepcode.core.agent.spi.Workspace
import com.deepcode.core.model.WorkspaceRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkillPackageTest {

    // ───────────────────────── 一个内存版 Workspace 替身 ─────────────────────────

    private class MemWorkspace : Workspace {
        override val ref = WorkspaceRef.LocalDir("/fake")
        private val files = LinkedHashMap<String, String>()
        private val dirs = LinkedHashSet<String>()

        fun dir(path: String) { dirs.add(path.trimEnd('/')) }
        fun file(path: String, content: String) {
            val parent = path.substringBeforeLast('/').ifEmpty { "/" }
            dirs.add(parent)
            files[path] = content
        }

        override suspend fun exists(path: String) = path in files || path in dirs
        override suspend fun readText(path: String, maxBytes: Int): FileRead {
            val c = files[path] ?: error("no such file: $path")
            return FileRead(c, truncated = c.length > maxBytes, totalBytes = c.length.toLong())
        }
        override suspend fun writeText(path: String, content: String, createParentDirs: Boolean) { files[path] = content }
        override suspend fun delete(path: String) = files.remove(path) != null || dirs.remove(path) != null
        override suspend fun stat(path: String) = if (path in files || path in dirs) FileStat(path, true, path in dirs) else null
        override suspend fun rootPath() = "/fake"

        override suspend fun list(path: String, recursive: Boolean): List<Entry> {
            val prefix = if (path.endsWith("/")) path else "$path/"
            val names = LinkedHashSet<String>()
            for (d in dirs + files.keys) {
                if (!d.startsWith(prefix)) continue
                val rest = d.removePrefix(prefix)
                val first = rest.substringBefore('/')
                if (first.isNotEmpty() && !rest.contains('/')) names.add(first)
            }
            return names.map { name ->
                val full = "$prefix$name"
                Entry(full, isDirectory = full in dirs)
            }
        }
    }

    private fun validFrontmatter(name: String = "pdf", description: String = "处理 PDF 文件") = """
        ---
        name: $name
        description: $description
        license: MIT
        compatibility: requires python3
        metadata:
          author: deepcode
          version: "1.0"
        allowed-tools:
          - Read
          - Bash
        ---
        # 正文指令
        当任务涉及 PDF 时，先读取文件再用 pdftotext 抽取文本。
    """.trimIndent()

    // ───────────────────────── 解析器 ─────────────────────────

    @Test
    fun `合法 frontmatter 解析出全部字段`() {
        val parsed = DefaultSkillParser().parse(validFrontmatter(), declaredDirName = "pdf")
        assertEquals("pdf", parsed.name)
        assertEquals("处理 PDF 文件", parsed.description)
        assertEquals("MIT", parsed.license)
        assertEquals("requires python3", parsed.compatibility)
        assertEquals("deepcode", parsed.metadata["author"])
        assertEquals("1.0", parsed.metadata["version"])
        assertEquals(listOf("Read", "Bash"), parsed.allowedTools)
        assertTrue(parsed.body.contains("pdftotext"))
    }

    @Test
    fun `name 含大写被拒绝`() {
        assertFailsWith<SkillParseException> {
            DefaultSkillParser().parse(validFrontmatter(name = "PdfTool"), declaredDirName = "PdfTool")
        }
    }

    @Test
    fun `name 与目录名不一致被拒绝`() {
        assertFailsWith<SkillParseException> {
            DefaultSkillParser().parse(validFrontmatter(name = "pdf"), declaredDirName = "other-dir")
        }
    }

    @Test
    fun `缺少 frontmatter 分隔符被拒绝`() {
        assertFailsWith<SkillParseException> {
            DefaultSkillParser().parse("# 没有 frontmatter\n正文")
        }
    }

    @Test
    fun `description 超长被拒绝`() {
        val long = "x".repeat(1025)
        assertFailsWith<SkillParseException> {
            DefaultSkillParser().parse(validFrontmatter(description = long), declaredDirName = "pdf")
        }
    }

    @Test
    fun `保留词 anthropic 被拒绝`() {
        assertFailsWith<SkillParseException> {
            DefaultSkillParser().parse(validFrontmatter(name = "anthropic-helper"), declaredDirName = "anthropic-helper")
        }
    }

    // ───────────────────────── 加载器 ─────────────────────────

    @Test
    fun `扫描多目录_有效 skill 入列_坏包报错_按名排序`() {
        val ws = MemWorkspace()
        ws.dir(".deepcode/skills")
        ws.dir(".deepcode/skills/pdf")
        ws.file(".deepcode/skills/pdf/SKILL.md", validFrontmatter(name = "pdf", description = "处理 PDF"))
        ws.dir(".deepcode/skills/ocr")
        ws.file(".deepcode/skills/ocr/SKILL.md", validFrontmatter(name = "ocr", description = "识别图片文字"))
        // 坏包：name 与目录名不符
        ws.dir(".deepcode/skills/bad")
        ws.file(".deepcode/skills/bad/SKILL.md", validFrontmatter(name = "wrong", description = "错"))

        val result = runScoped { WorkspaceSkillLoader(ws, listOf(".deepcode/skills")).load() }

        assertEquals(listOf("ocr", "pdf"), result.skills.map { it.name })
        assertEquals(1, result.errors.size)
        assertEquals(".deepcode/skills/bad", result.errors[0].dirPath)
        // 文件位置信息正确
        assertEquals(".deepcode/skills/pdf/SKILL.md", result.skills.first { it.name == "pdf" }.bodyPath)
    }

    @Test
    fun `缺少 SKILL 入口文件的目录报错`() {
        val ws = MemWorkspace()
        ws.dir(".deepcode/skills")
        ws.dir(".deepcode/skills/empty")
        val result = runScoped { WorkspaceSkillLoader(ws, listOf(".deepcode/skills")).load() }
        assertEquals(0, result.skills.size)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0].reason.contains("SKILL.md"))
    }

    @Test
    fun `多 root 同名目录先到先得_不重复`() {
        val ws = MemWorkspace()
        ws.dir("skills")
        ws.dir("skills/pdf")
        ws.file("skills/pdf/SKILL.md", validFrontmatter(name = "pdf", description = "A"))
        // 第二个 root 里也有同名 but 内容不同——应被去重忽略
        ws.dir(".extra/skills")
        ws.dir(".extra/skills/pdf")
        ws.file(".extra/skills/pdf/SKILL.md", validFrontmatter(name = "pdf", description = "B"))

        val result = runScoped {
            WorkspaceSkillLoader(ws, listOf("skills", ".extra/skills")).load()
        }
        assertEquals(1, result.skills.size)
        assertEquals("A", result.skills[0].description)
    }

    // ───────────────────────── 注入器 ─────────────────────────

    @Test
    fun `空技能列表生成空段`() {
        assertTrue(DefaultSkillInjector().buildSkillSection(emptyList()).isEmpty())
    }

    @Test
    fun `技能段含 name 与 description_不含正文`() {
        val skills = listOf(
            SkillManifest("pdf", "处理 PDF", dirPath = "x/pdf", bodyPath = "x/pdf/SKILL.md"),
            SkillManifest("ocr", "识别图片文字", dirPath = "x/ocr", bodyPath = "x/ocr/SKILL.md"),
        )
        val section = DefaultSkillInjector().buildSkillSection(skills)
        assertTrue(section.contains("pdf") && section.contains("处理 PDF"))
        assertTrue(section.contains("ocr") && section.contains("识别图片文字"))
        // 正文不应泄漏进 L1 段（那是 L2 该干的事）
        assertFalse(section.contains("pdftotext"))
    }

    // ───────────────────────── 辅助 ─────────────────────────

    private fun <T> runScoped(block: suspend () -> T): T =
        kotlinx.coroutines.runBlocking { block() }
}
