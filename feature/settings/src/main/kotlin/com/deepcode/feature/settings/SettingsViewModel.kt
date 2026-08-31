package com.deepcode.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
        if (url.isBlank()) return
        val id = rawName.trim().ifBlank { url.hashCode().toString(36) }
        val config = McpServerConfig(
            id = id,
            displayName = rawName.trim().ifBlank { id },
            transport = McpTransport.Http(url = url),
            trusted = trusted,
        )
        viewModelScope.launch {
            manager.addServer(config)
            store.save(manager.snapshotConfigs())
            refreshFromStore()
        }
    }

    fun removeServer(id: String) {
        viewModelScope.launch {
            manager.removeServer(id)
            store.save(manager.snapshotConfigs())
            refreshFromStore()
        }
    }

    fun setTrusted(id: String, trusted: Boolean) {
        val current = store.current().firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            manager.updateServer(current.copy(trusted = trusted))
            store.save(manager.snapshotConfigs())
            refreshFromStore()
        }
    }

    fun reconnectAll() {
        viewModelScope.launch {
            manager.reconnectAll()
            refreshFromStore()
        }
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
