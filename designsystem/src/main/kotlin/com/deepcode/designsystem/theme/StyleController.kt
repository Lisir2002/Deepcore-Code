package com.deepcode.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 主题切换的读取源（§7.1）：designsystem 只定义接口 + 默认实现，装配在 :app。
 * `import(pack)`（T8.3 运行时可插拔）在 loader 就绪后追加，P2 不开放。
 */
interface StyleController {
    val spec: StateFlow<AppThemeSpec>
    val darkMode: StateFlow<DarkMode>
    val packs: StateFlow<List<AppThemeSpec>>

    /** 从已登记的风格包取一档切换（含持久化）。 */
    suspend fun setSpec(id: String)

    /** 切 deep 模式三态（含持久化）。 */
    suspend fun setDarkMode(mode: DarkMode)

    /** 进程内登记（如 brand + 已解析 import）。 */
    fun registerPack(spec: AppThemeSpec)
}

/** 键值式持久化抽象：由 :app 在 TableModule（注册制）上实现，designsystem 零存储依赖。 */
interface StylePreferenceStore {
    suspend fun read(key: String): String?
    suspend fun write(key: String, value: String)
}

/** 无持久化兜底（测试 / 未装配时用）。 */
object NoopStylePreferenceStore : StylePreferenceStore {
    override suspend fun read(key: String): String? = null
    override suspend fun write(key: String, value: String) {}
}

/** 默认实现：StateFlow 驱动 + [store] 持久化。由 :app 装配并提供初始值。 */
class DefaultStyleController(
    initialSpec: AppThemeSpec,
    darkMode: DarkMode = DarkMode.FOLLOW_SYSTEM,
    private val store: StylePreferenceStore = NoopStylePreferenceStore,
) : StyleController {

    private val _spec = MutableStateFlow(initialSpec)
    override val spec: StateFlow<AppThemeSpec> = _spec.asStateFlow()

    private val _darkMode = MutableStateFlow(darkMode)
    override val darkMode: StateFlow<DarkMode> = _darkMode.asStateFlow()

    private val _packs = MutableStateFlow<List<AppThemeSpec>>(listOf(initialSpec))
    override val packs: StateFlow<List<AppThemeSpec>> = _packs.asStateFlow()

    private val registry = mutableMapOf<String, AppThemeSpec>().apply { put(initialSpec.id, initialSpec) }

    override suspend fun setSpec(id: String) {
        val target = registry[id] ?: return
        _spec.value = target
        store.write(KEY_SPEC, id)
    }

    override suspend fun setDarkMode(mode: DarkMode) {
        _darkMode.value = mode
        store.write(KEY_DARK_MODE, mode.name)
    }

    override fun registerPack(spec: AppThemeSpec) {
        registry[spec.id] = spec
        _packs.value = registry.values.toList()
    }

    companion object {
        const val KEY_SPEC = "ui.style_active_spec"
        const val KEY_DARK_MODE = "ui.style_dark_mode"
    }
}

/** AppTheme 主要在此装配前一行的 instance 提供；未装配时快速失败（§7.1）。 */
val LocalStyleController = staticCompositionLocalOf<StyleController> {
    error("StyleController 未装配：请先在根 AppTheme 之前提供实例")
}

/** 决板纯函数：由 darkMode + 系统 dark 决出单一语义板与是否 dark（§8.2）。ThemeSwitchTest 单测入口。 */
fun resolveAppTokens(spec: AppThemeSpec, darkMode: DarkMode, isSystemDark: Boolean): AppTokens =
    when (darkMode) {
        DarkMode.LIGHT -> spec.light
        DarkMode.DARK -> spec.dark
        DarkMode.FOLLOW_SYSTEM -> if (isSystemDark) spec.dark else spec.light
    }

fun isDarkResolved(darkMode: DarkMode, isSystemDark: Boolean): Boolean =
    when (darkMode) {
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
        DarkMode.FOLLOW_SYSTEM -> isSystemDark
    }