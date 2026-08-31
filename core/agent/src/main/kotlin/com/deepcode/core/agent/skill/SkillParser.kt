package com.deepcode.core.agent.skill

/**
 * SKILL.md 解析器：YAML frontmatter（受限子集）+ 校验，对齐 Agent Skills 开放标准。
 *
 * 为什么不引 YAML 库：标准 frontmatter 的字段集合小且封闭（两个必填、四个可选），
 * 一个逐行状态机就能覆盖；换来的是 core:agent 保持零第三方依赖、JVM 单测零环境差异。
 *
 * 支持的语法子集：
 *   key: value            （成对引号会被剥掉）
 *   key: [a, b, c]        （内联数组）
 *   key:                  （后跟缩进的子键，仅用于 metadata）
 *     sub: value
 *   key:                  （后跟 "- item" 列表项，用于 allowed-tools）
 *     - item
 */
interface SkillParser {

    /**
     * 解析并校验 frontmatter，返回其中间产物（不含文件位置信息）。
     * [declaredDirName] 传入 skill 目录名时校验 name ↔ 目录名一致性（标准要求）。
     *
     * @throws SkillParseException 任何违反规范的地方——错误信息面向安装者，直接展示。
     */
    fun parse(content: String, declaredDirName: String? = null): ParsedSkill
}

/** frontmatter 解析结果：字段集 + 指令正文（SKILL.md 去掉 frontmatter 后的 Markdown）。 */
data class ParsedSkill(
    val name: String,
    val description: String,
    val license: String?,
    val compatibility: String?,
    val metadata: Map<String, String>,
    val allowedTools: List<String>,
    val body: String,
)

/**
 * 默认实现：严格模式——标准说"必须"的（name/description 规则、保留词）一律拒绝。
 */
class DefaultSkillParser : SkillParser {

    override fun parse(content: String, declaredDirName: String?): ParsedSkill {
        val lines = content.normalizeLineSeparators()
        if (lines.firstOrNull()?.trimEnd() != FRONTMATTER_DELIMITER) {
            throw SkillParseException("SKILL.md 必须以 YAML frontmatter 开头（第一行应为 ---）")
        }

        val closing = lines.drop(1).indexOfFirst { it.trimEnd() == FRONTMATTER_DELIMITER }
        if (closing == -1) {
            throw SkillParseException("frontmatter 未闭合：缺少第二个 --- 分隔行")
        }

        val fields = parseFields(lines.subList(1, closing + 1))
        val body = lines.drop(closing + 2).joinToString("\n").trim()

        val name = fields.string(NAME) ?: throw SkillParseException("缺少必填字段 name")
        val description = fields.string(DESCRIPTION) ?: throw SkillParseException("缺少必填字段 description")

        validateName(name)
        validateDescription(description)
        if (declaredDirName != null && declaredDirName != name) {
            throw SkillParseException("name 与目录名不一致：name=$name，目录=$declaredDirName（标准要求两者相同）")
        }

        return ParsedSkill(
            name = name,
            description = description,
            license = fields.string(LICENSE),
            compatibility = fields.string(COMPATIBILITY),
            metadata = fields.nested(METADATA),
            allowedTools = fields.list(ALLOWED_TOOLS),
            body = body,
        )
    }

    // ───────────────────────── 校验规则（照抄规范） ─────────────────────────

    private fun validateName(name: String) {
        if (name.isEmpty()) throw SkillParseException("name 不能为空")
        if (name.length > MAX_NAME_LENGTH) throw SkillParseException("name 超过 $MAX_NAME_LENGTH 字符上限（实际 ${name.length}）")
        if (!NAME_PATTERN.matches(name)) {
            throw SkillParseException("name 只能包含小写字母、数字和连字符，且不能以连字符开头/结尾或含连续连字符：$name")
        }
        if ('<' in name || '>' in name) throw SkillParseException("name 不能包含 XML 标签字符")
        val hit = RESERVED_NAMES.firstOrNull { name == it || name.startsWith("$it-") }
        if (hit != null) throw SkillParseException("name 含保留词：$hit")
    }

    private fun validateDescription(description: String) {
        if (description.isBlank()) throw SkillParseException("description 不能为空")
        if (description.length > MAX_DESCRIPTION_LENGTH) {
            throw SkillParseException("description 超过 $MAX_DESCRIPTION_LENGTH 字符上限（实际 ${description.length}）")
        }
        if ('<' in description || '>' in description) throw SkillParseException("description 不能包含 XML 标签字符")
    }

    // ───────────────────────── frontmatter 解析 ─────────────────────────

    /** 中间结构：顶层标量 + 嵌套子表 + 列表共存。 */
    private class FieldBag {
        val scalars = LinkedHashMap<String, String>()
        val nested = LinkedHashMap<String, Map<String, String>>()
        val lists = LinkedHashMap<String, List<String>>()

        fun string(key: String): String? = scalars[key]
        fun nested(key: String): Map<String, String> = nested[key] ?: emptyMap()
        fun list(key: String): List<String> = lists[key] ?: emptyList()
    }

    private fun parseFields(lines: List<String>): FieldBag {
        val bag = FieldBag()
        var currentKey: String? = null

        for (raw in lines) {
            if (raw.isBlank() || raw.trimStart().startsWith("#")) continue
            val indent = raw.length - raw.trimStart().length
            val line = raw.trim()

            if (indent == 0) {
                val colon = line.indexOf(':')
                if (colon == -1) throw SkillParseException("frontmatter 行缺少冒号：$line")
                val key = line.substring(0, colon).trim()
                val value = line.substring(colon + 1).trim()
                if (key.isEmpty()) throw SkillParseException("frontmatter 存在空键名：$line")
                currentKey = key
                if (value.isEmpty()) {
                    // 值为空：可能是嵌套表或列表项的头部，先占位
                    bag.scalars[key] = ""
                } else if (value.startsWith("[") && value.endsWith("]")) {
                    bag.lists[key] = value.unbracket()
                    bag.scalars.remove(key)
                } else {
                    bag.scalars[key] = value.stripQuotes()
                }
                continue
            }

            // 缩进行：列表项或嵌套子键
            val parent = currentKey ?: throw SkillParseException("frontmatter 缩进行缺少所属键：$line")
            if (line.startsWith("- ")) {
                val item = line.removePrefix("- ").trim().stripQuotes()
                bag.lists[parent] = bag.lists[parent].orEmpty() + item
                bag.scalars.remove(parent)
            } else {
                val colon = line.indexOf(':')
                if (colon == -1) throw SkillParseException("frontmatter 行缺少冒号：$line")
                val subKey = line.substring(0, colon).trim()
                val subValue = line.substring(colon + 1).trim().stripQuotes()
                val existing = bag.nested[parent].orEmpty()
                bag.nested[parent] = existing + (subKey to subValue)
                bag.scalars.remove(parent)
            }
        }
        return bag
    }

    private fun String.normalizeLineSeparators(): List<String> =
        replace("\r\n", "\n").replace('\r', '\n').split('\n')

    private fun String.stripQuotes(): String {
        if (length >= 2 && ((startsWith("\"") && endsWith("\"")) || (startsWith("'") && endsWith("'")))) {
            return substring(1, length - 1)
        }
        return this
    }

    private fun String.unbracket(): List<String> =
        substring(1, length - 1).split(',').map { it.trim().stripQuotes() }.filter { it.isNotEmpty() }

    private companion object {
        const val FRONTMATTER_DELIMITER = "---"
        const val MAX_NAME_LENGTH = 64
        const val MAX_DESCRIPTION_LENGTH = 1024
        const val NAME = "name"
        const val DESCRIPTION = "description"
        const val LICENSE = "license"
        const val COMPATIBILITY = "compatibility"
        const val METADATA = "metadata"
        const val ALLOWED_TOOLS = "allowed-tools"
        val NAME_PATTERN = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")
        val RESERVED_NAMES = setOf("anthropic", "claude")
    }
}
