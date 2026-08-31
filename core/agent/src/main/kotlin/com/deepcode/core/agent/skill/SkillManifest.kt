package com.deepcode.core.agent.skill

/**
 * 一个 Agent Skill 的元数据（对齐 Agent Skills 开放标准，见 docs/TOOLS_SKILLS.md §5）。
 *
 * 标准规定 SKILL.md 的 YAML frontmatter 只有两个必填字段（name/description），
 * 四个可选字段（license/compatibility/metadata/allowed-tools）。
 * 本类是 frontmatter 的 Kotlin 映射 + 加载时的文件位置信息。
 */
data class SkillManifest(
    /** 技能 ID：≤64 字符，仅小写字母/数字/连字符，须与目录名一致。 */
    val name: String,
    /** 做什么 + 何时用（模型据此决定是否触发加载）。≤1024 字符。 */
    val description: String,
    val license: String? = null,
    /** 环境要求说明（如 "requires git and python3"）。 */
    val compatibility: String? = null,
    /** 自定义键值对（author、version…）。 */
    val metadata: Map<String, String> = emptyMap(),
    /** 预批工具清单（实验性字段；M1 仅解析展示，不自动放权）。 */
    val allowedTools: List<String> = emptyList(),
    /** skill 目录（相对工作区根），如 ".deepcode/skills/pdf-processing"。 */
    val dirPath: String,
    /** SKILL.md 的完整路径，供模型用 read 工具加载 L2 指令。 */
    val bodyPath: String,
    /** 是否捆绑可执行脚本（scripts/ 目录非空）——安装审计的提示项。 */
    val hasScripts: Boolean = false,
)

/** 单个 skill 加载失败的原因（不炸整体，聚合报告）。 */
data class SkillLoadError(
    val dirPath: String,
    val reason: String,
)

/** 一次扫描的完整结果：可用清单 + 失败明细。 */
data class SkillLoadResult(
    val skills: List<SkillManifest>,
    val errors: List<SkillLoadError>,
) {
    companion object {
        val EMPTY = SkillLoadResult(emptyList(), emptyList())
    }
}

/** frontmatter 违反 Agent Skills 规范时抛出，message 即人可读原因。 */
class SkillParseException(message: String) : IllegalArgumentException(message)
