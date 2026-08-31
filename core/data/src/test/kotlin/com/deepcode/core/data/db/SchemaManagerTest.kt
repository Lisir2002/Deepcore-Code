package com.deepcode.core.data.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.deepcode.core.data.event.SQLiteEventStore
import com.deepcode.core.data.tableNames
import com.deepcode.core.data.testDatabase
import com.deepcode.core.data.turnStarted
import com.deepcode.core.data.userVersion
import com.deepcode.core.model.SessionId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 数据层"新功能接入协议"的验收测试。
 *
 * 这里用一个假的 bookmarks 模块，把 M2/M3 真正要走的接入路径完整跑一遍：
 * 首装建表 → 加列 → 加第二列，全程核心框架零改动。
 */
class SchemaManagerTest {

    /** 最新形态：path / created_at / note / pinned */
    private object BookmarksModule : TableModule {
        override val name = "bookmarks"
        override val ddl = listOf(
            """
            CREATE TABLE IF NOT EXISTS bookmarks (
              path       TEXT PRIMARY KEY,
              created_at INTEGER NOT NULL,
              note       TEXT,
              pinned     INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
            "CREATE INDEX IF NOT EXISTS idx_bookmarks_created ON bookmarks(created_at)",
        )
        override val migrations = listOf(
            // ★ 建的是"v2 那一版"的形态（还没有 pinned），不是最新形态：
            //   老库里没这张表，只写 ALTER 必炸；而建成最新形态，下面的 ALTER 又会撞重复列。
            Migration(
                2,
                """
                CREATE TABLE IF NOT EXISTS bookmarks (
                  path       TEXT PRIMARY KEY,
                  created_at INTEGER NOT NULL,
                  note       TEXT
                )
                """.trimIndent(),
            ),
            Migration(3, "ALTER TABLE bookmarks ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0"),
        )
    }

    @Test
    fun `首装：核心表建好，user_version 落在核心版本`() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val manager = SchemaManager(driver)

        manager.migrate()

        assertEquals(1, manager.schemaVersion)
        assertEquals(1L, driver.userVersion())
        // sqlite_sequence 是 AUTOINCREMENT 的副产物，不算业务表
        assertEquals(setOf("events", "sessions"), driver.tableNames() - "sqlite_sequence")
    }

    @Test
    fun `首装带模块：模块表一起建好，且不重复跑迁移链`() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val manager = SchemaManager(driver, modules = listOf(BookmarksModule))

        manager.migrate()

        assertEquals(3, manager.schemaVersion)       // 核心 1，模块迁移到 3
        assertEquals(3L, driver.userVersion())
        assertTrue("bookmarks" in driver.tableNames())
        // 首装即最新形态：DDL 已带 pinned，迁移链的 ALTER 不该重复执行
        assertEquals(4, driver.columnsOf("bookmarks").size)
    }

    @Test
    fun `升级：v1 老库加载带迁移的模块后补列成功，业务数据零丢失`() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

        // v1 时代：只有核心表，且已经存了业务数据
        val v1 = testDatabase(driver)
        SQLiteEventStore(v1).append(turnStarted(ts = 42))
        assertEquals(1L, driver.userVersion())

        // 装上 bookmarks 模块重新打开：走迁移链 v1 → v2 → v3
        val v3 = testDatabase(driver, modules = listOf(BookmarksModule))
        assertEquals(3L, driver.userVersion())

        // 老数据在
        assertEquals(1, SQLiteEventStore(v3).loadEvents(SessionId("s1")).size)
        // 新表可用，且是最新形态（note + pinned 都在）
        v3.transaction {
            v3.rawExecute("INSERT INTO bookmarks(path, created_at, note, pinned) VALUES (?, ?, ?, ?)", 4) {
                bindString(0, "/app/Main.kt")
                bindLong(1, 7L)
                bindString(2, "记一下")
                bindLong(3, 1L)
            }
        }
        val rows = v3.read {
            v3.rawQuery("SELECT path, note, pinned FROM bookmarks") { cursor ->
                Triple(cursor.getString(0)!!, cursor.getString(1), cursor.getLong(2))
            }
        }
        assertEquals(listOf(Triple("/app/Main.kt", "记一下", 1L)), rows)
    }

    @Test
    fun `重复 migrate 是幂等的（每次启动都跑，不能炸）`() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val manager = SchemaManager(driver, modules = listOf(BookmarksModule))

        manager.migrate()
        manager.migrate()
        manager.migrate()

        assertEquals(3L, driver.userVersion())
        assertEquals(4, driver.columnsOf("bookmarks").size)
    }

    @Test
    fun `库版本高于程序支持版本时拒绝降级打开`() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SchemaManager(driver).migrate()
        driver.execute(null, "PRAGMA user_version = 99", 0)

        val error = assertFailsWith<IllegalArgumentException> { SchemaManager(driver).migrate() }

        assertTrue(error.message!!.contains("拒绝降级打开"))
    }

    @Test
    fun `两个模块占用同一个迁移版本号时立刻报错`() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val clash = listOf(
            object : TableModule {
                override val name = "m1"
                override val ddl = listOf("CREATE TABLE IF NOT EXISTS m1(id TEXT PRIMARY KEY)")
                override val migrations = listOf(Migration(2, "ALTER TABLE m1 ADD COLUMN a TEXT"))
            },
            object : TableModule {
                override val name = "m2"
                override val ddl = listOf("CREATE TABLE IF NOT EXISTS m2(id TEXT PRIMARY KEY)")
                override val migrations = listOf(Migration(2, "ALTER TABLE m2 ADD COLUMN b TEXT"))
            },
        )

        val error = assertFailsWith<IllegalArgumentException> { SchemaManager(driver, modules = clash) }

        assertTrue(error.message!!.contains("迁移版本号冲突"))
    }

    @Test
    fun `模块迁移版本号必须严格大于核心版本`() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val bad = object : TableModule {
            override val name = "legacy"
            override val ddl = listOf("CREATE TABLE IF NOT EXISTS legacy(id TEXT PRIMARY KEY)")
            override val migrations = listOf(Migration(1, "ALTER TABLE legacy ADD COLUMN a TEXT"))
        }

        val error = assertFailsWith<IllegalArgumentException> { SchemaManager(driver, modules = listOf(bad)) }

        assertTrue(error.message!!.contains("必须严格大于核心 schema 版本"))
    }

    @Test
    fun `模块名重复时立刻报错`() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val dup = listOf(
            object : TableModule {
                override val name = "same"
                override val ddl = listOf("CREATE TABLE IF NOT EXISTS a(id TEXT PRIMARY KEY)")
            },
            object : TableModule {
                override val name = "same"
                override val ddl = listOf("CREATE TABLE IF NOT EXISTS b(id TEXT PRIMARY KEY)")
            },
        )

        assertTrue(assertFailsWith<IllegalArgumentException> { SchemaManager(driver, modules = dup) }
            .message!!.contains("名称重复"))
    }

    @Test
    fun `没有迁移的纯新模块也能建表（DDL 幂等补建）`() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SchemaManager(driver).migrate()   // v1
        val simple = object : TableModule {
            override val name = "settings"
            override val ddl = listOf("CREATE TABLE IF NOT EXISTS settings(key TEXT PRIMARY KEY, value TEXT)")
        }

        SchemaManager(driver, modules = listOf(simple)).migrate()

        assertTrue("settings" in driver.tableNames())
        assertEquals(1L, driver.userVersion())   // 没有迁移就不推版本号
    }

    // ─────────────────────────── 工具 ───────────────────────────

    private fun app.cash.sqldelight.db.SqlDriver.columnsOf(table: String): List<String> =
        executeQuery(
            identifier = null,
            sql = "SELECT name FROM pragma_table_info('$table')",
            mapper = { cursor ->
                app.cash.sqldelight.db.QueryResult.Value(
                    buildList { while (cursor.next().value) add(cursor.getString(0)!!) }
                )
            },
            parameters = 0,
        ).value
}
