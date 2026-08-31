// 存储层：定义 Repository 接口 + 与平台无关的持久化实现。
// 纯 Kotlin（零 Android 依赖）；Android 驱动只在 :app 装配，见 DATA_LAYER.md。
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

sqldelight {
    databases {
        // 生成 com.deepcode.core.data.db.DeepCoreDatabase（类型安全查询入口）
        create("DeepCoreDatabase") {
            packageName.set("com.deepcode.core.data.db")
            dialect(libs.versions.sqldelightDialect.get())
        }
    }
}

dependencies {
    api(project(":core:model"))

    // SQLDelight 是数据层的公共契约（DAO 与 SPI 都暴露它的类型），故用 api
    api(libs.sqldelight.runtime)
    api(libs.sqldelight.coroutines.extensions)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    // JVM 内存库驱动：仅测试用，不进 APK
    testImplementation(libs.sqldelight.sqlite.driver)
}
