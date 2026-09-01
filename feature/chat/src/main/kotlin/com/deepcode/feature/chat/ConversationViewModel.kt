package com.deepcode.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepcode.core.data.EventStore
import com.deepcode.core.model.ModelRef
import com.deepcode.core.model.SessionId
import com.deepcode.core.model.SessionStatus
import com.deepcode.core.model.newSessionId
import com.deepcode.core.uistate.SessionSummaryReducer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch

/**
 * 对话列表页的 ViewModel。
 *
 * 列表数据来自会话索引（[EventStore.observeSessions]，不重放事件流）；
 * 每个会话的 标题/预览/状态角标 由纯 Kotlin 归约器 [SessionSummaryReducer]
 * 从该会话的事件日志折出——会话少时逐会话读一次事件可接受，
 * 会话变多后再把预览/状态落到索引列（schema v2）。
 */
class ConversationViewModel(
    private val store: EventStore,
) : ViewModel() {

    private val _items = MutableStateFlow<List<ConversationItem>>(emptyList())
    val items: StateFlow<List<ConversationItem>> = _items

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    /** 新建对话成功后发射新会话 id，由页面回调导航。 */
    private val _created = MutableStateFlow<SessionId?>(null)
    val created: StateFlow<SessionId?> = _created

    init {
        viewModelScope.launch {
            store.observeSessions()
                .mapLatest { indexList ->
                    indexList.map { idx ->
                        val events = store.loadEvents(idx.id)
                        val summary = SessionSummaryReducer.reduce(events)
                        ConversationItem(
                            id = idx.id,
                            title = summary.title.ifBlank { idx.title }.ifBlank { "新对话" },
                            preview = summary.preview,
                            updatedAt = idx.updatedAt,
                            status = summary.status,
                            modelRef = summary.modelRef,
                        )
                    }
                }
                .collect { items ->
                    _items.value = items
                    _loading.value = false
                }
        }
    }

    fun rename(id: SessionId, title: String) {
        viewModelScope.launch {
            runCatching { store.renameSession(id, title.trim()) }
        }
    }

    fun delete(id: SessionId) {
        viewModelScope.launch {
            runCatching { store.clear(id) }
        }
    }

    fun create() {
        viewModelScope.launch {
            val id = newSessionId()
            runCatching { store.createSession(id) }
                .onSuccess { _created.value = id }
        }
    }

    fun consumeCreated() {
        _created.value = null
    }
}

/** 列表项 UI 模型（由索引 + 事件归约合成）。 */
data class ConversationItem(
    val id: SessionId,
    val title: String,
    val preview: String,
    val updatedAt: Long,
    val status: SessionStatus,
    val modelRef: ModelRef? = null,
)
