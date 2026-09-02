package com.deepcode.agent.di

import com.deepcode.core.agent.AgentConfig
import com.deepcode.core.agent.AgentRuntimeFactory
import com.deepcode.core.agent.DefaultAgentRuntime
import com.deepcode.core.agent.spi.ContextPolicy
import com.deepcode.core.agent.spi.DefaultToolRegistry
import com.deepcode.core.agent.spi.DefaultContextPolicy
import com.deepcode.core.agent.spi.Sandbox
import com.deepcode.core.agent.spi.ToolRegistry
import com.deepcode.core.agent.spi.Workspace
import com.deepcode.core.agent.skill.DefaultSkillInjector
import com.deepcode.core.agent.skill.SkillInjector
import com.deepcode.core.agent.skill.SkillLoader
import com.deepcode.core.agent.skill.WorkspaceSkillLoader
import com.deepcode.core.mcp.HttpJsonRpcMcpClient
import com.deepcode.core.mcp.McpServerConfigStore
import com.deepcode.core.mcp.McpServerManager
import com.deepcode.core.mcp.McpTransport
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.deepcode.core.data.EventStore
import com.deepcode.core.data.db.DeepCoreDatabase
import com.deepcode.core.data.db.SqliteDatabase
import com.deepcode.core.data.db.TableModule
import com.deepcode.core.data.db.createSqliteDatabase
import com.deepcode.core.data.event.SQLiteEventStore
import com.deepcode.core.model.ModelRef
import com.deepcode.core.model.WorkspaceRef
import com.deepcode.core.platform.sandbox.CommandWhitelistSandbox
import com.deepcode.core.platform.tools.ListFilesTool
import com.deepcode.core.platform.tools.ReadFileTool
import com.deepcode.core.platform.tools.RunCommandTool
import com.deepcode.core.platform.tools.WriteFileTool
import com.deepcode.core.platform.workspace.LocalDirWorkspace
import com.deepcode.agent.demo.DemoProvider
import com.deepcode.agent.model.ModelEndpointConfigStore
import com.deepcode.agent.model.OkHttpProvider
import com.deepcode.agent.logging.AndroidLoggingActions
import com.deepcode.agent.logging.LogExporter
import com.deepcode.agent.logging.RollingFileSink
import com.deepcode.agent.logging.recentEventLines
import com.deepcode.agent.mcp.AndroidMcpServerConfigStore
import com.deepcode.feature.settings.LoggingActions
import com.deepcode.designsystem.theme.DefaultStyleController
import com.deepcode.designsystem.theme.StyleController
import com.deepcode.designsystem.theme.ThemePacks
import com.deepcode.designsystem.theme.DarkMode
import com.deepcode.feature.chat.ChatViewModel
import com.deepcode.feature.chat.ConversationViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.io.File

/**
 * 依赖装配。
 *
 * 注意这里**所有类型都是接口**：EventStore、ModelProvider、Sandbox、Workspace、
 * ToolRegistry。想换实现（Room 存储、真实模型、Proot 容器、SSH 远端），
 * 只改这个文件的绑定，业务代码一行不动。
 */
/**
 * ★ 数据层扩展注册表。
 *
 * 新功能要持久化时，只做一件事：把它的 `TableModule` 加进这个列表。
 * 建表、迁移、事务边界全部由 `:core:data` 的 SchemaManager 接管，
 * 核心框架一个字都不用改。协议与示例见 DATA_LAYER.md 第五节。
 */
val dataTableModules: List<TableModule> = listOf(
    SettingsTableModule,
)

val appModule = module {

    single<CoroutineScope>(qualifier = org.koin.core.qualifier.named("agent")) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    // 数据层：Android 驱动只在 :app 装配，:core:data 保持零 Android 依赖。
    // 所有 SQLite 操作都被 SqliteDatabase 切到单线程 IO，主线程永不碰数据库。
    single<SqliteDatabase> {
        val driver = AndroidSqliteDriver(
            schema = DeepCoreDatabase.Schema,
            context = androidContext(),
            name = "deepcore.db",
        )
        createSqliteDatabase(driver = driver, modules = dataTableModules)
    }

    single<EventStore> { SQLiteEventStore(db = get()) }

    // ── 日志（决策 D1/D8–D11/D13–D15/D21）────────────────────────────
    // 文件 sink：私有 + 根目录双写、1MB×5 滚动、danger.log 镜像。
    // LogExporter 取数函数延迟求值：MCP 配置读当前快照，事件流读数据层最近 200 条。
    single<RollingFileSink> { RollingFileSink(androidContext()) }

    single<LogExporter> {
        LogExporter(
            context = androidContext(),
            rollingSink = get(),
            mcpConfigs = { get<McpServerConfigStore>().current() },
            eventLines = { recentEventLines(get()) },
        )
    }

    // 设置页日志区的契约实现（feature:settings 只认接口，实现与权限都在 :app）。
    single<LoggingActions> {
        AndroidLoggingActions(context = androidContext(), exporter = get())
    }

    // ── 主题 / 外观（§7.1 StyleController 装配）──────────────────────
    // 内置包注册：brand 常驻 + console 演示包；持久化走 SettingsTableModule。
    // Koin single 块非 suspend，构造期初值用 runBlocking 一次性同步读取。
    single<StyleController> {
        val store = SqliteStylePreferenceStore(get())
        val (savedSpecId, savedDarkMode) = runBlocking {
            val spec = store.read(DefaultStyleController.KEY_SPEC)
            val mode = runCatching {
                DarkMode.valueOf(store.read(DefaultStyleController.KEY_DARK_MODE) ?: "")
            }.getOrDefault(DarkMode.FOLLOW_SYSTEM)
            spec to mode
        }

        val packs = ThemePacks.builtIn
        val initialSpec = packs.firstOrNull { it.id == savedSpecId } ?: packs.first()

        DefaultStyleController(
            initialSpec = initialSpec,
            darkMode = savedDarkMode,
            store = store,
        ).apply { packs.forEach(::registerPack) }
    }

    single<Sandbox> { CommandWhitelistSandbox() }

    single<Workspace> {
        val dir = File(androidContext().filesDir, "projects/default").apply { mkdirs() }
        LocalDirWorkspace(WorkspaceRef.LocalDir(dir.absolutePath))
    }

    single<ToolRegistry> {
        val builtin = DefaultToolRegistry().apply {
            register(ListFilesTool())
            register(ReadFileTool())
            register(WriteFileTool())
            register(RunCommandTool())
        }
        McpCompositeToolRegistry(builtin, get())
    }

    // ── MCP server 配置与连接 ──────────────────────────────────────
    // 配置落 filesDir/mcp/servers.json；构造期同步读，供 manager 非阻塞装配。
    single<McpServerConfigStore> { AndroidMcpServerConfigStore(androidContext()) }

    single<McpServerManager> {
        val store = get<McpServerConfigStore>()
        McpServerManager(
            configs = store.current(),
            clientFactory = { cfg ->
                when (val t = cfg.transport) {
                    is McpTransport.Http -> HttpJsonRpcMcpClient(cfg.id, t.url, t.headers)
                }
            },
            scope = get(named("agent")),
        )
    }

    // ── Agent Skills（L1 注入）──────────────────────────────────────
    // skill 根目录相对工作区根；用户级 skill 由 :app 的 Workspace 指向私有目录。
    single<SkillLoader> {
        WorkspaceSkillLoader(get(), roots = listOf("skills", ".deepcode/skills"))
    }
    single<SkillInjector> { DefaultSkillInjector() }

    single<ContextPolicy> { DefaultContextPolicy() }

    // 真实模型端点配置：配置齐全则 AgentRuntime 用 OkHttpProvider 接真实模型，
    // 否则回退 DemoProvider（脚手架，保证没配 key 也能跑通 UI 全链路）。
    single { ModelEndpointConfigStore(get()) }

    // 会话工厂：每个会话一个 AgentRuntime，UI 只传 sessionId。
    // 依赖在模块装配时解析一次，工厂只负责换 sessionId。
    // 演示模型（DemoProvider）在工厂内按会话新建独立实例：内部 round 等状态随会话隔离，
    // 不会跨会话串扰（否则所有对话行为互相影响、输出雷同）。接入真实模型时换成对应 Provider 工厂即可。
    single<AgentRuntimeFactory> {
        val skillLoader = get<SkillLoader>()
        val skillInjector = get<SkillInjector>()
        val toolRegistry = get<ToolRegistry>()
        val workspace = get<Workspace>()
        val sandbox = get<Sandbox>()
        val eventStore = get<EventStore>()
        val contextPolicy = get<ContextPolicy>()
        val scope = get<CoroutineScope>(qualifier = org.koin.core.qualifier.named("agent"))
        val modelConfig = get<ModelEndpointConfigStore>().config()
        AgentRuntimeFactory { sessionId ->
            // 配置了真实模型就用 OkHttpProvider，否则退回 DemoProvider（脚手架）。
            if (modelConfig != null) {
                DefaultAgentRuntime(
                    sessionId = sessionId,
                    provider = OkHttpProvider(modelConfig),
                    modelRef = ModelRef(OkHttpProvider.OPENAI_PROVIDER_ID, modelConfig.model),
                    toolRegistry = toolRegistry,
                    workspace = workspace,
                    sandbox = sandbox,
                    eventStore = eventStore,
                    contextPolicy = contextPolicy,
                    scope = scope,
                    config = AgentConfig(maxIterations = 12),
                    skillSectionProvider = {
                        val result = skillLoader.load()
                        skillInjector.buildSkillSection(result.skills).takeIf { it.isNotEmpty() }
                    },
                )
            } else {
                DefaultAgentRuntime(
                    sessionId = sessionId,
                    provider = DemoProvider(),
                    modelRef = ModelRef("demo", "demo-1"),
                    toolRegistry = toolRegistry,
                    workspace = workspace,
                    sandbox = sandbox,
                    eventStore = eventStore,
                    contextPolicy = contextPolicy,
                    scope = scope,
                    config = AgentConfig(maxIterations = 12),
                    skillSectionProvider = {
                        val result = skillLoader.load()
                        skillInjector.buildSkillSection(result.skills).takeIf { it.isNotEmpty() }
                    },
                )
            }
        }
    }

    viewModelOf(::ChatViewModel)
    viewModelOf(::ConversationViewModel)
}
