// 自定义 Lint 规则：把"UI 必须统一"这条约定变成编译期硬约束。
// 没有这一层，设计系统三个月内必然被绕过。
plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    compileOnly("com.android.tools.lint:lint-api:31.7.3")
    compileOnly("com.android.tools.lint:lint-checks:31.7.3")
}

// 不显式指定 JVM 版本，跟随项目默认 JDK，避免与 Kotlin 的 jvmTarget 不一致
java {
    sourceCompatibility = JavaVersion.current()
    targetCompatibility = JavaVersion.current()
}
