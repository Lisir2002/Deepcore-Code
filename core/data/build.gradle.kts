// 存储层：定义 Repository 接口 + 与平台无关的持久化实现。
// 纯 Kotlin，零 Android 依赖；后续要换 Room 只需新增一个实现，接口不动。
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":core:model"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
