package com.deepcode.core.data.db

import app.cash.sqldelight.Query
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlPreparedStatement
import kotlinx.coroutines.flow.Flow

/**
 * 数据层门面：**业务代码读写 SQLite 的唯一入口**。
 *
 * 铁律：feature 层禁止自己开连接、禁止直接持有 SqlDriver。要持久化就三选一：
 *   1. 用 [database] 里已有的类型安全查询（核心表 events / sessions）
 *   2. 注册一个 [TableModule]，然后用自己的 DAO 走 [rawQuery] / [rawExecute]
 *   3. 要类型安全查询自己的表：该 feature 自建 SQLDelight Database，共享同一个 SqlDriver
 *
 * 所有方法都把 SQLite 操作切到单线程 IO 上下文，主线程永不碰数据库。
 */
interface SqliteDatabase {

    /** SQLDelight 生成的类型安全查询入口（`eventsQueries` / `sessionsQueries`）。 */
    val database: DeepCoreDatabase

    /** 读路径：切到 IO 线程执行一段查询。 */
    suspend fun <T> read(block: () -> T): T

    /**
     * 写路径（事务边界）：block 内的所有写入要么全成，要么全回滚。
     *
     * 状态表与事件流必须在**同一个事务**里更新——例如"重命名会话"= 更新 sessions 行
     * + append 一条 SessionRenamed 事件，绝不能只做一个。
     */
    suspend fun <T> transaction(block: () -> T): T

    /**
     * 表观测：底层数据一变就重跑查询并重新发射整表结果（SQLDelight Flow）。
     * 首次订阅会立即发射一次当前值。
     */
    fun <T : Any> observe(query: Query<T>): Flow<List<T>>

    /**
     * 扩展模块 DAO 用的原始查询。
     *
     * **必须在 [read] 或 [transaction] 块内调用**——它本身不切线程，和 SQLDelight
     * 生成的 Queries 一样是同步 API，靠调用方已经切到 IO 线程来保证不碰主线程。
     */
    fun <T> rawQuery(
        sql: String,
        parameters: Int = 0,
        binders: (SqlPreparedStatement.() -> Unit)? = null,
        mapper: (SqlCursor) -> T,
    ): List<T>

    /**
     * 扩展模块 DAO 用的原始写入，同样必须在 [transaction] 块内调用。
     * 多条语句要么全部包进一个事务，要么就别怪数据不一致。
     */
    fun rawExecute(
        sql: String,
        parameters: Int = 0,
        binders: (SqlPreparedStatement.() -> Unit)? = null,
    ): Long

    fun close()
}
