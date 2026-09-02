package com.deepcode.agent.model

import com.deepcode.core.agent.spi.CompletionChunk
import com.deepcode.core.agent.spi.CompletionRequest
import com.deepcode.core.agent.spi.LlmMessage
import com.deepcode.core.agent.spi.LlmRole
import com.deepcode.core.agent.spi.ModelInfo
import com.deepcode.core.agent.spi.ModelProvider
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
    private val config: LlmEndpointConfig,
    client: OkHttpClient = defaultClient(),
) : ModelProvider {

    override val id: String = OPENAI_PROVIDER_ID
    override val displayName: String =
        "OpenAI 兼容 · " + config.baseUrl.substringAfter("://", config.baseUrl).removeSuffix("/")

    private val http: OkHttpClient = client
    private val json: Json = Json { ignoreUnknownKeys = true }

    override suspend fun listModels(): List<ModelInfo> = listOf(
        ModelInfo(
            id = config.model,
            displayName = config.model,
            contextWindowTokens = 128_000,
            maxOutputTokens = config.maxTokens,
            supportsTools = true,
            supportsThinking = true,
            supportsPromptCaching = true,
        ),
    )

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
        b.put("content", msg.content)
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

/**
 * 一个 OpenAI 兼容 LLM 端点的连接配置。
 *
 * [baseUrl] 通常是带 /v1 前缀的地址（如 `https://api.deepseek.com/v1`）；
 * 最终请求 URL = `baseUrl + /chat/completions`（末尾斜杠容错）。
 */
data class LlmEndpointConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val maxTokens: Int = 8192,
) {
    fun completionsUrl(): String = baseUrl.trimEnd('/') + "/chat/completions"
}