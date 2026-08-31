package com.deepcode.core.data.event

import com.deepcode.core.model.AgentEvent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 编码结果：[type] 进 `events.type` 列（过滤/统计用），[payload] 进 `events.payload` 列。 */
data class EncodedEvent(val type: String, val payload: String)

/**
 * 事件 <-> JSON 的编解码。
 *
 * 为什么 [type] 取自序列化后的多态判别字段，而不是 `event::class.simpleName`：
 * **开了 R8 之后类名会被混淆**（`TurnStarted` → `a.b.c`），存进去的 type 就废了；
 * 判别字符串来自 `@SerialName` 常量，是稳定的协议值，可以安全持久化。
 *
 * 开启 `ignoreUnknownKeys`：读到更新版本写入的、带新字段的 payload 时不炸，
 * 保证"老版本 App 能打开新版本写的库"。
 */
object EventCodec {

    private const val DISCRIMINATOR = "type"

    val json: Json = Json { ignoreUnknownKeys = true }

    fun encode(event: AgentEvent): EncodedEvent {
        val payload = json.encodeToString<AgentEvent>(event)
        return EncodedEvent(type = typeOf(payload), payload = payload)
    }

    fun decode(payload: String): AgentEvent = json.decodeFromString<AgentEvent>(payload)

    fun typeOf(payload: String): String =
        (json.parseToJsonElement(payload) as? JsonObject)
            ?.get(DISCRIMINATOR)
            ?.jsonPrimitive
            ?.content
            ?: error("事件 JSON 缺少判别字段 '$DISCRIMINATOR'：$payload")
}
