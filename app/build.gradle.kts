plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.deepcode.agent"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.deepcode.agent"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 4
        versionName = "0.1.3"
    }

    // ---- 正式签名配置 ----------------------------------------------------
    // 密钥实体放在仓库外，这里只读取路径和口令。
    // 取值优先级：环境变量（CI） > keystore.properties（本地）。
    //
    // 为什么分两套：CI 上不可能有本地文件，只能靠 Secrets 注入环境变量；
    // 本地开发反过来，不想每次开终端都 export 四个变量。
    // 两条路互不干扰，缺任一套都不影响另一套。
    signingConfigs {
        create("release") {
            val props = rootProject.file("keystore.properties").let { f ->
                if (f.exists()) {
                    f.readLines()
                        .filter { it.isNotBlank() && !it.trimStart().startsWith("#") && it.contains("=") }
                        .associate { line ->
                            val (k, v) = line.split("=", limit = 2)
                            k.trim() to v.trim()
                        }
                } else {
                    emptyMap()
                }
            }

            fun pick(env: String, prop: String): String? =
                System.getenv(env)?.takeIf { it.isNotBlank() } ?: props[prop]?.takeIf { it.isNotBlank() }

            val storePath = pick("SIGNING_STORE_FILE", "STORE_FILE")

            // storeFile 是 File? 类型，file(null) 会直接抛异常，
            // 所以必须先判空。缺配置时整个 config 留空，交给 buildTypes 决定
            // 是回退 debug 还是让构建失败。
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = pick("SIGNING_STORE_PASSWORD", "STORE_PASSWORD")
                keyAlias = pick("SIGNING_KEY_ALIAS", "KEY_ALIAS")
                keyPassword = pick("SIGNING_KEY_PASSWORD", "KEY_PASSWORD")

                // 显式声明 PKCS12。JDK 9+ 默认已是 PKCS12，但显式写出来更稳：
                // 实测 jarsigner 在类型推断不符时会报 "not a private key"，
                // 提前钉死类型可以省掉一整类排查。
                storeType = "pkcs12"

                // ---- 签名方案 ----
                // v1（JAR 签名）：兼容老安装器/分发渠道，但可被整包重签名绕过，
                //   必须与 v2/v3 同时启用，绝不能只开 v1。
                // v2（Android 7.0+）：校验整包二进制，防篡改主力。
                // v3（Android 9.0+）：在 v2 之上支持密钥轮换，上传密钥丢了还能换。
                // v4：留给 AGP 按构建类型自动决定，不在这里手动开关。
                //
                // AGP 8.7 的开关属性是 enableV1/V2/V3Signing（Boolean?，null 走默认）。
                // 实测结论（已用 APK Signing Block 二进制解析逐版验证）：
                //   - 显式三开（v0.1.1）→ v1+v2+v3 全部命中；
                //   - 只显式开 v1、其余交默认（v0.1.2）→ v3 消失。
                //     即 AGP 8.7.3 + minSdk 26 的「默认」并不包含 v3，
                //     v3 必须显式开启；v1 亦然。
                // 故此处三个方案全部显式开启，不依赖默认值。
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    // release 签名四个值是否齐全。
    // 只检查"非空"，不校验口令是否正确——口令对不对只有真正签名时才暴露，
    // 提前去读密钥库既慢又容易打出误导性错误。
    // 用 findByName 走公开 API，不碰 internal 类，AGP 升级不会碎。
    val releaseSigning = signingConfigs.findByName("release")
    val signingReady = releaseSigning != null
        && releaseSigning.storeFile != null
        && !releaseSigning.storePassword.isNullOrBlank()
        && !releaseSigning.keyAlias.isNullOrBlank()
        && !releaseSigning.keyPassword.isNullOrBlank()

    buildFeatures {
        compose = true
        // AGP 8 起 BuildConfig 默认不生成，而完整性校验要用 BuildConfig.DEBUG
        // 区分 debug/release 处置策略，所以必须显式打开。
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            // ---- 代码加固 ----
            // R8：代码收缩 + 混淆 + 优化。规则见 app/proguard-rules.pro
            isMinifyEnabled = true
            // 资源收缩：移除未引用的 drawable/layout/string。
            // 注意它依赖 R8 的引用分析，必须在 isMinifyEnabled 打开时才有意义。
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            // ---- 签名 ----
            // 本地没配 keystore 时回退 debug，方便随手跑 assembleRelease；
            // 真正打 Release 包的 CI（release.yml / release-build job）若缺签名
            // 则直接失败——一个不可分发的 debug 签名包被打成 vX.Y.Z 的 GitHub
            // Release，比构建红掉危险得多。
            //
            // 判断条件不能只凭 CI=true：GitHub Actions 给「每一个」job 都注入
            // CI=true，包括只跑单测的 core-test。若只看 CI，core-test 在配置
            // app 模块时就会因为缺签名直接抛错、连单测都跑不起来。
            // 所以这里额外看「本次实际请求的 task 名里有没有 Release」——
            // 只有真的在打 Release 包时才强制要求签名。
            val onCi = System.getenv("CI")?.toBoolean() == true
            val buildingRelease = gradle.startParameter.taskNames
                .any { it.contains("Release", ignoreCase = true) }
            if (signingReady) {
                signingConfig = signingConfigs.getByName("release")
            } else if (onCi && buildingRelease) {
                throw GradleException(
                    """
                    |CI 构建 Release 包但签名配置不完整，已中止。
                    |
                    |请检查仓库 Secrets 是否齐备（Settings → Secrets and variables → Actions）：
                    |  SIGNING_KEYSTORE_BASE64   keystore 文件的 base64
                    |  SIGNING_STORE_PASSWORD    keystore 口令
                    |  SIGNING_KEY_ALIAS         密钥别名
                    |  SIGNING_KEY_PASSWORD      密钥口令
                    |
                    |PKCS12 注意：STORE_PASSWORD 与 KEY_PASSWORD 必须相同，
                    |否则签名阶段会报 "not a private key"。
                    """.trimMargin(),
                )
            } else {
                logger.warn(
                    "[签名] 未检测到正式签名配置，Release 包将使用 DEBUG 签名，" +
                        "仅供本地验证，不可对外分发。按 RELEASING.md 配置 keystore 后自动切换。",
                )
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":designsystem"))
    implementation(project(":feature:chat"))
    implementation(project(":core:model"))
    implementation(project(":core:agent"))
    implementation(project(":core:data"))
    implementation(project(":core:platform"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.ktx)

    implementation(libs.koin.android)
    implementation(libs.koin.compose)

    // 数据层：Android 端驱动。唯一知道 Android 的模块就是 :app，
    // :core:data 保持零 Android 依赖（见 DATA_LAYER.md）。
    implementation(libs.sqldelight.android.driver)

    debugImplementation(libs.compose.ui.tooling)

    // 设计系统守卫：绕过组件库 = 构建失败
    lintChecks(project(":lint"))
}
