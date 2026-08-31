package com.deepcode.core.data.db

/**
 * 一次增量迁移：把数据库推进到 [version]。
 *
 * 铁律（详见 DATA_LAYER.md 第五节）：
 *   1. [version] 全局唯一、严格大于核心 schema 版本，且**只增不改**。
 *   2. [statements] 只会从"上一版本"执行一次；已发布的迁移**永不修改**——
 *      要改就在迁移链尾部追加新的 Migration。
 *   3. 删字段/改类型这类危险操作，一律走"建新表 → 拷数据 → 改名"三段式，
 *      禁止直接 DROP 带数据的列。
 */
interface Migration {
    /** 目标 schema 版本号：执行完后 `PRAGMA user_version` 落到这个值。 */
    val version: Int

    /** 按顺序执行的 SQL，通常是 `ALTER TABLE ...` / `CREATE INDEX IF NOT EXISTS ...` / 数据回填。 */
    val statements: List<String>
}

/**
 * 迁移的便捷实现，省掉每次写一个匿名对象。
 *
 * ```kotlin
 * Migration(2, "ALTER TABLE bookmarks ADD COLUMN note TEXT")
 * ```
 */
fun Migration(version: Int, vararg statements: String): Migration =
    SimpleMigration(version, statements.toList())

private class SimpleMigration(
    override val version: Int,
    override val statements: List<String>,
) : Migration

/**
 * ★ 数据层扩展协议：一个功能模块声明自己的表与演进历史。
 *
 * 新功能要持久化时，**核心框架一行不改**，只需三步：
 *   1. 写 `object XxxTableModule : TableModule`，声明 [ddl]（首装建表）与 [migrations]（后续加字段）
 *   2. 写该表的 DAO——用 [SqliteDatabase.rawQuery] / [SqliteDatabase.rawExecute]，
 *      或让该 feature 自建 SQLDelight Database 共享同一个 SqlDriver
 *   3. 在 `:app` 的 DI 装配处把模块注册进 SchemaManager
 *
 * 约定：
 *   • [ddl] 每一条都必须是幂等的（`CREATE TABLE/INDEX IF NOT EXISTS ...`）。
 *     它会在**每次启动时重跑一遍做自愈**，非幂等语句会直接炸给你看。
 *   • [name] 全局唯一，仅用于日志与注册校验。
 *   • 首装（user_version == 0）只跑 [ddl] 直接建到最新形态，跳过整条迁移链；
 *     升级时先按顺序补跑 [migrations] 中尚未到达的版本，再跑一遍 [ddl]
 *     做幂等自愈（表已存在则 DDL 是 no-op，不影响已写入的数据）。
 */
interface TableModule {
    /** 模块标识，全局唯一。 */
    val name: String

    /** 首装建表语句（幂等）。 */
    val ddl: List<String>

    /** 增量迁移链，按 [Migration.version] 升序执行，只跑尚未到达的版本。 */
    val migrations: List<Migration> get() = emptyList()
}
