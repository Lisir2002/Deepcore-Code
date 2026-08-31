package com.deepcode.agent.di

import com.deepcode.core.agent.AgentConfig
import com.deepcode.core.agent.AgentRuntime
import com.deepcode.core.agent.DefaultAgentRuntime
import com.deepcode.core.agent.spi.ContextPolicy
import com.deepcode.core.agent.spi.DefaultToolRegistry
import com.deepcode.core.agent.spi.DefaultContextPolicy
import com.deepcode.core.agent.spi.ModelProvider
import com.deepcode.core.agent.spi.Sandbox
import com.deepcode.core.agent.spi.ToolRegistry
import com.deepcode.core.agent.spi.Workspace
import com.deepcode.core.data.EventStore
import com.deepcode.core.data.InMemoryEventStore
import com.deepcode.core.model.ModelRef
import com.deepcode.core.model.SessionId
import com.deepcode.core.model.WorkspaceRef
import com.deepcode.core.platform.sandbox.CommandWhitelistSandbox
import com.deepcode.core.platform.tools.ListFilesTool
import com.deepcode.core.platform.tools.ReadFileTool
import com.deepcode.core.platform.tools.RunCommandTool
import com.deepcode.core.platform.tools.WriteFileTool
import com.deepcode.core.platform.workspace.LocalDirWorkspace
import com.deepcode.agent.demo.DemoProvider
import com.deepcode.feature.chat.ChatViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.dsl.module
import java.io.File

/**
 * 依赖装配。
 *
 * 注意这里**所有类型都是接口**：EventStore、ModelProvider、Sandbox、Workspace、
 * ToolRegistry。想换实现（Room 存储、真实模型、Proot 容器、SSH 远端），
 * 只改这个文件的绑定，业务代码一行不动。
 */
val appModule = module {

    single<CoroutineScope>(qualifier = org.koin.core.qualifier.named("agent")) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    single<EventStore> { InMemoryEventStore() }

    single<Sandbox> { CommandWhitelistSandbox() }

    single<Workspace> {
        val dir = File(androidContext().filesDir, "projects/default").apply { mkdirs() }
        LocalDirWorkspace(WorkspaceRef.LocalDir(dir.absolutePath))
    }

    single<ToolRegistry> {
        DefaultToolRegistry().apply {
            register(ListFilesTool())
            register(ReadFileTool())
            register(WriteFileTool())
            register(RunCommandTool())
        }
    }

    // M0 用演示模型跑通链路；接入真实模型时把这一行换成对应 Provider 即可
    single<ModelProvider> { DemoProvider() }

    single<ContextPolicy> { DefaultContextPolicy() }

    single<AgentRuntime> {
        DefaultAgentRuntime(
            sessionId = SessionId("default"),
            provider = get(),
            modelRef = ModelRef("demo", "demo-1"),
            toolRegistry = get(),
            workspace = get(),
            sandbox = get(),
            eventStore = get(),
            contextPolicy = get(),
            scope = get(qualifier = org.koin.core.qualifier.named("agent")),
            config = AgentConfig(maxIterations = 12),
        )
    }

    viewModelOf(::ChatViewModel)
}
