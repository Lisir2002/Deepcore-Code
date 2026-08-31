package com.deepcode.core.agent.skill

/**
 * 技能注入器（L1：常驻元数据段）。
 *
 * Agent Skills 的"渐进披露"三层的**第一层**：把所有已装 skill 的 name + description
 * 浓缩成一段稳定文本，塞进 system prompt。目的不是一次性把全部技能正文灌进去，
 * 而是让模型在任务匹配时**知道该去读哪个 SKILL.md**（L2），进而按需取资源（L3）。
 *
 * 设计要点（见 docs/TOOLS_SKILLS.md §5 / §6）：
 *  - 段内容只依赖"装了哪些 skill"，与扫描顺序无关（[SkillLoader] 已按 name 排序）；
 *  - 明确告诉模型：触发时请用 read 工具加载 [SkillManifest.bodyPath] 获取完整指令；
 *  - 不伪装成 Tool——skill 是知识而非能力，没有 function-calling 入口；
 *  - 没有可用 skill 时返回空串，主循环拼接 system prompt 时直接跳过，零噪声。
 */
interface SkillInjector {
    /** 生成可追加进 system prompt 的 L1 技能段；无技能时返回空串。 */
    fun buildSkillSection(skills: List<SkillManifest>): String
}

class DefaultSkillInjector : SkillInjector {

    override fun buildSkillSection(skills: List<SkillManifest>): String {
        if (skills.isEmpty()) return ""
        val lines = buildList<String> {
            add(SKILL_SECTION_HEADER)
            for (skill in skills) {
                // 格式固定：<name> — <description>。模型据此匹配任务并决定触发。
                add("- ${skill.name} — ${skill.description}")
            }
            add(SKILL_SECTION_FOOTER)
        }
        return lines.joinToString("\n")
    }

    private companion object {
        const val SKILL_SECTION_HEADER = """# Available Skills
当任务与下列技能的描述匹配时，请用 read 工具加载对应 SKILL.md 获取完整操作指引，再继续。"""

        const val SKILL_SECTION_FOOTER = "（以上仅为技能清单；完整指令以对应 SKILL.md 正文为准。）"
    }
}
