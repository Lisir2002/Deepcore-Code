package com.deepcode.core.mcp

import com.deepcode.core.agent.spi.Tool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * 一组 MCP Server 的生命周期与工具聚合器（核心:mcp 对上层暴露的唯一入口）。
 *
 * 职责（见 docs/TOOLS_SKILLS.md §3）：
 *  - [connectAll]：逐个 connect，单点失败只记入 [state] 不影响其余 server；
 *  - 工具快照：listTools 结果映射成 [McpTool]，按 server 缓存；
 *  - listChanged：注册回调，触发时重新拉取该 server 清单（下一 turn 生效，不击穿 prompt cache）；
 *  - [tools]：返回所有已连 server 的工具扁平列表，直接喂给统一 ToolRegistry。
 *
 * @param configs       要连接的 server 配置
 * @param clientFactory 按配置生产 [McpClient]（真实用 RealMcpClient；测试用 FakeMcpClient）
 * @param scope         生命周期协程作用域（连接/刷新在其内启动）
 */
class McpServerManager(
    private val configs: List<McpServerConfig>,
    private val clientFactory: (McpServerConfig) -> McpClient,
    private val scope: CoroutineScope,
) {
    private val clients = ConcurrentHashMap<String, McpClient>()
    private val toolsByServer = ConcurrentHashMap<String, List<McpTool>>()
    private val errors = ConcurrentHashMap<String, String>()

    /** 每个 server 的实时状态（UI 展示用）。 */
    val state: Map<String, McpServerStatus>
        get() = configs.associate { cfg ->
            cfg.id to McpServerStatus(
                connected = clients[cfg.id] != null,
                toolCount = toolsByServer[cfg.id]?.size ?: 0,
                error = errors[cfg.id],
            )
        }

    /** 连接所有配置里的 server。 */
    suspend fun connectAll() {
        for (cfg in configs) {
            runCatching {
                val client = clientFactory(cfg)
                client.connect()
                client.setToolsChangedHandler { scope.launch { refresh(cfg.id) } }
                clients[cfg.id] = client
                refresh(cfg.id)
            }.onFailure { errors[cfg.id] = it.message ?: (it::class.simpleName ?: "unknown") }
        }
    }

    private suspend fun refresh(serverId: String) {
        val client = clients[serverId] ?: return
        val cfg = configs.firstOrNull { it.id == serverId } ?: return
        val mcpTools = client.listTools()
        toolsByServer[serverId] = mcpTools.map { def ->
            McpTool(McpToolBridge.toToolSpec(cfg, def), client, def.name)
        }
    }

    /** 全部已桥接工具（扁平列表），直接注册进统一 ToolRegistry。 */
    fun tools(): List<Tool> = toolsByServer.values.flatten()

    /** 主动触发某 server 的清单刷新（如用户改了 server 配置）。 */
    fun reload(serverId: String) {
        scope.launch { refresh(serverId) }
    }

    /** 断开并清理所有连接。 */
    suspend fun disconnectAll() {
        clients.values.forEach { runCatching { it.close() } }
        clients.clear()
        toolsByServer.clear()
        errors.clear()
    }
}

data class McpServerStatus(
    val connected: Boolean,
    val toolCount: Int,
    val error: String?,
)
