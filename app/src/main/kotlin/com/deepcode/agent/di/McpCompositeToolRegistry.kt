package com.deepcode.agent.di

import com.deepcode.core.agent.spi.Tool
import com.deepcode.core.agent.spi.ToolRegistry
import com.deepcode.core.mcp.McpServerManager
import com.deepcode.core.model.ToolOrigin
import com.deepcode.core.model.ToolSpec

/**
 * 把"内置工具"与"MCP 工具"聚合成一个 [ToolRegistry]。
 *
 * 为什么需要它：[McpServerManager.tools()] 在 connectAll 之后才有内容，而内置工具在
 * Koin 装配时就注册好了。两者生命周期不同，不能简单合并进同一个 [ToolRegistry]。
 * 这里做实时聚合——每次调用都现查 manager 当前工具，因此 MCP 的 listChanged
 * 在下一 turn 自然反映（见 docs/TOOLS_SKILLS.md §6 的快照语义）。
 *
 * 排序沿用 T3 的稳定规则：BUILTIN 优先，再按 name 字典序，避免工具清单抖动击穿
 * prompt cache。
 */
class McpCompositeToolRegistry(
    private val builtin: ToolRegistry,
    private val manager: McpServerManager,
) : ToolRegistry {

    override fun register(tool: Tool) = builtin.register(tool)

    override fun unregister(name: String) = builtin.unregister(name)

    override fun get(name: String): Tool? =
        builtin[name] ?: manager.tools().firstOrNull { it.spec.name == name }

    override fun all(): List<Tool> = builtin.all() + manager.tools()

    override fun specs(): List<ToolSpec> =
        (builtin.specs() + manager.tools().map { it.spec })
            .sortedWith(compareBy({ it.origin != ToolOrigin.BUILTIN }, { it.name }))
}
