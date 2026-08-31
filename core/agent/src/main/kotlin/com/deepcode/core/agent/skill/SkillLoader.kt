package com.deepcode.core.agent.skill

import com.deepcode.core.agent.spi.Workspace

/**
 * 技能加载器：扫描 skill 根目录，解析每个含 SKILL.md 的子目录。
 *
 * 标准的目录约定（见 docs/TOOLS_SKILLS.md §5.3）：
 *   <root>/<skill-name>/SKILL.md     —— 必需
 *   <root>/<skill-name>/scripts/    —— 可选（存在则 hasScripts=true）
 *
 * 依赖 [Workspace] 抽象而非文件系统：项目级 skill 在工作区内（可远端），
 * 用户级 skill 由 :app 提供一个指向私有目录的 Workspace 实现——本层无感。
 */
interface SkillLoader {
    suspend fun load(): SkillLoadResult
}

/**
 * 基于 Workspace 的默认实现。
 *
 * 单个 skill 解析失败只记录 [SkillLoadError]，不影响其余 skill——
 * 安装包质量参差是常态，一个坏包不能拖垮整个技能面。
 */
class WorkspaceSkillLoader(
    private val workspace: Workspace,
    /** 相对工作区根的 skill 根目录列表，按顺序扫描（如 ".deepcode/skills"、"skills"）。 */
    private val roots: List<String>,
    private val parser: SkillParser = DefaultSkillParser(),
) : SkillLoader {

    override suspend fun load(): SkillLoadResult {
        val skills = ArrayList<SkillManifest>()
        val errors = ArrayList<SkillLoadError>()
        val seen = HashSet<String>()

        for (root in roots) {
            if (!workspace.exists(root)) continue
            for (entry in workspace.list(root)) {
                if (!entry.isDirectory) continue
                val dirName = entry.path.substringAfterLast('/')
                // 按目录名去重（标准规定 name 须与目录名一致）：多 root 同名 skill 先到先得，
                // 避免不同 root 下同一 skill 被重复加载。
                if (!seen.add(dirName)) continue
                val dirPath = joinPath(root, dirName)

                val skillMdPath = joinPath(dirPath, SKILL_MD)
                if (!workspace.exists(skillMdPath)) {
                    errors.add(SkillLoadError(dirPath, "缺少 SKILL.md（标准要求目录名与 name 一致且入口文件存在）"))
                    continue
                }

                try {
                    val content = workspace.readText(skillMdPath).content
                    val parsed = parser.parse(content, declaredDirName = dirPath.substringAfterLast('/'))
                    skills.add(
                        SkillManifest(
                            name = parsed.name,
                            description = parsed.description,
                            license = parsed.license,
                            compatibility = parsed.compatibility,
                            metadata = parsed.metadata,
                            allowedTools = parsed.allowedTools,
                            dirPath = dirPath,
                            bodyPath = skillMdPath,
                            hasScripts = hasScripts(dirPath),
                        )
                    )
                } catch (e: SkillParseException) {
                    errors.add(SkillLoadError(dirPath, e.message ?: "解析失败"))
                }
            }
        }

        // 按名字稳定排序：L1 注入段的内容只取决于装了哪些 skill，与扫描顺序无关
        skills.sortBy { it.name }
        return SkillLoadResult(skills, errors)
    }

    private suspend fun hasScripts(dirPath: String): Boolean =
        runCatching { workspace.list(joinPath(dirPath, SCRIPTS_DIR)).isNotEmpty() }
            .getOrDefault(false)

    private fun joinPath(a: String, b: String): String = "$a/$b".replace("//", "/")

    private companion object {
        const val SKILL_MD = "SKILL.md"
        const val SCRIPTS_DIR = "scripts"
    }
}
