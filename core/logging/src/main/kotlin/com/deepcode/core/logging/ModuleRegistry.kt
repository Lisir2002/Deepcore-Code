package com.deepcode.core.logging

import java.util.concurrent.ConcurrentHashMap

/**
 * 模块登记机制（决策 D3/D20）：模块名 → tag 前缀映射，Application 启动时集中登记。
 * 未来新模块接入：登记处加一行前缀 + import Log 即用。
 */
class ModuleRegistry {
    private val map = ConcurrentHashMap<String, String>()

    fun register(module: String, tagPrefix: String) {
        map[module] = tagPrefix
    }

    fun prefixFor(module: String): String? = map[module]

    fun all(): Map<String, String> = map.toMap()
}
