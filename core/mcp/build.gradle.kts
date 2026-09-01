// MCP 客户端模块：把外部 MCP Server 的工具桥接进统一的 ToolRegistry。
// 依赖 core:agent 的 SPI 与 core:model 的领域类型；用项目已有的 okhttp + kotlinx-serialization
// 实现协议级兼容的最小 MCP 客户端（官方 Kotlin SDK 需 Kotlin 2.2+，与本项目 2.0.21 不兼容，
// 见 docs/TOOLS_SKILLS.md §3）。McpClient 接口与桥接/管理器逻辑与具体传输实现解耦，
// 未来升级 Kotlin 后换成官方 SDK 只需替换一个实现类。
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":core:agent"))
    api(project(":core:model"))
    implementation(project(":core:logging"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
