# LOGGING_SYSTEM_DESIGN.md — 日志系统设计

> 状态：**设计定稿**（六个话题已与用户逐项确认，见 §2）。
> 实施进度记录在 `LOGGING_PLAN.md`。本文档是日志系统的唯一权威口径。

---

## 一、背景与目标

### 1.1 痛点

真机运行 App 直接闪退，拿不到日志与错误详情，严重阻碍迭代。根因盘点：

| 根因 | 说明 |
| --- | --- |
| 无统一日志层 | 全项目仅 6 处零散 `android.util.Log.w/i`，无分级/无 tag 规范 |
| 无全局崩溃捕获 | 没有 `Thread.setDefaultUncaughtExceptionHandler`，崩溃直接消失 |
| 日志不落盘 | 只进 logcat，真机崩溃后环形缓冲被冲掉 |
| Release 移除全部日志 | `proguard-rules.pro` 用 `assumenosideeffects` 删掉所有 `Log.*` |
| 启动早期崩溃 | 签名校验 `Tampered` 直接抛异常，任何初始化前崩溃无迹可查 |

### 1.2 目标

1. 任何构建（debug/release）崩溃都能留下崩溃栈与上下文，可一键导出。
2. 统一日志入口 + 分类，覆盖现有模块并为未来模块留出入口。
3. 调试日志自动落盘到外部存储，方便直接翻阅。
4. 敏感内容（凭据/对话/设备标识）脱敏，日志文件不成为泄露源。

---

## 二、已确认决策总览（2026-09-01 与用户逐项拍板）

| # | 话题 | 决策 | 结论 |
| --- | --- | --- | --- |
| D1 | 模块结构 | 代码位置 | 新建 `core:logging` **纯 Kotlin** 模块；Android 实现在 app 层 |
| D2 | 模块结构 | 调用方式 | **门面为主 + 关键类可选构造注入** `Logger` |
| D3 | 模块结构 | 未来出入口 | **模块登记机制**：Application 启动时集中登记各模块 tag 前缀 |
| D4 | 分类设计 | 分类集合 | **五分类细分到子类**（见 §4.1） |
| D5 | 分类设计 | 危险类边界 | SECURITY 含完整性/签名、权限拒绝/越界、密钥/鉴权、日志系统自身、崩溃相关原因等 |
| D6 | 分类设计 | 分类存储 | 主日志单文件 JSON 行带 `category` + **SECURITY 类镜像 `danger.log`** |
| D7 | 分类设计 | 崩溃归类 | **崩溃全归 `SECURITY.CRASH_CAUSE`**（ERROR 只记普通异常不记崩溃） |
| D8 | 外部存储 | 目录结构 | 公共根目录 `/sdcard/deepcorefile/logs/` 子目录，顶层 README |
| D9 | 外部存储 | 同步策略 | **实时双写**：私有目录 + 公共根目录各写一份 |
| D10 | 外部存储 | 权限降级 | 未授权"所有文件访问"→ 降级只写私有目录 + 设置页引导授权 |
| D11 | 外部存储 | 手动同步 | 设置页提供"立即同步日志到根目录"按钮 |
| D12 | 外部存储 | 生效范围 | **全构建**（debug+release）都写私有+根目录，不做区分；稳定后再定是否切割 |
| D13 | 崩溃导出 | 触发方式 | 崩溃后下次启动自动弹窗 + 设置页手动导出 |
| D14 | 崩溃导出 | 事件流 | 最近 200 条，对话正文脱敏（用元数据代替） |
| D15 | 崩溃导出 | 保留策略 | 崩溃记录**全保留**，不自动清理 |
| D16 | 埋点范围 | 深度 | 关键事件为主 + Agent 循环局部详细 |
| D17 | 埋点范围 | 数据层 | 读写操作级（操作/表/耗时/影响行数），OPERATION.DATA |
| D18 | 埋点范围 | LLM 元数据开关 | debug 自动开 / release 关 |
| D19 | 埋点范围 | 第一期范围 | 六类全埋：Agent / MCP / 权限沙箱 / 设置 / 生命周期 / 数据层 |
| D20 | 其他 | 登记形态 | Application 启动**集中登记** |
| D21 | 其他 | 滚动策略 | 运行时主日志 **1MB × 5** |
| D22 | 其他 | 脱敏范围 | 凭据字段 / 绝对文件路径 / 用户输入正文 / 设备标识 / URL 凭据 |
| D23 | 其他 | R8 策略 | release 文件 sink 保留 **W/E**，V/D/I 不落文件；logcat 仅 debug 装配 |
| D24 | 其他 | ANR | 一期轻量 watchdog（主线程超时记录栈） |
| D25 | 其他 | 日志格式 | 文件日志 **JSON 行**；崩溃时另 dump 人类可读 `.txt` 快照 |

---

## 三、总体架构

```
业务模块（core:agent / core:mcp / core:data / core:platform / feature:*）
        │  调用 Log.i("Tag", ...)  或  构造注入 Logger
        ▼
┌──────────────────────────────────────────────────────────┐
│ core:logging（纯 Kotlin，零 Android 依赖，可 JVM 单测）      │
│  LogLevel / LogCategory+子类 / LogEntry / Log 门面          │
│  LogSink 链 / ModuleRegistry / RingBuffer(200) / Redactor  │
└──────────────────────────────────────────────────────────┘
        │  plant 的 Sink 链（app 层装配）
        ▼
┌──────────────────────────────────────────────────────────┐
│ app 层实现（Android）                                       │
│  LogcatSink（debug）→ logcat                                │
│  RollingFileSink → 私有 filesDir/logs + /sdcard/deepcorefile/logs（实时双写）│
│  CrashVault（崩溃 + ANR watchdog + 崩溃标记）                 │
│  LogExporter（四层导出包 + share）                           │
│  崩溃弹窗 / 设置页导出入口                                    │
└──────────────────────────────────────────────────────────┘
```

**核心原则**：`core:logging` 定义"写什么、怎么组织"，app 层定义"写到哪、如何捕获"。核心层可脱离 Android 单测，Android 细节全部可替换。

---

## 四、分类模型

### 4.1 五分类 → 子类

| 大类 | 子类 | 说明 | 典型事件 |
| --- | --- | --- | --- |
| `SECURITY` 危险 | `INTEGRITY` | 完整性/签名 | 签名校验失败、Tampered |
| | `PERMISSION` | 权限拒绝/越界 | PermissionGate 拒绝、Sandbox 白名单拒绝、Workspace 越界拦截 |
| | `CREDENTIAL` | 密钥/鉴权 | API Key 读取、证书校验失败、MCP 鉴权失败 |
| | `SELF` | 日志系统自身 | 外部存储导出失败、日志文件操作异常 |
| | `CRASH_CAUSE` | 应用崩溃相关原因 | **全部崩溃**（含崩溃上下文根因） |
| | `OTHER` | 其他 | 未归类危险事件 |
| `OPERATION` 操作 | `AGENT` | Agent 循环/工具调用 | 每次迭代、工具调用前后 |
| | `MCP` | 协议连接 | 握手、tools/list、tools/call 成败耗时 |
| | `DATA` | 数据存储读写 | SQLite 增删改查（操作/表/耗时/影响行数） |
| | `SANDBOX` | 沙箱命令执行 | 命令执行、白名单命中 |
| | `USER` | 用户操作/设置变更 | MCP server 增删改、导出日志 |
| `STATE` 状态 | `LIFECYCLE` | App/Activity 生命周期 | onCreate/onStop、前后台切换 |
| | `SESSION` | Agent 会话 | 会话开始/结束 |
| | `CONFIG` | 配置变更 | 配置读取/变更 |
| `ERROR` 错误 | `EXCEPTION` | 异常捕获 | 普通异常捕获（非崩溃） |
| | `FAILURE` | 调用失败 | 网络失败、操作失败 |
| `SYSTEM` 系统 | `INIT` | 初始化 | Koin 装配、日志系统自身初始化 |
| | `FRAMEWORK` | 框架级 | 框架内部事件 |

### 4.2 崩溃归类规则（D7）

- **所有崩溃**（`uncaughtException`）记录为 `SECURITY.CRASH_CAUSE`，写入主文件 + 镜像进 `danger.log`。
- `ERROR` 类只记普通异常/失败，**不记崩溃**，避免与 SECURITY 混淆。
- 崩溃捕获时同时 dump 崩溃前上下文（RingBuffer 200 条 + 文件尾部）。

### 4.3 存储组织（D6）

- 主日志文件：`app.log`（JSON 行，每条含 `category` 字段），滚动 `1MB × 5`。
- 危险镜像：`danger.log`（仅 SECURITY 类），同目录，同步滚动。
- 崩溃文件：`crash-YYYYMMDD-HHmmss.log`（人类可读 txt），全保留。
- 崩溃上下文：`crash-…-context.txt`（RingBuffer + 文件尾部）。

---

## 五、外部存储与权限（D8–D12）

### 5.1 目录结构

```
filesDir/logs/                      （私有，始终写）
├── app.log / app.log.1 … .5
├── danger.log / danger.log.1 … .5
└── crash-*.log / crash-*-context.txt

/sdcard/deepcorefile/logs/          （公共根目录，实时双写镜像，结构同上）
/sdcard/deepcorefile/README.txt     （顶层说明：目录用途、如何反馈日志）
```

### 5.2 权限

| Android 版本 | 权限 | 说明 |
| --- | --- | --- |
| API ≤ 29（Android 10 及以下） | `WRITE_EXTERNAL_STORAGE` + `requestLegacyExternalStorage` | 可直接写公共根目录 |
| API 30+（Android 11+） | `MANAGE_EXTERNAL_STORAGE` | 需用户在系统设置手动授权一次 |

### 5.3 降级策略（D10）

- 启动时检测权限；未授权时**只写私有目录**，日志不丢。
- 设置页显示"授权后启用根目录导出"引导入口。
- 提供"立即同步日志到根目录"手动按钮（D11），授权后一键补同步。

### 5.4 生效范围（D12）

全构建（debug + release）均写私有 + 根目录，不做区分。稳定后再由用户决定是否按构建切割。

---

## 六、崩溃捕获与导出（D13–D15）

### 6.1 CrashVault 时序

```
Application.onCreate()
  └─ CrashVault.install()   ← 第一行，super.onCreate() 之前
       ├─ Thread.setDefaultUncaughtExceptionHandler（含原 handler 链）
       ├─ 捕获时：写 crash-时间戳.log（完整栈 + 线程）
       ├─ dump RingBuffer 200 条 + 文件尾部 → crash-…-context.txt
       ├─ 写崩溃标记（DataStore/SharedPreferences）
       └─ 调用原 handler（默认终止进程）
```

必须在 `onCreate` 第一行，确保签名校验等启动早期崩溃也能捕获。

### 6.2 ANR watchdog（D24，一期轻量）

- 主线程 Handler 定时心跳：postDelayed 检查上一心跳是否被消费。
- 超时（如 5s）时 dump 主线程栈到崩溃文件，标记为 ANR（`SECURITY.CRASH_CAUSE` 或单独标记）。

### 6.3 崩溃导出包（四层）

| 层 | 内容 |
| --- | --- |
| 1 崩溃栈 | 异常类型 + 完整堆栈 + 线程名 |
| 2 崩溃前上下文 | RingBuffer 200 条 + 文件日志尾部 |
| 3 环境信息 | 设备型号 / Android 版本 / App 版本 / 构建类型 / 已登记模块 / MCP server 列表（URL 脱敏） |
| 4 最近事件流 | SQLite 最近 200 条 Agent 事件，对话正文脱敏（元数据代替） |

### 6.4 触发与保留

- 崩溃后下次启动自动弹窗"检测到崩溃，导出日志？"→ share sheet 发 zip。
- 设置页"导出日志"手动入口。
- 崩溃文件全保留（D15），用户可手动清理。

---

## 七、埋点范围（D16–D19）

第一期六类全埋，关键事件为主，Agent 循环加局部详细：

| 模块 | 埋点 | 分类 |
| --- | --- | --- |
| Agent 运行时 | 每次迭代开始/结束、模型调用、工具调用前后、异常 | OPERATION.AGENT / ERROR |
| MCP | server 连接/断开、握手、tools/list、tools/call 成败与耗时 | OPERATION.MCP |
| 权限/沙箱 | PermissionGate 放行/拒绝、Sandbox 命令白名单、Workspace 越界 | SECURITY.PERMISSION |
| 设置 | MCP server 增删改、日志导出、权限状态 | OPERATION.USER |
| 生命周期 | App 启动/停止、前后台 | STATE.LIFECYCLE |
| 数据层 | SQLite 读写（操作/表/耗时/影响行数） | OPERATION.DATA |

**LLM 内容策略**（D18）：默认只记元数据（耗时/token 数/模型名/成败），不记正文；debug 构建自动开启，release 关闭。

---

## 八、脱敏（D22）

`Redactor` 覆盖：

| 类别 | 规则示例 |
| --- | --- |
| 凭据字段 | `Authorization` / `api-key` / `token` / `secret` / `password` 值替换为 `***` |
| 绝对文件路径 | `/data/user/0/…` 替换为相对路径 |
| 用户输入正文 | LLM 元数据模式默认不记正文，此处兜底（截断 + 标记） |
| 设备标识 | IMEI / 序列号 / Android ID 替换为 hash |
| URL 凭据 | URL 中 userinfo / query 里的 token 片段脱敏 |

挂载点：写入 Sink 前统一过 Redactor；MCP headers 处显式过滤敏感 header。

---

## 九、模块登记与可扩展性（D3/D20）

- `ModuleRegistry`：模块名 → tag 前缀映射，Application 启动时集中登记。
- 已登记前缀示例：`core-agent → AgentRuntime`、`core-mcp → McpClient`、`core-data → DataStore`、`core-platform → Platform`、`feature-settings → Settings`、`app → App`。
- 未来模块接入：实现侧 `import Log` 即用 + 在登记处加一行前缀。
- 新输出目标：实现 `LogSink` + `plant()` 一行接入。

---

## 十、R8 / Release 策略（D23）

- 保留 `core:logging` 门面（不 obfuscate 核心类名，避免栈不可读）。
- release 下文件 sink 保留 **W/E** 级落盘；V/D/I 不落文件。
- `LogcatSink` 仅 debug 装配（debug 构建判据），release 不装配。
- 崩溃日志 release 照常落盘（W/E + CrashVault 独立于门面级别）。

---

## 十一、日志格式（D25）

JSON 行（每行一条）：

```json
{"ts":"2026-09-01T10:00:00.000Z","lvl":"W","cat":"SECURITY.PERMISSION","tag":"Sandbox","msg":"命令被白名单拒绝","thr":"main","ex":"java.lang.SecurityException: …"}
```

字段：`ts` 时间 / `lvl` 级别 / `cat` 分类.子类 / `tag` 模块前缀 / `msg` 消息（已脱敏）/ `thr` 线程 / `ex` 异常（可选）。崩溃时另 dump 人类可读 `.txt` 快照。

---

## 附：调试辅助

`scripts/pull_logs.sh`：`adb exec-out run-as <pkg> tar …` 一键拉取私有目录日志，不依赖存储权限，作为调试兜底。
