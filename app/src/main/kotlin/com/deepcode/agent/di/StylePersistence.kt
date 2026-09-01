package com.deepcode.agent.di

import com.deepcode.core.data.db.Migration
import com.deepcode.core.data.db.SqliteDatabase
import com.deepcode.core.data.db.TableModule
import com.deepcode.designsystem.theme.StylePreferenceStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 设置项 KV 表（§7.1 StyleController 持久化，注册制 TableModule）。
 *
 * 只存字符串键值对（当前：ui.style_active_spec / ui.style_dark_mode）。整表单行事务
 * 已被 SqliteDatabase 包住，业务不碰连接。
 */
object SettingsTableModule : TableModule {
    override val name: String = "style_settings"

    override val ddl: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS style_settings (" +
            "key TEXT PRIMARY KEY, " +
            "value TEXT NOT NULL" +
            ")",
    )

    // v1 尚无增量迁移；后续加字段时在此追加 Migration。
    override val migrations: List<Migration> = emptyList()
}

/**
 * StyleController 持久化实现：委托 SqliteDatabase（:app 侧的 Android SqlDriver 已在 DI 里）。
 * rawQuery / rawExecute 必须放进 read/transaction 块，由 db 切到 IO 线程。
 */
class SqliteStylePreferenceStore(private val db: SqliteDatabase) : StylePreferenceStore {

    private val mutex = Mutex()   // read/write 之间极薄，DB 单线程兜底，这里仅防偶发并发

    override suspend fun read(key: String): String? = mutex.withLock {
        db.read {
            db.rawQuery(
                sql = "SELECT value FROM style_settings WHERE key = ?",
                parameters = 1,
                binders = { bindString(1, key) },
            ) { cursor -> if (cursor.next().value) cursor.getString(0) else null }
                .firstOrNull()
        }
    }

    override suspend fun write(key: String, value: String) = mutex.withLock {
        db.transaction {
            db.rawExecute(
                sql = "INSERT OR REPLACE INTO style_settings(key, value) VALUES(?, ?)",
                parameters = 2,
                binders = { bindString(1, key); bindString(2, value) },
            )
            Unit
        }
    }
}