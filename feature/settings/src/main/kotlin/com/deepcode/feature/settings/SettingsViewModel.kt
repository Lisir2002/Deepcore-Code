package com.deepcode.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepcode.core.logging.Log
import com.deepcode.core.logging.LogCategory
import com.deepcode.core.logging.LogLevel
import com.deepcode.core.mcp.McpServerConfig
import com.deepcode.core.mcp.McpServerConfigStore
import com.deepcode.core.mcp.McpServerManager
import com.deepcode.core.mcp.McpTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 设置页 ViewModel：把 [McpServerConfigStore]（持久化）与 [McpServerManager]（运行时连接）
 * 桥接成 UI 状态。
 *
 * 关键不变式：任何增删改都先落到 manager（实时生效），再 `store.save(manager.snapshotConfigs())`
 * 持久化——保证内存与磁盘最终一致，且 store 永远持有 manager 的权威快照。
 */
class SettingsViewModel(
    private val store: McpServerConfigStore,
    private val manager: McpServerManager,
    val loggingActions: LoggingActions,
) : ViewModel() {

    private val _servers = MutableStateFlow<List<McpServerUiModel>>(emptyList())
    val servers: StateFlow<List<McpServerUiModel>> = _servers.asStateFlow()

    init {
        refreshFromStore()
    }

    /** 用 store + manager 的实时状态重建 UI 列表。 */
    fun refreshFromStore() {
        val statuses = manager.state
        _servers.update {
            store.current().map { cfg ->
                val st = statuses[cfg.id]
                McpServerUiModel(
                    id = cfg.id,
                    displayName = cfg.displayName,
                    url = (cfg.transport as? McpTransport.Http)?.url ?: "",
                    trusted = cfg.trusted,
                    connected = st?.connected ?: false,
                    toolCount = st?.toolCount ?: 0,
                    error = st?.error,
                )
            }
        }
    }

    fun addServer(rawName: String, rawUrl: String, trusted: Boolean) {
        val url = rawUrl.trim()
        // 协议校验：只放行 http/https，拒绝 file://、content://、javascript:// 等
        // 会被 OkHttp 接受的异常协议，防止 Agent 被诱导向本地文件或任意目标发请求。
        if (!isValidMcpUrl(url)) {
            Log.log(LogLevel.WARN, LogCategory.OPERATION_USER, "Settings", "MCP server URL 非法被拒绝：$url")
            return
        }
        val id = rawName.trim().ifBlank { url.hashCode().toString(36) }
        val config = McpServerConfig(
            id = id,
            displayName = rawName.trim().ifBlank { id },
            transport = McpTransport.Http(url = url),
            trusted = trusted,
        )
        Log.log(
            LogLevel.INFO, LogCategory.OPERATION_USER, "Settings",
            "新增/编辑 MCP server ${config.displayName}（trusted=$trusted）",
        )
        viewModelScope.launch {
            manager.addServer(config)
            store.save(manager.snapshotConfigs())
            refreshFromStore()
        }
    }

    fun removeServer(id: String) {
        Log.log(LogLevel.INFO, LogCategory.OPERATION_USER, "Settings", "删除 MCP server $id")
        viewModelScope.launch {
            manager.removeServer(id)
            store.save(manager.snapshotConfigs())
            refreshFromStore()
        }
    }

    fun setTrusted(id: String, trusted: Boolean) {
        val current = store.current().firstOrNull { it.id == id } ?: return
        Log.log(LogLevel.INFO, LogCategory.OPERATION_USER, "Settings", "切换 MCP server $id 信任状态 → $trusted")
        viewModelScope.launch {
            manager.updateServer(current.copy(trusted = trusted))
            store.save(manager.snapshotConfigs())
            refreshFromStore()
        }
    }

    fun reconnectAll() {
        Log.log(LogLevel.INFO, LogCategory.OPERATION_USER, "Settings", "重连全部 MCP server")
        viewModelScope.launch {
            manager.reconnectAll()
            refreshFromStore()
        }
    }

    /**
     * MCP server URL 合法性校验。
     *
     * 只接受带 http/https 协议且含 host 的 URL，其余（file://、content://、
     * javascript://、无协议裸路径等）一律拒绝。用 [java.net.URI] 解析，
     * 不依赖 Android API，便于在 JVM 上单测。
     */
    internal fun isValidMcpUrl(rawUrl: String): Boolean {
        val url = rawUrl.trim()
        if (url.isBlank()) return false
        return runCatching {
            val uri = java.net.URI(url)
            (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()
        }.getOrDefault(false)
    }
}

/** 单个 MCP server 的设置页展示模型。 */
data class McpServerUiModel(
    val id: String,
    val displayName: String,
    val url: String,
    val trusted: Boolean,
    val connected: Boolean,
    val toolCount: Int,
    val error: String?,
)
