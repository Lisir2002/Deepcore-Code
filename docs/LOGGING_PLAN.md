# LOGGING_PLAN.md — 日志系统实施规划

> 状态：**规划定稿**。设计口径见 `LOGGING_SYSTEM_DESIGN.md`（决策 D1–D25）。
> 每阶段任务 → 产出 → 验证方式，逐段推进，段内完成后更新状态。

---

## 阶段总览

| 阶段 | 内容 | 依赖 | 验证 |
| --- | --- | --- | --- |
| P1 | `core:logging` 纯 Kotlin 骨架 | 无 | JVM 单测 |
| P2 | 注册模块 + 构建配置 | P1 | `:core:logging:compileKotlin` |
| P3 | Android 实现（Sinks/CrashVault/Exporter/权限） | P1,P2 | 编译 |
| P4 | 装配（Application/Koin/权限引导） | P3 | 编译 |
| P5 | UI（设置页导出 + 崩溃弹窗） | P3,P4 | 编译 |
| P6 | 埋点六类 + 模块登记 | P1 | 编译 |
| P7 | R8/proguard 策略 | P3 | 编译 |
| P8 | 全量验证（单测 + 编译） | 全部 | 单测 + 编译 |

---

## P1 — core:logging 纯 Kotlin 骨架

任务：
1. 新建 `core/logging/build.gradle.kts`（kotlin-jvm + serialization，同 core:model 风格）。
2. 核心类型：
   - `LogLevel`（V/D/I/W/E）
   - `LogCategory`（五分类）+ `LogSubCategory`（子类，含 `SECURITY.CRASH_CAUSE` 等，`category.sub` 组合）
   - `LogEntry`（ts/lvl/cat/tag/msg/thr/ex）→ JSON 行序列化
   - `Logger` 接口（v/d/i/w/e + category 变体）
   - `Log` 门面（object，持 Sink 链 + ModuleRegistry + Redactor）
   - `LogSink` 接口
   - `ModuleRegistry`（模块名 → tag 前缀）
   - `RingBuffer`（固定 200 条，线程安全，崩溃 dump）
   - `Redactor`（脱敏：凭据/路径/正文/设备标识/URL）
3. 单测：Log 门面路由、RingBuffer 覆盖、Redactor 规则、LogEntry JSON 序列化。

产出：`core/logging/**` 全部源码 + 测试。

验证：`:core:logging:test` 全绿；`:core:logging:compileKotlin` 通过。

---

## P2 — 注册模块与构建配置

任务：
1. `settings.gradle.kts` 加 `include(":core:logging")`。
2. 根 `build.gradle.kts` 确认 kotlin-jvm/serialization 插件可用（已 apply false）。
3. 确认 `gradle/libs.versions.toml` 无需新增依赖（stdlib + serialization 现有可用）。

产出：构建图含 `:core:logging`。

验证：`./gradlew :core:logging:compileKotlin` 通过。

---

## P3 — Android 实现

任务（app 层，`app/src/main/kotlin/com/deepcode/agent/logging/`）：
1. `LogcatSink`：写 `android.util.Log`；仅 debug 装配（BuildConfig.DEBUG）。
2. `RollingFileSink`：
   - 私有目录 `filesDir/logs/` + 公共根目录 `/sdcard/deepcorefile/logs/` **实时双写**
   - JSON 行写入；`app.log` + `danger.log`（SECURITY 镜像）；滚动 `1MB × 5`
   - 根目录顶层 `README.txt`；权限未授权时跳过根目录只写私有
3. `CrashVault`：
   - `install()` 首行注册 `uncaughtExceptionHandler`
   - 写 `crash-*.log`（人类可读）+ `crash-*-context.txt`（RingBuffer + 文件尾部）
   - 崩溃标记写入；ANR watchdog（主线程心跳，超时 dump 栈）
4. `LogExporter`：
   - 四层导出包（栈/上下文/环境/事件流 200 条脱敏）→ zip → share
   - 环境信息收集（Build/设备/MCP server 列表）
   - 手动"同步到根目录"
5. 权限处理：`MANAGE_EXTERNAL_STORAGE`（API 30+）/ `WRITE_EXTERNAL_STORAGE`（≤29）+ legacy 声明；降级逻辑。

产出：app 层 5 个实现类。

验证：`./gradlew :app:compileDebugKotlin` 通过。

---

## P4 — 装配

任务：
1. `DeepCoreCodeApp.onCreate()` **第一行** `CrashVault.install(this)`。
2. Koin 或显式初始化：plant `LogcatSink`（debug）+ `RollingFileSink`（全构建）→ `Log` 门面。
3. 模块登记：`ModuleRegistry` 集中登记各模块 tag 前缀。
4. 权限检测 + 降级 + 设置页引导状态暴露（DataStore/SharedPreferences）。
5. `AndroidManifest.xml` 声明权限。

产出：App 启动装配完成，崩溃捕获生效。

验证：`./gradlew :app:compileDebugKotlin` 通过；真机/模拟器安装验证崩溃捕获（手动抛异常）。

---

## P5 — UI

任务：
1. 设置页（feature:settings）加"日志"区块：
   - 导出日志（LogExporter share）
   - 立即同步到根目录
   - 权限状态 + 授权引导入口
2. 崩溃后弹窗：MainActivity/首屏检测崩溃标记 → AlertDialog"导出日志？"。

产出：设置页 + 崩溃弹窗。

验证：`./gradlew :feature:settings:compileDebugKotlin :app:compileDebugKotlin` 通过。

---

## P6 — 埋点六类 + 模块登记

任务（替换零散 `android.util.Log` + 新增关键埋点）：
1. Agent 循环：迭代/工具调用/异常 → OPERATION.AGENT（局部详细）。
2. MCP：连接/握手/tools/list/tools/call 成败耗时 → OPERATION.MCP；headers 脱敏。
3. 权限/沙箱：PermissionGate 放行/拒绝、Sandbox 白名单、Workspace 越界 → SECURITY.PERMISSION。
4. 设置：MCP server 增删改、导出 → OPERATION.USER。
5. 生命周期：App 启动/停止/前后台 → STATE.LIFECYCLE。
6. 数据层：SQLite 读写（操作/表/耗时/影响行数）→ OPERATION.DATA。
7. `ModuleRegistry` 集中登记全部前缀。
8. 移除 `SignatureGuard`/`DeepCoreCodeApp` 里零散 `android.util.Log`，统一走门面。

产出：六类埋点接入。

验证：`./gradlew compileDebugKotlin`（受影响模块）通过；日志文件出现各分类。

实施记录（P6）：
- [x] 依赖：core:agent/mcp/platform/data + feature:settings 均加 `implementation(project(":core:logging"))`。
- [x] Agent 循环（`DefaultAgentRuntime.kt`）：turn 开始/END_TURN 迭代与用量/上下文压缩/MAX_TURNS/取消/失败（ERROR_EXCEPTION）/未知工具（ERROR_FAILURE）/工具批准·成功·失败（OPERATION_AGENT）+ 权限门拒绝（SECURITY_PERMISSION）。
- [x] 权限门（`InteractivePermissionGate.kt`）：策略放行/只读放行/等待审批/用户批准·拒绝 → SECURITY.PERMISSION。
- [x] MCP（`McpServerManager.kt` + `HttpJsonRpcMcpClient.kt`）：握手/连接成功·失败/工具数/list·call 成败耗时 → OPERATION.MCP（headers/URL 由 Redactor 脱敏）。
- [x] 权限/沙箱（`LocalDirWorkspace.kt` + `CommandWhitelistSandbox.kt`）：Workspace 越界 → SECURITY_INTEGRITY；写/删文件 → OPERATION_DATA；白名单拒绝 → SECURITY_PERMISSION；命令执行 → OPERATION_SANDBOX。
- [x] 设置（`SettingsViewModel.kt`）：URL 校验拒绝/增删改/重连 → OPERATION.USER。
- [x] 数据层（`SQLiteEventStore.kt`）：events 表写/读行数 + 耗时 → OPERATION.DATA。
- [x] 替换零散 `android.util.Log`：`SignatureGuard`/`DeepCoreCodeApp` 统一走门面（SECURITY_INTEGRITY）。
- [x] 模块登记已在 P4 完成（`DeepCoreCodeApp.setupLogging()`）。

验证结果（P6）：`core:agent/mcp/data/platform + feature:settings + :app compileDebugKotlin` 全部通过；
`:core:logging:test` 全绿（RingBuffer/Redactor/LogEntry/LogTest）。agent/mcp/data 的测试编译依赖
（turbine/mockwebserver/sqlite-driver jar）在沙箱内未缓存且 dl.google.com 不可达，未能执行，属环境限制、非代码问题。

---

## P7 — R8/proguard 策略

任务：
1. `proguard-rules.pro`：`keep` `core:logging` 核心类（Log/LogEntry/LogSink 等）避免混淆影响序列化与栈可读。
2. 保留门面 W/E 调用（release 落盘）；V/D/I 由文件 sink 过滤（不落文件）。
3. 确认 LogcatSink 仅 debug（BuildConfig.DEBUG 判据天然裁剪）。

产出：R8 规则调整。

验证：`./gradlew :app:assembleRelease`（若可）通过；release 崩溃日志仍落盘。

---

## P8 — 全量验证

任务：
1. `:core:logging:test` 全绿（RingBuffer/Redactor/JSON/门面路由/登记）。
2. 全模块编译：`./gradlew :app:compileDebugKotlin` + 受影响 feature/core 模块。
3. 真机/模拟器手动验证清单：
   - 正常日志 → 私有 + 根目录双写出现 app.log / danger.log
   - 手动抛异常 → 崩溃后下次启动弹窗 → 导出包四层齐全
   - 未授权根目录权限 → 降级私有，设置页引导
   - release 包崩溃 → 崩溃日志仍落盘

产出：全量验证通过。

---

## 状态记录

- [x] P1 core:logging 骨架
- [x] P2 注册模块
- [x] P3 Android 实现
- [x] P4 装配（Application 首行 install + Koin 注册 RollingFileSink/LogExporter/LoggingActions + manifest 权限）
- [x] P5 UI（设置页"日志"区块 + MainActivity 崩溃弹窗）
- [x] P6 埋点（六类全埋：Agent/MCP/权限沙箱/设置/数据层 + 生命周期启动日志；零散 android.util.Log 已替换）
- [x] P7 R8 策略（proguard-rules.pro 七·六 节：keep core:logging + app.logging）
- [~] P8 全量验证（core:logging 单测全绿；:app:compileDebugKotlin 通过；真机手动验证清单待设备）
