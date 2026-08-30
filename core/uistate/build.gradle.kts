// 视图状态层：事件 → 渲染块 的归约逻辑。
//
// 它**故意放在纯 Kotlin 模块**里：这段逻辑是 UI 层最容易出 bug 的地方
// （流式拼接、块复用、状态迁移），必须能脱离模拟器直接单测。
// 真正的绘制（Compose）留在 :designsystem。
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":core:model"))

    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}
