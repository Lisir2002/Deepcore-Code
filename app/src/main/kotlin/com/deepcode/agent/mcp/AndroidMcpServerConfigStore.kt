package com.deepcode.agent.mcp

import android.content.Context
import com.deepcode.core.mcp.McpServerConfig
import com.deepcode.core.mcp.McpServerConfigStore
import com.deepcode.core.mcp.McpTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** 落盘用的扁平结构；与 [McpServerConfig] 解耦，避免污染 core:mcp 的类型。 */
@Serializable
private data class StoredServer(
    val id: String,
    val displayName: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val trusted: Boolean = false,
)

@Serializable
private data class StoredConfig(val servers: List<StoredServer> = emptyList())

/**
 * [McpServerConfigStore] 的 Android 实现：配置存到 `filesDir/mcp/servers.json`。
 *
 * 设计要点：
 *  - 构造期**同步**读取小 JSON（启动期一次性，失败降级空列表），这样 Koin 装配
 *    [McpServerManager] 时拿到的 `current()` 就是非阻塞快照，无需在 module 里 suspend；
 *  - `load()`/`save()` 仍走 [Dispatchers.IO] 异步读写，供设置页刷新与持久化；
 *  - 读写都经过本地 DTO 映射，core:mcp 的 [McpServerConfig] 保持零序列化耦合。
 */
class AndroidMcpServerConfigStore(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = true },
) : McpServerConfigStore {

    private val file = File(context.filesDir, "mcp/servers.json")

    @Volatile
    private var cache: List<McpServerConfig> = readSync()

    /** 构造期同步读；文件缺失或损坏都降级为空列表，不阻断启动。 */
    private fun readSync(): List<McpServerConfig> {
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString(StoredConfig.serializer(), file.readText())
                .servers.map { it.toConfig() }
        }.getOrDefault(emptyList())
    }

    /** 当前内存快照（非阻塞），装配与 UI 即时读取。 */
    override fun current(): List<McpServerConfig> = cache

    /** 重新从磁盘读取并刷新快照（设置页"刷新"时用）。 */
    override suspend fun load(): List<McpServerConfig> = withContext(Dispatchers.IO) {
        val result = runCatching {
            if (!file.exists()) return@runCatching emptyList()
            json.decodeFromString(StoredConfig.serializer(), file.readText())
                .servers.map { it.toConfig() }
        }.getOrDefault(emptyList())
        cache = result
        result
    }

    /** 整体覆盖写入并刷新快照。 */
    override suspend fun save(configs: List<McpServerConfig>) = withContext(Dispatchers.IO) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(
                json.encodeToString(
                    StoredConfig.serializer(),
                    StoredConfig(configs.map { it.toStored() }),
                ),
            )
        }
        cache = configs
    }

    private fun StoredServer.toConfig() = McpServerConfig(
        id = id,
        displayName = displayName,
        transport = McpTransport.Http(url = url, headers = headers),
        trusted = trusted,
    )

    private fun McpServerConfig.toStored() = StoredServer(
        id = id,
        displayName = displayName,
        url = (transport as? McpTransport.Http)?.url ?: "",
        headers = (transport as? McpTransport.Http)?.headers ?: emptyMap(),
        trusted = trusted,
    )
}
