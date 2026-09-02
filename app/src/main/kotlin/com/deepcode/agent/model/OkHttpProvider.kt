package com.deepcode.agent.model

import com.deepcode.core.agent.spi.CompletionChunk
import com.deepcode.core.agent.spi.CompletionRequest
import com.deepcode.core.agent.spi.LlmMessage
import com.deepcode.core.agent.spi.LlmRole
import com.deepcode.core.agent.spi.ModelInfo
import com.deepcode.core.agent.spi.ModelProvider
import com.deepcode.core.agent.spi.ModelTestResult
import com.deepcode.core.agent.spi.OpenAIConfig
import com.deepcode.core.agent.spi.StopReasonRaw
import com.deepcode.core.model.ToolCall
import com.deepcode.core.model.ToolCallId
import com.deepcode.core.model.ToolSpec
import com.deepcode.core.model.Usage
import com.deepcode.core.model.newToolCallId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
 * 面向 OpenAI 兼容协议（`/v1/chat/completions`，stream=true）的真实模型 Provider。
 *
 * 覆盖 GPT / DeepSeek / 通义等一大族模型——它们都遵循同一份协议，差异只体现在
 * 一个厂商特有的字段上，这里做了约定式宽容解析：
 * - 深度推理模型（如 DeepSeek-R1）把思考过程放到 `delta.reasoning_content`，
 *   与流式正文分开；本实现把它映射成 [CompletionChunk.Thinking]。
 * - 工具调用（function calling）走 `delta.tool_calls`，按 `index` 累加碎片，
 *   流结束时拼成一批 [ToolCall]。
 * - 用量通常随最后一个 chunk 一起下发（`usage` 字段），映射成
 *   [CompletionChunk.UsageUpdate]。
 *
 * 协议差异都被烂在这一层，Agent 主循环看到的是统一的 [CompletionChunk] 流。
 * 换供应商（Anthropic 等）只需再做一个实现类，Runtime 一行不动。
 */
class OkHttpProvider(
    private val config: OpenAIConfig,
    client: OkHttpClient = defaultClient(),
) : ModelProvider {

    override val id: String = OPENAI_PROVIDER_ID
    override val displayName: String =
        "OpenAI 兼容 · " + config.baseUrl.substringAfter("://", config.baseUrl).removeSuffix("/")

    private val http: OkHttpClient = client
    private val json: Json = Json { ignoreUnknownKeys = true }

    override suspend fun listModels(): List<ModelInfo> {
        // 真实拉取 OpenAI 兼容端点的 /v1/models 目录。
        // Failover（借鉴 deepcode-R ModelApiService）：若该端点返回 404（baseUrl 指向的是
        // 仅文生图专用网关，/v1/models 未暴露），则探测 /v1/images/generations——只要后者可
        // 达就回退一份常见文生图模型候选，避免用户误判为 Key/网络错误；其余失败回退已填模型单条。
        val (code, body) = try {
            val req = Request.Builder()
                .url(config.modelsUrl())
                .header("Authorization", "Bearer ${config.apiKey}")
                .header("Accept", "application/json")
                .build()
            http.newCall(req).execute().use { r ->
                r.code to r.body?.string().orEmpty()
            }
        } catch (e: Exception) {
            return fallbackSingle()
        }

        if (code == 404) {
            val t2i = probeT2IEndpoint()
            return if (t2i != 404) t2iCandidates() else fallbackSingle()
        }
        if (code !in 200..299 || body.isBlank()) return fallbackSingle()

        val remote = runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            root["data"]?.jsonArray?.mapNotNull { el ->
                val id = el.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                ModelInfo(
                    id = id,
                    displayName = el.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: id,
                    contextWindowTokens = 128_000,
                    maxOutputTokens = config.maxTokens,
                    supportsTools = true,
                    supportsThinking = true,
                    supportsPromptCaching = true,
                )
            } ?: emptyList()
        }.getOrDefault(emptyList())

        return remote.ifEmpty { fallbackSingle() }
    }

    override suspend fun testModel(modelId: String): ModelTestResult {
        val start = System.nanoTime()
        return try {
            if (config.apiKey.isBlank()) {
                ModelTestResult(success = false, latencyMs = 0, message = "请先填写 API Key")
            } else {
                val payload =
                    """{"model":${modelRef(modelId)},"max_tokens":1,"messages":[{"role":"user","content":"hi"}]}"""
                val req = Request.Builder()
                    .url(config.completionsUrl())
                    .header("Authorization", "Bearer ${config.apiKey}")
                    .header("Content-Type", "application/json")
                    .post(payload.toRequestBody(JSON))
                    .build()
                http.newCall(req).execute().use { r ->
                    val code = r.code
                    val latency = elapsedMs(start)
                    if (code in 200..299) {
                        ModelTestResult(success = true, latencyMs = latency, message = "连通 · ${latency}ms")
                    } else if (code == 404) {
                        // Failover：chat/completions 404 → 探测文生图接口，可用则以文生图判定。
                        val t2iCode = probeT2IEndpoint()
                        val total = elapsedMs(start)
                        if (t2iCode == 404) {
                            ModelTestResult(false, total, "聊天接口 404 · 文生图接口也 404：请检查 baseUrl 路径结构")
                        } else {
                            val tip = if (t2iCode in 200..299) "连通" else "可达（HTTP $t2iCode，可能是 Key / 额度问题）"
                            ModelTestResult(
                                success = t2iCode in 200..299,
                                latencyMs = total,
                                message = "聊天接口 404 · 已回退文生图接口 $tip · $total ms",
                            )
                        }
                    } else {
                        ModelTestResult(false, latency, "HTTP $code: ${r.body?.string().orEmpty().take(160)}")
                    }
                }
            }
        } catch (e: Exception) {
            ModelTestResult(false, elapsedMs(start), e.message ?: "请求失败")
        }
    }

    /** 文生图接口探活（只取 HTTP 状态，不消耗 token）：返回 404 = 路径不存在。 */
    private fun probeT2IEndpoint(): Int {
        val payload = """{"model":"probe","prompt":"probe","n":1,"size":"256x256"}"""
        val req = Request.Builder()
            .url(config.baseUrl.trimEnd('/') + "/images/generations")
            .header("Authorization", "Bearer ${config.apiKey}")
            .post(payload.toRequestBody(JSON))
            .build()
        return http.newCall(req).execute().use { it.code }
    }

    /** 拉取失败时回退到已填模型单条，保证「选择模型」页始终有兜底项。 */
    private fun fallbackSingle(): List<ModelInfo> =
        setOf(config.model, config.displayName.substringAfter("· ")).mapNotNull { raw ->
            raw.takeIf { it.isNotBlank() }?.let {
                ModelInfo(
                    id = it,
                    displayName = it,
                    contextWindowTokens = 128_000,
                    maxOutputTokens = config.maxTokens,
                    supportsTools = true,
                    supportsThinking = true,
                    supportsPromptCaching = true,
                )
            }
        }

    /** 文生图专用网关的常见模型候选（用户可在 UI 增删）。 */
    private fun t2iCandidates(): List<ModelInfo> = listOf(
        "step-2x-large", "step-image-edit-2", "step-image-plus",
        "dall-e-3", "dall-e-2", "flux-schnell", "flux-dev", "stable-diffusion-xl-1.0",
    ).map { id ->
        ModelInfo(
            id = id, displayName = id, contextWindowTokens = 0, maxOutputTokens = 0,
            supportsTools = false, supportsThinking = false,
        )
    }

    /** 安全的 JSON 字符串字面量（含引号、正确转义）。 */
    private fun modelRef(modelId: String): String = JsonPrimitive(modelId).toString()

    private fun elapsedMs(start: Long): Long = (System.nanoTime() - start) / 1_000_000

    override fun supports(modelId: String): Boolean = modelId == config.model || modelId == id

    override fun stream(request: CompletionRequest): Flow<CompletionChunk> = channelFlow {
        val body = buildRequestBody(request)
        val httpReq = Request.Builder()
            .url(config.completionsUrl())
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Accept", "text/event-stream")
            .post(body.toRequestBody(JSON))
            .build()

        http.newCall(httpReq).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string().orEmpty().take(512)
                val retryable = response.code >= 500 || response.code == 429
                trySend(CompletionChunk.Error("HTTP ${response.code}: $errBody", retryable = retryable))
                return@use
            }

            var usage = Usage()
            var terminated = false
            val toolAccumulators = mutableMapOf<Int, ToolAccumulator>()

            response.body?.charStream()?.useLines { lines ->
                for (line in lines) {
                    val data = line.trimStart()
                    if (!data.startsWith("data:")) continue
                    val payload = data.substringAfter("data:").trim()
                    if (payload == "[DONE]") break

                    val chunk = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: continue
                    val choice = chunk["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: continue
                    val delta = choice["delta"]?.jsonObject

                    if (delta != null) {
                        val thinking = delta["reasoning_content"]?.jsonPrimitive?.contentOrNull
                        if (!thinking.isNullOrBlank()) trySend(CompletionChunk.Thinking(thinking))

                        val text = delta["content"]?.jsonPrimitive?.contentOrNull
                        if (!text.isNullOrEmpty()) trySend(CompletionChunk.Text(text))

                        appendToolCalls(delta["tool_calls"]?.jsonArray, toolAccumulators) { trySend(it) }
                    }

                    val finish = choice["finish_reason"]?.jsonPrimitive?.contentOrNull
                    if (finish != null) emitToolCalls(toolAccumulators) { trySend(it) }

                    chunk["usage"]?.jsonObject?.let { usage = parseUsage(it) }
                    if (finish != null) {
                        terminated = true
                        trySend(CompletionChunk.UsageUpdate(usage))
                        trySend(CompletionChunk.Done(mapStopReason(finish)))
                        return@useLines
                    }
                }
            }

            // 只有流以 [DONE] 正常结束、且期间未出现 finish_reason 时，才收个尾。
            if (!terminated) {
                trySend(CompletionChunk.UsageUpdate(usage))
                trySend(CompletionChunk.Done(StopReasonRaw.END_TURN))
            }
        }
    }

    // ───────────────────────── 请求体构建 ─────────────────────────

    private fun buildRequestBody(request: CompletionRequest): String = buildJsonObject {
        put("model", config.model)
        put("stream", true)
        put("max_tokens", request.maxTokens)
        request.temperature?.let { put("temperature", it) }
        if (request.stopSequences.isNotEmpty()) {
            putJsonArray("stop") { request.stopSequences.forEach { add(JsonPrimitive(it)) } }
        }

        val system = request.system
            ?: request.messages.filter { it.role == LlmRole.SYSTEM }.joinToString("\n\n") { it.content }
                .takeIf { it.isNotBlank() }

        putJsonArray("messages") {
            if (!system.isNullOrBlank()) {
                add(buildJsonObject { put("role", "system"); put("content", system) })
            }
            request.messages.filter { it.role != LlmRole.SYSTEM }.forEach { msg ->
                add(buildJsonObject { providerMessage(this, msg) })
            }
        }

        if (request.tools.isNotEmpty()) {
            putJsonArray("tools") { request.tools.forEach { add(buildJsonObject { providerTool(this, it) }) } }
            put("tool_choice", "auto")
        }
    }.toString()

    private fun providerMessage(b: JsonObjectBuilder, msg: LlmMessage) {
        b.put("role", when (msg.role) {
            LlmRole.USER -> "user"
            LlmRole.ASSISTANT -> "assistant"
            LlmRole.TOOL -> "tool"
            LlmRole.SYSTEM -> "system"
        })
        // 多模态：带图片时 content 走数组（text/image_url 块），纯文本走字符串（兼容更多网关）。
        if (msg.images.isNotEmpty()) {
            b.putJsonArray("content") {
                if (msg.content.isNotBlank()) {
                    add(buildJsonObject { put("type", "text"); put("text", msg.content) })
                }
                msg.images.forEach { img ->
                    add(buildJsonObject {
                        put("type", "image_url")
                        putJsonObject("image_url") {
                            put("url", "data:${img.mimeType};base64,${img.base64Data}")
                        }
                    })
                }
            }
        } else {
            b.put("content", msg.content)
        }
        // 思考模式（如 DeepSeek）：assistant 推理过程必须原样回传，否则多轮/工具循环报 400。
        if (msg.role == LlmRole.ASSISTANT && !msg.reasoning.isNullOrBlank()) {
            b.put("reasoning_content", msg.reasoning)
        }
        if (msg.role == LlmRole.TOOL && msg.toolCallId != null) {
            b.put("tool_call_id", msg.toolCallId)
        }
        if (msg.role == LlmRole.ASSISTANT && msg.toolCalls.isNotEmpty()) {
            b.putJsonArray("tool_calls") { msg.toolCalls.forEach { add(toolCallToJson(it)) } }
        }
    }

    private fun providerTool(b: JsonObjectBuilder, spec: ToolSpec) {
        b.put("type", "function")
        b.putJsonObject("function") {
            put("name", spec.name)
            put("description", spec.description)
            put("parameters", spec.parameters.let { if (it.isEmpty()) defaultParamSchema else it })
        }
    }

    private fun toolCallToJson(call: ToolCall): JsonElement = buildJsonObject {
        put("id", call.id.value)
        put("type", "function")
        putJsonObject("function") {
            put("name", call.name)
            put("arguments", call.arguments.toString())
        }
    }

    // ───────────────────────── SSE 增量解析 ─────────────────────────

    private fun appendToolCalls(
        toolCalls: JsonArray?,
        acc: MutableMap<Int, ToolAccumulator>,
        send: (CompletionChunk) -> Unit,
    ) {
        toolCalls ?: return
        for (element in toolCalls) {
            val tc = element.jsonObject
            val index = tc["index"]?.jsonPrimitive?.intOrNull ?: continue
            val target = acc.getOrPut(index) { ToolAccumulator(null, null, StringBuilder()) }
            tc["id"]?.jsonPrimitive?.contentOrNull?.let { target.id = it }
            tc["function"]?.jsonObject?.let { fn ->
                fn["name"]?.jsonPrimitive?.contentOrNull?.let { target.name = it }
                fn["arguments"]?.jsonPrimitive?.contentOrNull?.let { target.args.append(it) }
            }
        }
    }

    private fun emitToolCalls(acc: MutableMap<Int, ToolAccumulator>, send: (CompletionChunk) -> Unit) {
        if (acc.isEmpty()) return
        val calls = acc.keys.sorted().mapNotNull { index ->
            val t = acc[index] ?: return@mapNotNull null
            val name = t.name
            if (name.isNullOrBlank()) return@mapNotNull null
            ToolCall(
                id = ToolCallId(t.id ?: newToolCallId().value),
                name = name,
                arguments = runCatching { json.parseToJsonElement(t.args.toString()).jsonObject }
                    .getOrDefault(JsonObject(emptyMap())),
            )
        }
        if (calls.isNotEmpty()) {
            send(CompletionChunk.ToolCalls(calls))
            acc.clear()
        }
    }

    private fun parseUsage(usageJson: JsonObject): Usage = Usage(
        inputTokens = usageJson["prompt_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
        outputTokens = usageJson["completion_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
        cacheReadTokens = usageJson["prompt_cache_hit_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
        cacheCreationTokens = usageJson["prompt_cache_miss_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
    )

    private fun mapStopReason(finish: String): StopReasonRaw = when (finish) {
        "tool_calls" -> StopReasonRaw.TOOL_USE
        "length" -> StopReasonRaw.MAX_TOKENS
        "stop" -> StopReasonRaw.STOP_SEQUENCE
        else -> StopReasonRaw.END_TURN
    }

    private class ToolAccumulator(var id: String?, var name: String?, val args: StringBuilder)

    companion object {
        const val OPENAI_PROVIDER_ID = "openai"

        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val defaultParamSchema: JsonObject = JsonObject(
            mapOf("type" to JsonPrimitive("object"), "properties" to JsonObject(emptyMap())),
        )

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS) // 长流式，读超时由字节流驱动
                .build()
    }
}