package com.deepcode.core.model

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class SessionId(val value: String)

@Serializable
@JvmInline
value class TurnId(val value: String)

@Serializable
@JvmInline
value class EventId(val value: String)

@Serializable
@JvmInline
value class ToolCallId(val value: String)

@Serializable
@JvmInline
value class MessageId(val value: String)

/** 生成带可读前缀的 ID，方便日志里一眼看出是什么东西。 */
internal fun newId(prefix: String): String = "$prefix-${java.util.UUID.randomUUID()}"

fun newSessionId() = SessionId(newId("ses"))
fun newTurnId() = TurnId(newId("turn"))
fun newEventId() = EventId(newId("evt"))
fun newToolCallId() = ToolCallId(newId("call"))
fun newMessageId() = MessageId(newId("msg"))
