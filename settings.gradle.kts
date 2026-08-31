pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "DeepCore-Code"

// ---- L0/L1 底座：纯 Kotlin，零 Android 依赖，可脱离模拟器编译与单测 ----
include(":core:model")
include(":core:agent")
include(":core:data")
include(":core:uistate")

// ---- L0/L1 扩展：MCP 客户端（工具互操作，对齐 MCP 规范；Android 约束下仅客户端先行） ----
include(":core:mcp")

// ---- L2 能力实现（Android 相关） ----
include(":core:platform")

// ---- UI 统一层：唯一允许直接使用 Material3 的模块（app 除外） ----
include(":designsystem")

// ---- 守卫：自定义 Lint，禁止 feature 层绕过 designsystem ----
include(":lint")

// ---- 业务页面 ----
include(":feature:chat")

include(":app")
