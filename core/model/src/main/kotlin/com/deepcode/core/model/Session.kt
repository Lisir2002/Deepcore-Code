package com.deepcode.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 指向某个模型供应商下的具体模型。切换模型只改这个引用，不动会话数据。 */
@Serializable
data class ModelRef(
    val providerId: String,
    val modelId: String,
) {
    override fun toString(): String = "$providerId/$modelId"
}

/**
 * 工作区引用。
 *
 * Android 上"代码在哪"有多种形态：App 私有目录、SAF 授权树、Git 仓库、远端 SSH。
 * 这里只存引用，具体读写由 Workspace 实现负责，上层无感切换。
 */
@Serializable
sealed interface WorkspaceRef {

    @Serializable
    @SerialName("local_dir")
    data class LocalDir(val absolutePath: String) : WorkspaceRef

    @Serializable
    @SerialName("document_tree")
    data class DocumentTree(val treeUri: String) : WorkspaceRef

    @Serializable
    @SerialName("git_repo")
    data class GitRepo(val absolutePath: String, val branch: String? = null) : WorkspaceRef

    @Serializable
    @SerialName("remote_ssh")
    data class RemoteSsh(val host: String, val port: Int, val user: String, val rootPath: String) : WorkspaceRef
}

@Serializable
enum class SessionStatus {
    @SerialName("idle") IDLE,
    @SerialName("running") RUNNING,
    /** 停在权限确认上，等用户点。 */
    @SerialName("awaiting_approval") AWAITING_APPROVAL,
    @SerialName("completed") COMPLETED,
    @SerialName("failed") FAILED,
    @SerialName("cancelled") CANCELLED,
}

/** 会话元数据。真正的会话内容在事件日志里，不在这里。 */
@Serializable
data class Session(
    val id: SessionId,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val modelRef: ModelRef,
    val workspaceRef: WorkspaceRef? = null,
    val status: SessionStatus = SessionStatus.IDLE,
    val pinned: Boolean = false,
    val totalTokens: Int = 0,
)
