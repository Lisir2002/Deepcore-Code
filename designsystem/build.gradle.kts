// ══════════════════════════════════════════════════════════════════
// UI 统一层：整个 App 中唯一允许直接使用 Material3 的模块（:app 除外）。
//
// 约定（由 :lint 模块自定义规则强制）：
//   • feature 层禁止直接 import androidx.compose.material3.*
//   • feature 层禁止硬编码颜色 / dp 字面量
//   • feature 层禁止自建 Scaffold / TopAppBar / Button
// 违反构建即报错，不靠口头约定。
// ══════════════════════════════════════════════════════════════════
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)   // ThemeJsonCodec（theme.json v1）编解码
}

android {
    namespace = "com.deepcode.designsystem"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    api(project(":core:model"))
    api(project(":core:uistate"))

    api(platform(libs.compose.bom))
    api(libs.compose.material3)
    // extended 包含 core 全部图标，并额外提供 Stop / Inbox / ErrorOutline 等。
    // 设计系统后续会持续新增图标，只依赖 core 会反复踩"图标找不到"。
    // Release 构建由 R8 移除未使用图标，不增加最终 APK 体积。
    api(libs.compose.material.icons.core)
    api(libs.compose.material.icons.extended)
    api(libs.compose.ui)
    api(libs.compose.ui.tooling.preview)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.compose.ui.tooling)

    // TranscriptReducer 是纯 Kotlin，可以在 JVM 上直接单测，不必跑模拟器
    testImplementation(kotlin("test"))
    testImplementation(kotlin("reflect"))   // 12.1 面板完整性用反射枚举全属性
    testImplementation(libs.kotlinx.coroutines.test)
}
