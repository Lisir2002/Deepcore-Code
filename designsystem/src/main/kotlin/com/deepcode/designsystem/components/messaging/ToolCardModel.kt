package com.deepcode.designsystem.components.messaging

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.ui.graphics.vector.ImageVector
import com.deepcode.core.model.ToolKind
import com.deepcode.core.model.ToolSpec

/**
 * 工具卡静态注册表（§6.8.2）：工具名 → (图标, 人类标题, 参数摘要提取器) 的单一映射。
 * 与 `core:mcp` 工具注册同构；MCP 外来工具走灰色兜底 + 名称字面量。
 */
data class ToolCardSpec(
    val icon: ImageVector,
    /** 人类标题模式（§6.8.2：读=读取+路径副标题等）。返回多行时以换行区分标题/副标题。 */
    val title: (name: String) -> String = { it },
    /** 关键参数摘要提取器：把参数摘要压缩成一行关键信息。 */
    val summary: (argumentsSummary: String) -> String = { it },
)

/**
 * 默认（空）工具卡注册表（§6.8.2）：未接业务工具注册表时的兜底。
 * 所有工具都走 `Unknown` 灰色 Build 图标 + 名称字面量，保证渲染不崩、语义可读。
 */
val defaultRegistry: ToolCardRegistry = ToolCardRegistry.empty()

class ToolCardRegistry private constructor(
    private val specs: Map<String, ToolCardSpec>,
) {
    operator fun get(name: String): ToolCardSpec = specs[name] ?: Unknown

    companion object {
        /** 空注册表（兜底）：所有工具走灰色 `Unknown` 图标。 */
        fun empty(): ToolCardRegistry = ToolCardRegistry(emptyMap())

        /** MCP 外来工具灰色兜底：名称字面量 + 通用图标。 */
        private val Unknown = ToolCardSpec(
            icon = Icons.Filled.Build,
            title = { it },
            summary = { it },
        )

        private val kindIcon: Map<ToolKind, ImageVector> = mapOf(
            ToolKind.READ to Icons.Filled.Description,
            ToolKind.WRITE to Icons.Filled.Edit,
            ToolKind.SEARCH to Icons.Filled.Search,
            ToolKind.WEB to Icons.Filled.Public,
            ToolKind.EXECUTE to Icons.Filled.Terminal,
        )

        /** 由 tool spec 的 kind 派生默认 spec（无注册表命中也至少按 kind 给图标）。 */
        fun fromKind(kind: ToolKind): ToolCardSpec =
            ToolCardSpec(icon = kindIcon[kind] ?: Icons.Filled.Build)

        /**
         * 从工具 spec 列表构建注册表。命中的工具名用注册表标题；未命中的用 kind 兜底。
         * key = 工具名。
         */
        fun build(tools: List<ToolSpec>): ToolCardRegistry {
            val specs = tools.associate { tool ->
                tool.name to ToolCardSpec(
                    icon = fromKind(tool.kind).icon,
                    title = { tool.title ?: tool.name },
                    summary = { it },
                )
            }
            return ToolCardRegistry(specs)
        }
    }
}