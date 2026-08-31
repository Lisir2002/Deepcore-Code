package com.deepcode.core.mcp

/**
 * 一个 MCP Server 的连接配置。
 *
 * 由 :app 从持久化层（M1 暂用内存/设置页，后续落数据层）加载后注入 [McpServerManager]。
 * 本层（core:mcp）只认这份配置，不关心它从哪来——换存储不影响桥接逻辑。
 */
data class McpServerConfig(
    /** 稳定标识，用作工具命名空间（工具名 = "$id__${toolName}"）。 */
    val id: String,
    /** 展示名（UI 与日志用）。 */
    val displayName: String,
    /** 传输配置。M1 只实现 http；stdio 等 M2 ProotSandbox 就绪后补（见 docs/TOOLS_SKILLS.md §3）。 */
    val transport: McpTransport,
    /**
     * 是否受信任。受信任 server 的 annotations 允许降档风险（readOnly→READ_ONLY 等）；
     * 未受信任一律 NETWORK（规范要求 annotations 不可信，详见 TOOLS_SKILLS.md §4）。
     */
    val trusted: Boolean = false,
)

/** MCP 传输配置。Android 约束下 stdio 受限，M1 仅 http。 */
sealed interface McpTransport {
    /** Streamable HTTP（云端 MCP Server 主流形态）。 */
    data class Http(
        val url: String,
        val headers: Map<String, String> = emptyMap(),
    ) : McpTransport
    // M2: data class Stdio(val command: String, val args: List<String>) : McpTransport
}
