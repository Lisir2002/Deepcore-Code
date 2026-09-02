package com.deepcode.agent.model

import com.deepcode.core.agent.spi.CompletionChunk
import com.deepcode.core.agent.spi.CompletionRequest
import com.deepcode.core.agent.spi.GeminiConfig
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
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
 * Google Gemini Provider（`POST /v1beta/models/{model}:streamGenerateContent?alt=sse`）。
 *
 * 协议要点：
 * - 鉴权用 `x-goog-api-key` 头。
 * - 请求结构用 `contents[].parts`，对话角色为 `user` / `model`（非 assistant）；
 *   system prompt 走顶层 `systemInstruction`。
 * - 工具调用：响应 `parts[].functionCall`（无 id，本地补一个），
 *   工具结果用 role=`function` 的 `functionResponse` 块表达。
 * - 流式经 `?alt=sse` 返回 SSE，每块是完整 JSON：`candidates[0]` 增量文本 +
 *   `usageMetadata` + 最终 `finishReason`。
 */
class GeminiProvider(
    private val config: GeminiConfig,
    client: OkHttpClient = defaultClient(),
) : ModelProvider {

    override val id: String = GEMINI_PROVIDER_ID
    override val displayName: String =
        "Gemini · " + config.baseUrl.substringAfter("://", config.baseUrl).removeSuffix("/")

    private val http: OkHttpClient = client
    private val json: Json = Json { ignoreUnknownKeys = true }
    private val modelId: String = config.model.removePrefix("models/")

    override suspend fun listModels(): List<ModelInfo> {
        val req = Request.Builder()
            .url(config.modelsUrl())
            .header("x-goog-api-key", config.apiKey)
            .build()
        return runCatching {
            http.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return@runCatching emptyList()
                val root = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
                root["models"]?.jsonArray?.mapNotNull { el ->
                    val raw = el.jsonObject["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val id = raw.removePrefix("models/")
                    ModelInfo(
                        id = id,
                        displayName = el.jsonObject["displayName"]?.jsonPrimitive?.contentOrNull ?: id,
                        contextWindowTokens = el.jsonObject["inputTokenLimit"]?.jsonPrimitive?.intOrNull ?: 1_000_000,
                        maxOutputTokens = el.jsonObject["maxOutputTokens"]?.jsonPrimitive?.intOrNull ?: config.maxTokens,
                    )
                } ?: emptyList()
            }
        }.getOrDefault(emptyList())
    }

    override fun supports(modelId: String): Boolean =
        modelId == config.model || modelId == this.modelId || modelId == id

    override suspend fun testModel(modelId: String): ModelTestResult {
        val start = System.nanoTime()
        return try {
            if (config.apiKey.isBlank()) {
                ModelTestResult(success = false, latencyMs = 0, message = "请先填写 API Key")
            } else {
                val url = config.baseUrl.trimEnd('/') + "/v1beta/models/" +
                    modelId.removePrefix("models/") + ":generateContent"
                val payload = """{"contents":[{"role":"user","parts":[{"text":"hi"}]}]}"""
                val req = Request.Builder()
                    .url(url)
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", config.apiKey)
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
        val url = config.baseUrl.trimEnd('/') + "/v1beta/models/" + modelId + ":streamGenerateContent?alt=sse"
        val body = buildRequestBody(request)
        val httpReq = Request.Builder()
            .url(url)
            .header("content-type", "application/json")
            .header("x-goog-api-key", config.apiKey)
            .post(body.toRequestBody(JSON))
            .build()

        http.newCall(httpReq).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string().orEmpty().take(512)
                val retryable = response.code >= 500 || response.code == 429
                trySend(CompletionChunk.Error("HTTP ${response.code}: $errBody", retryable = retryable))
                return@use
            }

            var finite = false
            var usage = Usage()
            val pendingCalls = mutableListOf<ToolCall>()

            response.body?.charStream()?.useLines { lines ->
                for (line in lines) {
                    val trimmed = line.trim()
                    var payload = trimmed
                    if (trimmed.startsWith("data:")) {
                        payload = trimmed.substringAfter("data:").trim()
                        if (payload == "[DONE]") break
                    }
                    if (payload.isEmpty()) continue
                    val obj = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: continue
                    if (finite) continue

                    // 逐块增量文本
                    val parts = obj["candidates"]
                        ?.jsonArray?.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray
                    if (parts != null) {
                        for (part in parts) {
                            val p = part.jsonObject
                            p["text"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let {
                                trySend(CompletionChunk.Text(it))
                            }
                            p["functionCall"]?.jsonObject?.let { fc ->
                                pendingCalls += ToolCall(
                                    id = ToolCallId(newToolCallId().value),
                                    name = fc["name"]?.jsonPrimitive?.contentOrNull ?: "unknown",
                                    arguments = fc["args"]?.jsonObject ?: JsonObject(emptyMap()),
                                )
                            }
                        }
                    }

                    obj["usageMetadata"]?.jsonObject?.let { usage = parseUsage(it) }

                    val finish = obj["candidates"]
                        ?.jsonArray?.firstOrNull()?.jsonObject?.get("finishReason")?.jsonPrimitive?.contentOrNull
                    if (finish != null) {
                        finite = true
                        if (pendingCalls.isNotEmpty()) {
                            trySend(CompletionChunk.ToolCalls(pendingCalls.toList()))
                            pendingCalls.clear()
                        }
                        trySend(CompletionChunk.UsageUpdate(usage))
                        trySend(CompletionChunk.Done(mapStopReason(finish, pendingCalls.isNotEmpty())))
                        return@useLines
                    }
                }
            }

            // 流结束但未显式给 finishReason（宽松兜底）
            if (!finite) {
                if (pendingCalls.isNotEmpty()) trySend(CompletionChunk.ToolCalls(pendingCalls.toList()))
                trySend(CompletionChunk.UsageUpdate(usage))
                trySend(CompletionChunk.Done(StopReasonRaw.END_TURN))
            }
        }
    }

    // ───────────────────────── 请求体构建 ─────────────────────────

    private fun buildRequestBody(request: CompletionRequest): String {
        val system = request.system
            ?: request.messages.filter { it.role == LlmRole.SYSTEM }.joinToString("\n\n") { it.content }
                .takeIf { it.isNotBlank() }

        return buildJsonObject {
            putJsonArray("contents") {
                request.messages.filter { it.role != LlmRole.SYSTEM }.forEach { msg ->
                    add(buildJsonObject { providerContent(this, msg) })
                }
            }
            if (!system.isNullOrBlank()) {
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") { add(buildJsonObject { put("text", system) }) }
                }
            }
            putJsonObject("generationConfig") {
                put("maxOutputTokens", request.maxTokens)
                request.temperature?.let { put("temperature", it) }
                if (request.stopSequences.isNotEmpty()) {
                    putJsonArray("stopSequences") { request.stopSequences.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
                }
            }
            if (request.tools.isNotEmpty()) {
                putJsonArray("tools") {
                    add(buildJsonObject {
                        putJsonArray("functionDeclarations") { request.tools.forEach { add(toolToDeclaration(it)) } }
                    })
                }
            }
        }.toString()
    }

    private fun providerContent(b: JsonObjectBuilder, msg: LlmMessage) {
        when (msg.role) {
            LlmRole.USER -> {
                b.put("role", "user")
                b.putJsonArray("parts") {
                    if (msg.content.isNotBlank()) {
                        add(buildJsonObject { put("text", msg.content) })
                    }
                    // 多模态：图片走 inline_data（base64 直传）。
                    msg.images.forEach { img ->
                        add(buildJsonObject {
                            putJsonObject("inlineData") {
                                put("mimeType", img.mimeType)
                                put("data", img.base64Data)
                            }
                        })
                    }
                }
            }

            LlmRole.ASSISTANT -> {
                b.put("role", "model")
                if (msg.toolCalls.isEmpty()) {
                    b.putJsonArray("parts") {
                        if (!msg.reasoning.isNullOrBlank()) {
                            add(buildJsonObject { put("text", msg.reasoning); put("thought", true) })
                        }
                        if (msg.content.isNotBlank()) {
                            add(buildJsonObject { put("text", msg.content) })
                        }
                    }
                } else {
                    b.putJsonArray("parts") {
                        if (!msg.reasoning.isNullOrBlank()) {
                            add(buildJsonObject { put("text", msg.reasoning); put("thought", true) })
                        }
                        if (msg.content.isNotBlank()) {
                            add(buildJsonObject { put("text", msg.content) })
                        }
                        msg.toolCalls.forEach { call ->
                            add(buildJsonObject {
                                putJsonObject("functionCall") {
                                    put("name", call.name)
                                    put("args", call.arguments)
                                }
                            })
                        }
                    }
                }
            }

            LlmRole.TOOL -> {
                // 工具结果 = role=function 的 functionResponse 块。
                b.put("role", "function")
                b.putJsonArray("parts") {
                    add(buildJsonObject {
                        putJsonObject("functionResponse") {
                            put("name", msg.toolName ?: "unknown")
                            putJsonObject("response") {
                            putJsonObject("result") {
                                val parsed = msg.content.toJsonObjectOrNull()
                                if (parsed != null) parsed.forEach { (k, v) -> put(k, v) }
                                else put("text", msg.content)
                            }
                        }
                        }
                    })
                }
            }

            LlmRole.SYSTEM -> b.put("role", "user") // 占位，已由 systemInstruction 承载
        }
    }

    private fun String.toJsonObjectOrNull(): JsonObject? =
        runCatching { json.parseToJsonElement(this).jsonObject }.getOrNull()

    private fun toolToDeclaration(spec: ToolSpec): JsonObject = buildJsonObject {
        put("name", spec.name)
        put("description", spec.description)
        if (spec.parameters.isNotEmpty()) put("parameters", spec.parameters)
    }

    // ───────────────────────── 事件解析 ─────────────────────────

    private fun parseUsage(u: JsonObject): Usage = Usage(
        inputTokens = u["promptTokenCount"]?.jsonPrimitive?.intOrNull ?: 0,
        outputTokens = u["candidatesTokenCount"]?.jsonPrimitive?.intOrNull ?: 0,
        cacheReadTokens = u["cachedContentTokenCount"]?.jsonPrimitive?.intOrNull ?: 0,
        cacheCreationTokens = 0,
    )

    private fun mapStopReason(finish: String, hasPendingCalls: Boolean): StopReasonRaw = when (finish) {
        "MAX_TOKENS" -> StopReasonRaw.MAX_TOKENS
        "STOP", "SAFETY", "RECITATION" -> if (hasPendingCalls) StopReasonRaw.TOOL_USE else StopReasonRaw.END_TURN
        else -> if (hasPendingCalls) StopReasonRaw.TOOL_USE else StopReasonRaw.END_TURN
    }

    companion object {
        const val GEMINI_PROVIDER_ID = "gemini"

        private val JSON = "application/json; charset=utf-8".toMediaType()

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .build()
    }
}