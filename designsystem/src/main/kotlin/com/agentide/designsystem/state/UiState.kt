package com.agentide.designsystem.state

/**
 * 全 App 统一的页面状态契约。
 *
 * 每个 ViewModel 对外只暴露 StateFlow<UiState<T>>，每个页面都用 AppScaffoldWithState 渲染。
 * 加载中 / 空态 / 错误态长什么样，全项目只有一份实现——
 * 这直接消灭了"这个页面的空态和那个页面不一样"这类问题。
 */
sealed interface UiState<out T> {

    data object Idle : UiState<Nothing>

    data object Loading : UiState<Nothing>

    data class Content<out T>(val value: T) : UiState<T>

    data class Empty(
        val title: String,
        val message: String? = null,
        val actionLabel: String? = null,
    ) : UiState<Nothing>

    data class Error(
        val message: String,
        val retryable: Boolean = true,
        val detail: String? = null,
    ) : UiState<Nothing>
}

fun <T> UiState<T>.valueOrNull(): T? = (this as? UiState.Content)?.value

inline fun <T, R> UiState<T>.map(transform: (T) -> R): UiState<R> = when (this) {
    is UiState.Content -> UiState.Content(transform(value))
    is UiState.Idle -> UiState.Idle
    is UiState.Loading -> UiState.Loading
    is UiState.Empty -> this
    is UiState.Error -> this
}

/** 把结果包成 UiState，统一处理异常，避免每个 ViewModel 各写一套 try/catch。 */
inline fun <T> runCatchingToUiState(block: () -> T): UiState<T> = try {
    UiState.Content(block())
} catch (t: Throwable) {
    UiState.Error(t.message ?: "未知错误", detail = t.stackTraceToString().take(500))
}
