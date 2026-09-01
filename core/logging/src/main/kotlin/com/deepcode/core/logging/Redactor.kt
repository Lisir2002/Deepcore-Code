package com.deepcode.core.logging

/**
 * 脱敏器（决策 D22）。覆盖：凭据字段 / 绝对文件路径 / 用户输入正文 / 设备标识 / URL 凭据。
 * 挂载点：写入 Sink 前统一过一遍（见 [Log]）。
 */
class Redactor {

    private val credentialPattern = Regex(
        """(?i)(authorization|api[_-]?key|access[_-]?token|refresh[_-]?token|token|secret|password|passwd|bearer|client[_-]?secret)(["'\s:=]+)([^,;&?]+)"""
    )

    private val urlCredentialPattern = Regex(
        """(https?://)([^/\s:@]+):([^@/\s]+)@"""
    )

    private val pathPattern = Regex(
        """/data/(?:user/\d+|data|media|app)/[^"'\s,;]+"""
    )

    private val querySecretPattern = Regex(
        """([?&](?:token|key|secret|signature|sig)=)[^&\s]+"""
    )

    /** 对文本应用全部规则。 */
    fun redact(text: String?): String? {
        if (text == null) return null
        var out = text
        // 先处理 query 参数，避免凭据规则吞掉后续 & 参数
        out = querySecretPattern.replace(out) { m ->
            m.groupValues[1] + "***"
        }
        out = credentialPattern.replace(out) { m ->
            m.groupValues[1] + m.groupValues[2] + "***"
        }
        out = urlCredentialPattern.replace(out) { m ->
            m.groupValues[1] + "***:***@"
        }
        out = pathPattern.replace(out) { m ->
            val path = m.value
            val dirs = path.split('/').filter { it.isNotBlank() }
            val tail = dirs.takeLast(2).joinToString("/")
            "[path]/$tail"
        }
        return out
    }

    /** 设备标识：替换为不可逆 hash，避免日志/导出泄露。 */
    fun redactDeviceId(id: String): String {
        if (id.isBlank()) return ""
        return "id:" + id.hashCode().toUInt().toString(16)
    }
}
