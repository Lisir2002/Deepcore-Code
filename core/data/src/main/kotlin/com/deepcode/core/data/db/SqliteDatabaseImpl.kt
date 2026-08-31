package com.deepcode.core.data.db

import app.cash.sqldelight.Query
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * 打开（并在必要时迁移）数据库。
 *
 * @param driver 平台驱动：Android 用 `AndroidSqliteDriver`，JVM 测试用 `JdbcSqliteDriver(IN_MEMORY)`
 * @param modules 各功能模块注册的表，核心框架不认识它们也能正确建表/迁移
 * @param io 所有 SQLite 操作执行的上下文；默认单线程，保证写入串行
 */
fun createSqliteDatabase(
    driver: SqlDriver,
    modules: List<TableModule> = emptyList(),
    io: CoroutineContext = Dispatchers.IO.limitedParallelism(1),
): SqliteDatabase {
    SchemaManager(driver = driver, modules = modules).migrate()
    return SqliteDatabaseImpl(driver, io)
}

internal class SqliteDatabaseImpl(
    private val driver: SqlDriver,
    private val io: CoroutineContext,
) : SqliteDatabase {

    override val database: DeepCoreDatabase = DeepCoreDatabase(driver)

    override suspend fun <T> read(block: () -> T): T = withContext(io) { block() }

    override suspend fun <T> transaction(block: () -> T): T = withContext(io) {
        database.transactionWithResult { block() }
    }

    override fun <T : Any> observe(query: Query<T>): Flow<List<T>> =
        query.asFlow().mapToList(io)

    override fun <T> rawQuery(
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
        mapper: (SqlCursor) -> T,
    ): List<T> = driver.executeQuery(
        identifier = null,
        sql = sql,
        mapper = { cursor ->
            QueryResult.Value(buildList {
                while (cursor.next().value) add(mapper(cursor))
            })
        },
        parameters = parameters,
        binders = binders,
    ).value

    override fun rawExecute(
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): Long = driver.execute(null, sql, parameters, binders).value

    override fun close() {
        driver.close()
    }
}
