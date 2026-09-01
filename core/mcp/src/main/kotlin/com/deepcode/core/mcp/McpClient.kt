package com.deepcode.core.mcp

import com.deepcode.core.logging.Log
import com.deepcode.core.logging.LogCategory
import com.deepcode.core.logging.LogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 对 MCP 传输的薄封装（核心:mcp 与上层只依赖这个接口）。
 *
 * 这样 [McpServerManager] / [McpToolBridge] 可在不拉起真实网络的情况下用假实现测试；
 * 真实实现 [HttpJsonRpcMcpClient] 才 import okhttp——把传输依赖收敛到这一处。
 * 未来换官方 Kotlin SDK 时，只需新增一个实现本接口的 Client 类。
 */
interface McpClient {
    /** server 标识（与配置 id 一致），用于工具命名空间。 */
    val serverName: String

    /** 建立连接（含 MCP initialize 握手 + initialized 通知）。 */
    suspend fun connect()

    /** 列出该 server 暴露的工具。 */
    suspend fun listTools(): List<McpToolDef>

    /** 调用某个工具，返回 MCP 原始结果。 */
    suspend fun callTool(name: String, arguments: JsonObject): McpCallToolResult

    /**
     * 注册 tools/list changed 通知回调。server 动态增删工具时，manager 据此重新拉取清单。
     * 非 suspend：实现内部自行切换协程上下文。
     */
    fun setToolsChangedHandler(handler: () -> Unit)

    /** 关闭连接。 */
    suspend fun close()
}

/**
 * 基于 OkHttp 的 Streamable HTTP MCP 客户端（协议级兼容，对齐 MCP 规范 2025-11-05）。
 *
 * 实现 JSON-RPC 2.0 的请求/响应：initialize → initialized 通知 → tools/list → tools/call。
 * 响应优先按 `application/json` 解析；若 server 走 SSE（`text/event-stream`）则抽取 data 行。
 * list_changed 通知在 M1 由上层轮询/手动触发（真实 SSE 长连接订阅留到 M2）。
 */
class HttpJsonRpcMcpClient(
    override val serverName: String,
    private val url: String,
    private val headers: Map<String, String> = emptyMap(),
    private val http: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : McpClient {

    private var sessionId: String? = null
    private var handler: (() -> Unit)? = null
    private var nextId = 0
    private val ownClient = http === DEFAULT_HTTP

    override suspend fun connect() {
        Log.log(LogLevel.INFO, LogCategory.OPERATION_MCP, "McpClient", "server $serverName 开始 initialize 握手")
        rpc(
            "initialize",
            buildJsonObject {
                put("protocolVersion", "2025-11-05")
                putJsonObject("capabilities") {}
                putJsonObject("clientInfo") {
                    put("name", "deepcore-code")
                    put("version", "0.1.0")
                }
            },
        )
        rpcNotification("notifications/initialized", buildJsonObject {})
        Log.log(LogLevel.INFO, LogCategory.OPERATION_MCP, "McpClient", "server $serverName 握手完成")
    }

    override suspend fun listTools(): List<McpToolDef> {
        val res = rpc("tools/list", buildJsonObject {})
        return json.decodeFromJsonElement(McpListToolsResult.serializer(), res.getValue("result")).tools
    }

    override suspend fun callTool(name: String, arguments: JsonObject): McpCallToolResult {
        val startedAt = System.currentTimeMillis()
        return try {
            val res = rpc(
                "tools/call",
                buildJsonObject {
                    put("name", name)
                    put("arguments", arguments)
                },
            )
            val result = json.decodeFromJsonElement(McpCallToolResult.serializer(), res.getValue("result"))
            Log.log(
                LogLevel.DEBUG, LogCategory.OPERATION_MCP, "McpClient",
                "server $serverName tools/call $name 成功（${System.currentTimeMillis() - startedAt}ms）",
            )
            result
        } catch (t: Throwable) {
            Log.log(
                LogLevel.ERROR, LogCategory.OPERATION_MCP, "McpClient",
                "server $serverName tools/call $name 失败（${System.currentTimeMillis() - startedAt}ms）：${t.message}", t,
            )
            throw t
        }
    }

    override fun setToolsChangedHandler(handler: () -> Unit) {
        this.handler = handler
    }

    /** 由传输层的 tools/list_changed 通知调用。 */
    internal fun notifyToolsChanged() = handler?.invoke()

    override suspend fun close() {
        if (ownClient) http.dispatcher.executorService.shutdown()
        sessionId = null
    }

    private suspend fun rpc(method: String, params: JsonObject): JsonObject {
        val id = ++nextId
        return rawCall(
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", id)
                put("method", method)
                if (params.isNotEmpty()) put("params", params)
            },
            expectResult = true,
        )
    }

    private suspend fun rpcNotification(method: String, params: JsonObject) {
        rawCall(
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("method", method)
                if (params.isNotEmpty()) put("params", params)
            },
            expectResult = false,
        )
    }

    private suspend fun rawCall(payload: JsonObject, expectResult: Boolean): JsonObject =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url)
                .addHeader("Accept", "application/json, text/event-stream")
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .apply { sessionId?.let { header("Mcp-Session-Id", it) } }
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .build()

            http.newCall(request).execute().use { resp ->
                resp.header("Mcp-Session-Id")?.let { sessionId = it }
                // 通知类请求（notifications/initialized 等）服务端通常返回 202 + 空 body，
                // 不解析 JSON，直接视为成功。
                if (!expectResult) return@use buildJsonObject {}

                val raw = resp.body?.string() ?: ""
                val text = if (raw.contains("data:") || raw.startsWith("event:")) {
                    extractSseData(raw)
                } else {
                    raw
                }
                if (text.isBlank()) throw McpClientException("MCP 响应为空")
                val obj = json.parseToJsonElement(text) as JsonObject
                obj["error"]?.let { err ->
                    val msg = (err as JsonObject)["message"]?.toString()?.trim('"') ?: "unknown"
                    throw McpClientException("MCP error: $msg")
                }
                obj["result"] ?: throw McpClientException("MCP 响应缺少 result：$text")
                obj
            }
        }

    /**
     * 从 SSE 文本中提取**最后一个完整事件**的 data 内容。
     *
     * SSE 规范（EventSource）：一个事件由一行或多行 `data:` 组成，多行 data
     * 用换行拼接成事件体；事件之间以空行分隔。旧实现只取最后一行 `data:`，
     * 遇到分块的 MCP 响应会丢失前几块。这里按事件边界切分、把多行拼回一条，
     * 取最后一个完整事件（MCP 响应一次只发一条 JSON-RPC 结果）。
     */
    private fun extractSseData(raw: String): String {
        val events = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        raw.lineSequence().forEach { line ->
            if (line.isBlank()) {
                if (current.isNotEmpty()) {
                    events += current
                    current = mutableListOf()
                }
            } else if (line.startsWith("data:")) {
                // 规范：`data: ` 后紧跟一个空格时该空格不属于内容，去掉它
                current += line.removePrefix("data:").trim()
            }
            // 其余字段行（event:/id:/retry: 及以 : 开头的注释）不参与事件体
        }
        if (current.isNotEmpty()) events += current
        return events.lastOrNull()?.joinToString("\n") ?: raw
    }

    companion object {
        private val DEFAULT_HTTP = OkHttpClient()
    }
}

/** MCP 客户端层异常（握手/调用/协议错误），不穿透 Agent 主循环。 */
class McpClientException(message: String) : RuntimeException(message)
