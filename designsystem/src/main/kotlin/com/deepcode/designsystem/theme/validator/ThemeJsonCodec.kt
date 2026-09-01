package com.deepcode.designsystem.theme.validator

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * theme.json v1 编解码器 + 结构/值校验（§7.2 格式、§7.4「结构/值」两行 → 整包拒载）。
 *
 * 纯 Kotlin、可 JVM 单测。只负责**拒载级门槛**（不准进合并器）：
 *   • 结构：JSON 合法、schemaVersion ≤ 当前、必填字段（id/name）→ 否则整包拒载；
 *   • 值：hex 合法、radius 0–40dp → 否则整包拒载（含组错位的类型冲突）；
 *   • 未知令牌/组键 → 告警忽略（§7.3.2 前向兼容，进导入报告）。
 *
 * 与 `ThemeValidator` 分工：此处是 delta 原句校验；A11y 硬约束/可用性软约束在
 * 合并后的 `ThemeValidator`（§7.4 后两行）。schemaVersion 高于当前也由这里明确拒载
 * （§7.5，不降级猜测）。
 */
object ThemeJsonCodec {

    /** 当前 schema 版本；未来升版只改这里（§7.5 单调递增，高于即拒）。 */
    const val CURRENT_SCHEMA_VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true   // 顶层未来扩展字段忽略（前向兼容）
        explicitNulls = false
    }

    // —— 结果类型 ——
    sealed interface CodecResult {
        /** 可进入合并器。warnings 含未知键等前向兼容告警（进导入报告）。 */
        data class Success(val pack: ThemePackJson, val warnings: List<String>) : CodecResult

        /** 整包拒载（结构/值失败）。errors 给出可读键路径。 */
        data class Reject(val errors: List<String>) : CodecResult
    }

    /**
     * 解析 theme.json 原文。JSON 非法/结构错位 → Reject；必填缺失/版本过高/
     * hex 非法/radius 越界 → Reject（§7.4）。未知键 → Success + warnings。
     */
    fun decode(raw: String): CodecResult {
        val pack = try {
            json.decodeFromString<ThemePackJson>(raw)
        } catch (e: SerializationException) {
            // 结构错位（含 radius 组内类型冲突：「组错位」→ 整包拒载，§7.3.3）
            return CodecResult.Reject(listOf("JSON 非法 / 组错位：${e.message}"))
        }

        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (pack.id.isBlank()) errors += "id 必填（非空）"
        if (pack.name.isBlank()) errors += "name 必填（非空）"
        if (pack.schemaVersion > CURRENT_SCHEMA_VERSION) {
            errors += "schemaVersion ${pack.schemaVersion} 高于当前 ${CURRENT_SCHEMA_VERSION}：拒绝加载，不降级猜测（§7.5）"
        }

        validateBoard("light", pack.light, errors, warnings)
        validateBoard("dark", pack.dark, errors, warnings)

        return if (errors.isEmpty()) CodecResult.Success(pack, warnings)
        else CodecResult.Reject(errors)
    }

    /** 对称编码（导入报告/调试；合并产物走 AppThemeSpec，不在此列）。 */
    fun encode(pack: ThemePackJson): String = json.encodeToString(ThemePackJson.serializer(), pack)

    private fun validateBoard(
        mode: String,
        board: BoardJson?,
        errors: MutableList<String>,
        warnings: MutableList<String>,
    ) {
        if (board == null) return

        board.color.forEach { (key, value) ->
            val path = "$mode.color.$key"
            if (key !in ThemeTokenCatalog.colorKeys) {
                warnings += "未知颜色令牌 `$key`（$path）：忽略，前向兼容（§7.3.2）"
                return@forEach
            }
            if (!isValidHex(value)) errors += "$path：非法 hex \"$value\"（§7.4 值校验）"
        }

        board.radius.forEach { (key, value) ->
            val path = "$mode.radius.$key"
            if (key !in ThemeTokenCatalog.radiusKeys) {
                warnings += "未知圆角令牌 `$key`（$path）：忽略，前向兼容（§7.3.2）"
                return@forEach
            }
            if (value !in MIN_RADIUS_DP..MAX_RADIUS_DP) {
                errors += "$path：数值越界 [${MIN_RADIUS_DP}, ${MAX_RADIUS_DP}] dp，实际 $value（§7.4 值校验）"
            }
        }
    }

    /** 接受 `#RGB` / `#RRGGBB` / `#RRGGBBAA`。 */
    private fun isValidHex(s: String): Boolean =
        HEX_REGEX.matches(s.trim())

    const val MIN_RADIUS_DP = 0
    const val MAX_RADIUS_DP = 40
    private val HEX_REGEX = Regex("^#[0-9A-Fa-f]{3}([0-9A-Fa-f]{3})?([0-9A-Fa-f]{2})?$")
}

/** theme.json v1 数据镜像（§7.2 扁平 + delta 覆盖）。 */
@Serializable
data class ThemePackJson(
    val id: String,
    val name: String = "",
    val schemaVersion: Int = ThemeJsonCodec.CURRENT_SCHEMA_VERSION,
    val light: BoardJson? = null,
    val dark: BoardJson? = null,
)

/** 单一明暗板的 delta 覆盖组：color / radius。 */
@Serializable
data class BoardJson(
    val color: Map<String, String> = emptyMap(),
    val radius: Map<String, Int> = emptyMap(),
)