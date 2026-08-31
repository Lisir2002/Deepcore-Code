package com.deepcode.core.data.db

import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema

/**
 * Schema 管家：把"核心表（SQLDelight 生成）"和"各功能模块的注册制表"缝成一条版本链。
 *
 * 版本号三件事：
 *   • 核心版本 = [coreSchema].version，由 `:core:data` 的 `.sq` / `.sqm` 管理
 *   • 模块迁移版本 = [TableModule.migrations]，必须**严格大于**核心版本且全局唯一
 *   • 落库版本 = [schemaVersion] = max(核心版本, 最大模块迁移版本)，写进 `PRAGMA user_version`
 *
 * 打开流程（[migrate]）：
 *   • 首装（user_version == 0）：建核心表 + 跑全部模块 DDL（DDL 即最新形态，**跳过迁移链**）
 *   • 升级：先按顺序补跑尚未到达的迁移，再跑一遍模块 DDL 做幂等自愈
 *   • 发现库版本高于程序支持版本：直接抛错，绝不降级打开
 *
 * ★ 模块作者必读（两条路必须殊途同归）：
 *   • [TableModule.ddl] 是**最新形态**，只在首装时执行，直接建到最新。
 *   • [TableModule.migrations] 是**升级路径**，第一个迁移必须写
 *     `CREATE TABLE IF NOT EXISTS ...`（**那一版**的形态，不是最新形态），
 *     后续迁移再按版本逐个 ALTER。老库里根本没有你的表，只写 ALTER 必炸；
 *     而第一个迁移就建成最新形态，后面的 ALTER 会撞"重复列"。
 */
class SchemaManager(
    private val driver: SqlDriver,
    private val coreSchema: SqlSchema<QueryResult.Value<Unit>> = DeepCoreDatabase.Schema,
    private val modules: List<TableModule> = emptyList(),
) {

    /** 核心 schema 版本（由 SQLDelight 的 .sq/.sqm 决定）。 */
    val coreVersion: Int = coreSchema.version.toInt()

    /** 全局 schema 版本：核心版本与所有模块迁移版本取最大值。 */
    val schemaVersion: Int =
        maxOf(coreVersion, modules.flatMap { it.migrations }.maxOfOrNull { it.version } ?: 0)

    /** 有序迁移链（跨模块合并后按版本号升序，同一版本号内按注册顺序）。 */
    val migrationChain: List<Migration> =
        modules.flatMap { module -> module.migrations.map { module.name to it } }
            .sortedWith(compareBy({ it.second.version }, { it.first }))
            .map { it.second }

    // 事务载体：直接用生成的 Database（它本身就是 Transacter），不额外造包装类
    private val transacter: Transacter = DeepCoreDatabase(driver)

    init {
        val names = modules.map { it.name }
        require(names.distinct().size == names.size) { "TableModule 名称重复：$names" }

        val versions = modules.flatMap { it.migrations }.map { it.version }
        require(versions.distinct().size == versions.size) {
            "迁移版本号冲突（每个版本号只能被一个模块占用）：$versions"
        }
        val illegal = versions.filter { it <= coreVersion }
        require(illegal.isEmpty()) {
            "迁移版本号必须严格大于核心 schema 版本($coreVersion)，非法版本：$illegal。" +
                "核心版本由 SQLDelight 管理，模块迁移只能排在它后面。"
        }
    }

    /** 建表 / 迁移。必须在任何查询之前调用一次。 */
    fun migrate() {
        val current = readUserVersion()
        require(current <= schemaVersion.toLong()) {
            "数据库版本($current) 高于本程序支持的版本($schemaVersion)：拒绝降级打开。"
        }

        transacter.transaction {
            if (current == 0L) {
                // 2a) 首装：核心表 + 模块表都直接建到最新形态，跳过整条迁移链
                coreSchema.create(driver)
                applyModuleDdl()
            } else {
                // 2b) 升级：先按顺序补跑尚未到达的迁移，再用 DDL 幂等补建（自愈）
                migrationChain.filter { it.version > current }.forEach { migration ->
                    migration.statements.forEach { sql -> driver.execute(null, sql, 0) }
                }
                applyModuleDdl()
            }

            // 3) 落版本
            setUserVersion(schemaVersion)
        }
    }

    /** 幂等建表：只负责"补上缺失的表/索引"，绝不 ALTER 已存在的表。 */
    private fun applyModuleDdl() {
        modules.forEach { module -> module.ddl.forEach { sql -> driver.execute(null, sql, 0) } }
    }

    private fun readUserVersion(): Long =
        driver.executeQuery(
            identifier = null,
            sql = "PRAGMA user_version",
            mapper = { cursor ->
                QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
            },
            parameters = 0,
        ).value

    private fun setUserVersion(version: Int) {
        driver.execute(null, "PRAGMA user_version = $version", 0)
    }
}
