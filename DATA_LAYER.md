# DATA_LAYER.md — 数据层设计定稿（SQLite）

> 决策日期：2026-08-31。本文是数据层方案的**定稿记录**，回答"用什么、怎么分层、
> 新功能怎么接入"。实现细节以代码与本文为准；版本演进决策记入 CHANGELOG。

---

## 一、决策记录

| # | 决策项 | 结论 |
| --- | --- | --- |
| 1 | 存储基础 | **SQLite 接管数据层**（替代内存实现成为唯一持久化基座） |
| 2 | 技术栈 | **SQLDelight 2.x**（SQL-first，纯 Kotlin） |
| 3 | 事件存储 | `payload` **JSON blob**（kotlinx.serialization 多态）+ `type` 列过滤 |
| 4 | 会话索引 | `sessions` 表**本期引入**，与事件流同事务更新 |
| 5 | 写入一致性 | 状态表与事件流**统一事务边界** |
| 6 | 扩展协议 | **`TableModule` 注册制**：新功能加表/加字段不改核心框架 |

## 二、选型结论（为什么是 SQLDelight）

| 维度 | SQLDelight 2.x（✅ 选定） | Room 2.7 KMP | 裸 SQLite |
| --- | --- | --- | --- |
| `:core:data` 纯 Kotlin 铁律 | 天生 KMP，JVM 单测零摩擦 | 需 KSP + room-runtime | ✅ 但欠账多 |
| 类型安全 | `.sq` 编译期校验 | `@Query` 编译期校验 | 无 |
| Flow 表观测 | ✅ | ✅ | 手写 |
| 迁移 | `.sqm` 版本化迁移 | Migration/AutoMigration | 全手写 |
| Android 端运行时 | `AndroidSqliteDriver` 接系统 SQLite | 内置 | 内置 |

关键动机：`:core:agent`/`:core:uistate` 的单测能在 JVM 秒级跑通是本项目最重要的工程资产，
数据层必须继承这个能力——SQLDelight 在 JVM 用内存库即可跑全部迁移链与 DAO 测试，
CI 无需模拟器。Android 端由 `:app` 装配 `AndroidSqliteDriver`，不引入额外运行时。

## 三、分层架构（铁律不破）

```
:core:data（纯 Kotlin —— 契约 + 通用实现，零 Android 依赖）
├─ EventStore               ← 现有接口不动（append/appendAll/loadEvents/observe/clear）
├─ InMemoryEventStore       ← 保留，专供单测
├─ event/SQLiteEventStore   ← events 表实现 EventStore（通用实现）
└─ db/
   ├─ SqliteDatabase        ← 门面 SPI：连接获取、withTransaction、Flow 观测
   ├─ SchemaManager         ← SCHEMA_VERSION + 有序迁移链（版本只增不改历史）
   └─ TableModule           ← ★ 新表接入协议（见第五节）

:app（唯一知道 Android 的模块）
└─ 装配：AndroidSqliteDriver + 注册全部 TableModule + EventStore = SQLiteEventStore
```

**铁律**：
1. 事件流仍是唯一契约——SQLite 存的是事件日志，不是"最终状态快照"。
2. `:core:data` 零 Android 依赖；只有 `:app` 能创建 Android 驱动。
3. 业务代码禁止自开 SQLite 连接——一切读写必须经由 `SqliteDatabase` 门面或注册过的表模块 DAO。
4. 迁移文件永不修改：加字段只允许在迁移链**尾部追加**。

## 四、首版 Schema（v1）

```sql
-- 会话索引：会话列表页不必全量重放事件流
CREATE TABLE sessions (
  id          TEXT PRIMARY KEY,   -- SessionId.value
  title       TEXT NOT NULL,
  created_at  INTEGER NOT NULL,   -- epoch millis
  updated_at  INTEGER NOT NULL
);
CREATE INDEX idx_sessions_updated ON sessions(updated_at DESC);

-- 事件日志：append-only（架构地基，见 ARCHITECTURE.md §二）
CREATE TABLE events (
  seq         INTEGER PRIMARY KEY AUTOINCREMENT,  -- 全局单调，保证重放顺序
  session_id  TEXT NOT NULL,
  turn_id     TEXT,
  ts          INTEGER NOT NULL,
  type        TEXT NOT NULL,      -- 事件类型名（@Serializable class 简名）
  payload     TEXT NOT NULL       -- 完整 JSON（kotlinx.serialization 多态编码）
);
CREATE INDEX idx_events_session_seq ON events(session_id, seq);
```

设计要点：
- **事件维度永远不改表**：新增事件子类（M1–M4 每个里程碑都会加）对 schema 零影响，
  `type` 列仅供过滤/统计，不承载结构。
- `sessions` 与 `events` 同库同事务：重命名会话 = 状态行更新 + `SessionRenamed` 事件
  append 在**同一事务**提交，索引与事实流永不脱节（决策 #5）。
- `payload` 用 JSON blob 而非列式展开（决策 #3）：列式方案在 20+ 且持续增长的事件
  类型下会导致每次加事件都改表，与事件流架构相性最差。

## 五、★ TableModule：新功能数据表接入协议

后续每个功能（M2 文件树/收藏、M3 设置项、知识库……）接入数据层被标准化为**注册制**：

```kotlin
/** 一个功能模块声明自己的表与迁移，核心框架零改动 */
interface TableModule {
    val name: String                 // 标识（日志/校验用）
    val ddl: List<String>            // 首装建表：CREATE TABLE IF NOT EXISTS ...
    val migrations: List<Migration>  // 增量迁移：加字段/加索引，按版本有序
}

interface Migration {
    val version: Int                 // 目标 schema 版本号
    val statements: List<String>     // ALTER TABLE ... / CREATE INDEX ...
}
```

**接入操作流程（新功能要持久化数据时）：**

1. 新建 `XxxTableModule : TableModule`，声明 DDL（首装）与后续迁移（加字段）。
2. 写该表的 DAO/Repository（SQLDelight 生成类型安全查询）。
3. 在 `:app` 的 DI 装配处把模块注册进 `SchemaManager`——**核心框架一行不改**。
4. 测试：JVM 内存库按注册顺序跑全量 DDL + 迁移链，直接断言行为。

**字段演进规则：**

| 场景 | 动作 | 禁止 |
| --- | --- | --- |
| 新功能加表 | 注册新 TableModule | 改核心 schema 文件 |
| 老表加字段/索引 | 迁移链尾部追加 `ALTER TABLE`/`CREATE INDEX` | 修改历史迁移、重排版本号 |
| 废弃字段 | 迁移链追加复制-清理语句（数据安全第一） | 直接 DROP 历史数据 |

## 六、EventStore 整合与事务边界

```kotlin
// SQLiteEventStore：events 表 + sessions 表联动
class SQLiteEventStore(private val db: SqliteDatabase) : EventStore {
    override suspend fun append(event: AgentEvent) = db.withTransaction {
        insertEvent(event)                       // events 表
        touchSession(event.sessionId, event.ts)  // sessions.updated_at（同事务）
    }
    // loadEvents: WHERE session_id=? ORDER BY seq；重放即恢复
    // observe: SQLDelight 表观测 Flow（或写入时 emit，与现实现行为一致）
    // clear: 事务内删 events + 删 sessions 行
}
```

迁移路径：首版无线上数据，直接以 v1 schema 起步；`InMemoryEventStore` 保留供单测；
`:app` 一处绑定切换，上层业务零改动（EventStore 接口未动）。

## 七、实施清单（下个里程碑落地）

- [ ] `:core:data` 引入 SQLDelight Gradle 插件与 `.sq` 定义（sessions/events）
- [ ] `SqliteDatabase` / `SchemaManager` / `TableModule` / `Migration` SPI 落地
- [ ] `SQLiteEventStore` 实现 + JVM 内存库全链路单测（含迁移链）
- [ ] `:app` 装配 Android 驱动（`AndroidSqliteDriver`）与模块注册
- [ ] 会话列表读路径接 `sessions` 表（M1 的会话列表页前置就绪）
- [ ] CI：JVM 数据层测试纳入 `ci.yml`（core-test job）
