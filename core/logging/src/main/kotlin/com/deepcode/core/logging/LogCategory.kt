package com.deepcode.core.logging

/**
 * 业务维度分类。完整分类 = 大类 + 子类，如 SECURITY.PERMISSION。
 * 见 docs/LOGGING_SYSTEM_DESIGN.md §4 分类模型（决策 D4–D7）。
 */
enum class LogGroup {
    SECURITY,  // 危险/安全
    OPERATION, // 操作
    STATE,     // 状态
    ERROR,     // 错误
    SYSTEM;    // 系统

    fun sub(sub: LogSubCategory): LogCategory =
        LogCategory.valueOf("${name}_$sub") // 枚举名统一为 大类_子类
}

/**
 * 完整分类枚举（大类+子类扁平化，类型安全、易序列化）。
 * 命名规则：<GROUP><SUB>，如 SECURITY_PERMISSION。
 */
enum class LogCategory(val group: LogGroup) {
    // SECURITY 危险
    SECURITY_INTEGRITY(LogGroup.SECURITY),
    SECURITY_PERMISSION(LogGroup.SECURITY),
    SECURITY_CREDENTIAL(LogGroup.SECURITY),
    SECURITY_SELF(LogGroup.SECURITY),
    SECURITY_CRASH_CAUSE(LogGroup.SECURITY),
    SECURITY_OTHER(LogGroup.SECURITY),

    // OPERATION 操作
    OPERATION_AGENT(LogGroup.OPERATION),
    OPERATION_MCP(LogGroup.OPERATION),
    OPERATION_DATA(LogGroup.OPERATION),
    OPERATION_SANDBOX(LogGroup.OPERATION),
    OPERATION_USER(LogGroup.OPERATION),

    // STATE 状态
    STATE_LIFECYCLE(LogGroup.STATE),
    STATE_SESSION(LogGroup.STATE),
    STATE_CONFIG(LogGroup.STATE),

    // ERROR 错误
    ERROR_EXCEPTION(LogGroup.ERROR),
    ERROR_FAILURE(LogGroup.ERROR),

    // SYSTEM 系统
    SYSTEM_INIT(LogGroup.SYSTEM),
    SYSTEM_FRAMEWORK(LogGroup.SYSTEM);

    /** 展示名：SECURITY.PERMISSION */
    val displayName: String get() = "${group.name}.${name.removePrefix("${group.name}_")}"

    /** 是否危险类（镜像进 danger.log） */
    val isSecurity: Boolean get() = group == LogGroup.SECURITY
}

/**
 * 子类标记：供 [LogGroup.sub] 构造完整分类使用。
 */
enum class LogSubCategory {
    INTEGRITY, PERMISSION, CREDENTIAL, SELF, CRASH_CAUSE, OTHER,
    AGENT, MCP, DATA, SANDBOX, USER,
    LIFECYCLE, SESSION, CONFIG,
    EXCEPTION, FAILURE,
    INIT, FRAMEWORK
}
