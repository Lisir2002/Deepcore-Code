package com.deepcode.core.mcp

/**
 * MCP server 配置持久化抽象（纯 Kotlin，零 Android 依赖）。
 *
 * [McpServerManager] 只认 [McpServerConfig]，不关心它从哪来；本接口把"配置从哪读、
 * 写到哪"这件事收口到 [core:mcp] 以外。
 *
 * 依赖方向刻意保持 `feature:settings → core:mcp` 单向：
 *   · 接口定义在本模块（feature 只依赖它，拿不到 Android Context）；
 *   · [androidx.content.Context] 读写文件的实现放在 `:app`（见 AndroidMcpServerConfigStore）。
 *
 * 设计取舍（见 docs/TOOLS_SKILLS.md §3 / PLAN T5）：M1 用 JSON 文件落地，
 * 后续可平滑切到 SQLite（[core:data] 的 TableModule），上层一行不动。
 */
interface McpServerConfigStore {

    /** 当前内存快照（非阻塞、不触发 IO）；返回最近一次读/写后的配置。 */
    fun current(): List<McpServerConfig>

    /** 读取全部已配置 server；无配置返回空列表（不抛异常）。 */
    suspend fun load(): List<McpServerConfig>

    /** 整体覆盖写入配置列表。 */
    suspend fun save(configs: List<McpServerConfig>)
}
