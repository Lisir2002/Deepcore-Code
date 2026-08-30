// 业务页面：只消费 AgentEvent 流，只使用 designsystem 提供的组件。
// 本模块不 import androidx.compose.material3.*（被 :lint 规则拦截）。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.agentide.feature.chat"
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

    lint {
        // 绕过设计系统 = 构建失败，而不是 code review 时才被发现
        error.add("DirectMaterial3Usage")
        error.add("HardcodedDesignToken")
        checkDependencies = true
    }
}

dependencies {
    implementation(project(":designsystem"))
    implementation(project(":core:model"))
    implementation(project(":core:agent"))
    implementation(project(":core:uistate"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.koin.compose)
    implementation(libs.kotlinx.coroutines.core)

    debugImplementation(libs.compose.ui.tooling)

    lintChecks(project(":lint"))
}
