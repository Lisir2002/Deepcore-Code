package com.deepcode.agent.model

import com.deepcode.core.agent.spi.AnthropicConfig
import com.deepcode.core.agent.spi.CompletionChunk
import com.deepcode.core.agent.spi.CompletionRequest
import com.deepcode.core.agent.spi.LlmMessage
import com.deepcode.core.agent.spi.LlmRole
import com.deepcode.core.agent.spi.ModelInfo
import com.deepcode.core.agent.spi.ModelProvider
import com.deepcode.core.agent.spi.ModelTestResult
import com.deepcode.core.agent.spi.StopReasonRaw
import com.deepcode.core.model.ToolCall
import com.deepcode.core.model.ToolCallId
import com.deepcode.core.model.ToolSpec
import com.deepcode.core.model.Usage
import com.deepcode.core.model.newToolCallId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Anthropic Messages API Provider（`POST /v1/messages`，`stream=true`）。
 *
 * 协议要点（与本仓库 OpenAI 兼容实现的差异，全部烂在这一层）：
 * - 鉴权用 `x-api-key` + `anthropic-version` 头，而非 Bearer。
 * - `system` 是顶层参数，不放进 messages 数组；messages 只含 user / assistant。
 * - `max_tokens` 必填（无默认）。
 * - 工具调用返回 content blocks：`tool_use` 块 + `stop_reason=tool_use`。
 * - 流式用命名事件：`content_block_start / _delta / _stop`、`message_delta`（携带
 *   usage 与 stop_reason）、`message_stop`，而非 `data:` 稀疏块 + `[DONE]`。
 * - 工具结果以 `user` 消息内的 `tool_result` content block 表达。
 */
class AnthropicProvider(
    private val config: AnthropicConfig,
    client: OkHttpClient = defaultClient(),
) : ModelProvider {

    override val id: String = ANTHROPIC_PROVIDER_ID
    override val displayName: String =
        "Anthropic · " + config.baseUrl.substringAfter("://", config.baseUrl).removeSuffix("/")

    private val http: OkHttpClient = client
    private val json: Json = Json { ignoreUnknownKeys = true }

    override suspend fun listModels(): List<ModelInfo> {
        val req = Request.Builder()
            .url(config.modelsUrl())
            .header("x-api-key", config.apiKey)
            .header("anthropic-version", config.anthropicVersion)
            .build()
        return runCatching {
            http.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return@runCatching emptyList()
                val root = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
                root["data"]?.jsonArray?.mapNotNull { el ->
                    val id = el.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    ModelInfo(
                        id = id,
                        displayName = el.jsonObject["display_name"]?.jsonPrimitive?.contentOrNull ?: id,
                        contextWindowTokens = el.jsonObject["context_window"]?.jsonPrimitive?.intOrNull ?: 200_000,
                        maxOutputTokens = el.jsonObject["max_output_tokens"]?.jsonPrimitive?.intOrNull ?: config.maxTokens,
                    )
                } ?: emptyList()
            }
        }.getOrDefault(emptyList())
    }

    override fun supports(modelId: String): Boolean = modelId == config.model || modelId == id

    override suspend fun testModel(modelId: String): ModelTestResult {
        val start = System.nanoTime()
        return try {
            if (config.apiKey.isBlank()) {
                ModelTestResult(success = false, latencyMs = 0, message = "请先填写 API Key")
            } else {
                val payload = """{"model":${
                    kotlinx.serialization.json.JsonPrimitive(modelId).toString()
                },"max_tokens":1,"messages":[{"role":"user","content":"hi"}]}"""
                val req = Request.Builder()
                    .url(config.messagesUrl())
                    .header("Content-Type", "application/json")
                    .header("x-api-key", config.apiKey)
                    .header("anthropic-version", config.anthropicVersion)
                    .post(payload.toRequestBody(JSON))
                    .build()
                http.newCall(req).execute().use { r ->
                    val latency = (System.nanoTime() - start) / 1_000_000
                    if (r.code in 200..299) {
                        ModelTestResult(success = true, latencyMs = latency, message = "连通 · ${latency}ms")
                    } else {
                        ModelTestResult(false, latency, "HTTP ${r.code}: ${r.body?.string().orEmpty().take(160)}")
                    }
                }
            }
        } catch (e: Exception) {
            ModelTestResult(false, (System.nanoTime() - start) / 1_000_000, e.message ?: "请求失败")
        }
    }

    override fun stream(request: CompletionRequest): Flow<CompletionChunk> = channelFlow {
        val body = buildRequestBody(request)
        val httpReq = Request.Builder()
            .url(config.messagesUrl())
            .header("content-type", "application/json")
            .header("x-api-key", config.apiKey)
            .header("anthropic-version", config.anthropicVersion)
            .post(body.toRequestBody(JSON))
            .build()

        http.newCall(httpReq).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string().orEmpty().take(512)
                val retryable = response.code >= 500 || response.code == 429
                trySend(CompletionChunk.Error("HTTP ${response.code}: $errBody", retryable = retryable))
                return@use
            }

            var event = ""
            var usage = Usage()
            val toolAcc = mutableMapOf<Int, ToolUseAccumulator>()
            val pendingCalls = mutableListOf<ToolCall>()
            var stopReason: StopReasonRaw = StopReasonRaw.END_TURN
            var sawUsageEnd = false

            response.body?.charStream()?.useLines { lines ->
                for (line in lines) {
                    val trimmed = line.trimEnd()
                    if (trimmed.startsWith("event:")) {
                        event = trimmed.substringAfter("event:").trim()
                        continue
                    }
                    if (!trimmed.startsWith("data:")) continue
                    val payload = trimmed.substringAfter("data:").trim()
                    if (payload == "[DONE]") break
                    val obj = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: continue

                    when (event) {
                        "content_block_start" -> {
                            val idx = obj["index"]?.jsonPrimitive?.intOrNull ?: continue
                            val block = obj["content_block"]?.jsonObject ?: continue
                            if (block["type"]?.jsonPrimitive?.contentOrNull == "tool_use") {
                                toolAcc[idx] = ToolUseAccumulator(
                                    id = block["id"]?.jsonPrimitive?.contentOrNull,
                                    name = block["name"]?.jsonPrimitive?.contentOrNull,
                                    args = StringBuilder(),
                                )
                            }
                        }

                        "content_block_delta" -> {
                            val idx = obj["index"]?.jsonPrimitive?.intOrNull
                            val delta = obj["delta"]?.jsonObject ?: continue
                            when (delta["type"]?.jsonPrimitive?.contentOrNull) {
                                "text_delta" -> delta["text"]?.jsonPrimitive?.contentOrNull?.let {
                                    if (it.isNotEmpty()) trySend(CompletionChunk.Text(it))
                                }

                                "thinking_delta" -> {
                                    delta["thinking"]?.jsonPrimitive?.contentOrNull?.let {
                                        if (it.isNotEmpty()) trySend(CompletionChunk.Thinking(it))
                                    }
                                    // extended thinking 的签名：随 thinking 增量下发，供多轮/工具循环回传。
                                    delta["signature"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let {
                                        trySend(CompletionChunk.Signature(it))
                                    }
                                }

                                "input_json_delta" -> {
                                    val acc = toolAcc[idx]
                                    delta["partial_json"]?.jsonPrimitive?.contentOrNull?.let { acc?.args?.append(it) }
                                }
                            }
                        }

                        "content_block_stop" -> {
                            val idx = obj["index"]?.jsonPrimitive?.intOrNull
                            val acc = toolAcc.remove(idx) ?: continue
                            val name = acc.name
                            if (!name.isNullOrBlank()) {
                                pendingCalls += ToolCall(
                                    id = ToolCallId(acc.id ?: newToolCallId().value),
                                    name = name,
                                    arguments = runCatching { json.parseToJsonElement(acc.args.toString()).jsonObject }
                                        .getOrDefault(JsonObject(emptyMap())),
                                )
                            }
                        }

                        "message_delta" -> {
                            obj["usage"]?.jsonObject?.let { usage = parseUsage(it) }
                            sawUsageEnd = true
                            val stop = obj["delta"]?.jsonObject?.get("stop_reason")?.jsonPrimitive?.contentOrNull
                            if (stop != null) stopReason = mapStopReason(stop)
                            if (stop == "tool_use" && pendingCalls.isNotEmpty()) {
                                trySend(CompletionChunk.ToolCalls(pendingCalls))
                                pendingCalls.clear()
                            }
                        }

                        "message_stop" -> {
                            if (pendingCalls.isNotEmpty()) {
                                trySend(CompletionChunk.ToolCalls(pendingCalls))
                                pendingCalls.clear()
                            }
                            if (sawUsageEnd) trySend(CompletionChunk.UsageUpdate(usage))
                            trySend(CompletionChunk.Done(stopReason))
                            return@useLines
                        }
                    }
                }
            }
        }
    }

    // ───────────────────────── 请求体构建 ─────────────────────────

    private fun buildRequestBody(request: CompletionRequest): String {
        val system = request.system
            ?: request.messages.filter { it.role == LlmRole.SYSTEM }.joinToString("\n\n") { it.content }
                .takeIf { it.isNotBlank() }

        return buildJsonObject {
            put("model", config.model)
            put("stream", true)
            put("max_tokens", request.maxTokens)
            request.temperature?.let { put("temperature", it) }
            if (request.stopSequences.isNotEmpty()) {
                putJsonArray("stop_sequences") { request.stopSequences.forEach { add(JsonPrimitive(it)) } }
            }
            if (!system.isNullOrBlank()) put("system", system)

            putJsonArray("messages") {
                request.messages.filter { it.role != LlmRole.SYSTEM }.forEach { msg ->
                    add(buildJsonObject { providerMessage(this, msg) })
                }
            }

            if (request.tools.isNotEmpty()) {
                putJsonArray("tools") { request.tools.forEach { add(buildJsonObject { providerTool(this, it) }) } }
            }
        }.toString()
    }

    private fun providerMessage(b: JsonObjectBuilder, msg: LlmMessage) {
        when (msg.role) {
            LlmRole.USER -> {
                b.put("role", "user")
                if (msg.images.isNotEmpty()) {
                    b.putJsonArray("content") {
                        if (msg.content.isNotBlank()) {
                            add(buildJsonObject { put("type", "text"); put("text", msg.content) })
                        }
                        msg.images.forEach { img ->
                            add(buildJsonObject {
                                put("type", "image")
                                put("source", buildJsonObject {
                                    put("type", "base64")
                                    put("media_type", img.mimeType)
                                    put("data", img.base64Data)
                                })
                            })
                        }
                    }
                } else {
                    b.put("content", msg.content)
                }
            }

            LlmRole.TOOL -> {
                // 工具结果 = 一条 user 消息内的 tool_result content block。
                b.put("role", "user")
                b.putJsonArray("content") {
                    add(buildJsonObject {
                        put("type", "tool_result")
                        put("tool_use_id", msg.toolCallId ?: "")
                        put("content", msg.content)
                    })
                }
            }

            LlmRole.ASSISTANT -> {
                b.put("role", "assistant")
                if (msg.toolCalls.isEmpty()) {
                    // extended thinking 多轮/工具循环：thinking + signature 必须原样回传，否则 400。
                    if (!msg.signature.isNullOrBlank()) {
                        b.putJsonArray("content") {
                            add(buildJsonObject {
                                put("type", "thinking")
                                put("thinking", msg.reasoning ?: "")
                                put("signature", msg.signature)
                            })
                            if (msg.content.isNotBlank()) {
                                add(buildJsonObject { put("type", "text"); put("text", msg.content) })
                            }
                        }
                    } else {
                        b.put("content", msg.content)
                    }
                } else {
                    b.putJsonArray("content") {
                        if (!msg.signature.isNullOrBlank()) {
                            add(buildJsonObject {
                                put("type", "thinking")
                                put("thinking", msg.reasoning ?: "")
                                put("signature", msg.signature)
                            })
                        } else if (!msg.reasoning.isNullOrBlank()) {
                            add(buildJsonObject {
                                put("type", "thinking")
                                put("thinking", msg.reasoning)
                            })
                        }
                        if (msg.content.isNotBlank()) {
                            add(buildJsonObject { put("type", "text"); put("text", msg.content) })
                        }
                        msg.toolCalls.forEach { call ->
                            add(buildJsonObject {
                                put("type", "tool_use")
                                put("id", call.id.value)
                                put("name", call.name)
                                put("input", call.arguments)
                            })
                        }
                    }
                }
            }

            LlmRole.SYSTEM -> b.put("role", "user") // 已由顶层 system 承载，此处占位兜底
        }
    }

    private fun providerTool(b: JsonObjectBuilder, spec: ToolSpec) {
        b.put("name", spec.name)
        b.put("description", spec.description)
        b.put(
            "input_schema",
            spec.parameters.let { if (it.isEmpty()) AnthropicToolSchema else it },
        )
    }

    // ───────────────────────── 事件解析 ─────────────────────────

    private fun parseUsage(u: JsonObject): Usage = Usage(
        inputTokens = u["input_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
        outputTokens = u["output_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
        cacheReadTokens = u["cache_read_input_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
        cacheCreationTokens = u["cache_creation_input_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
    )

    private fun mapStopReason(stop: String): StopReasonRaw = when (stop) {
        "tool_use" -> StopReasonRaw.TOOL_USE
        "max_tokens" -> StopReasonRaw.MAX_TOKENS
        "stop_sequence" -> StopReasonRaw.STOP_SEQUENCE
        else -> StopReasonRaw.END_TURN
    }

    private class ToolUseAccumulator(val id: String?, val name: String?, val args: StringBuilder)

    companion object {
        const val ANTHROPIC_PROVIDER_ID = "anthropic"

        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val AnthropicToolSchema: JsonObject = JsonObject(mapOf("type" to JsonPrimitive("object")))

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .build()
    }
}