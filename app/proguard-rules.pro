# ===========================================================================
# DeepCore-Code Release 构建 R8 规则
#
# 配套：app/build.gradle.kts 的 release { isMinifyEnabled = true }
#
# 每条规则都注明了「为什么需要」和「去掉会怎样」。
# 改之前先读注释——很多规则是踩坑换来的，凭直觉删掉会在运行时炸，
# 而且往往只在 Release 包上复现，debug 完全正常，极难排查。
# ===========================================================================


# ---------------------------------------------------------------------------
# 一、通用属性保留
# ---------------------------------------------------------------------------

# 崩溃栈要能定位到源码行，否则 Release 包的堆栈全是行号偏移，没法看。
-keepattributes SourceFile,LineNumberTable

# 注解和泛型签名：kotlinx-serialization 依赖注解，
# Kotlin 反射/内联类依赖 Signature，去掉会在运行时抛 NoSuchMethodError。
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# 保留 Kotlin 元数据，Compose 编译器与 Koin 都靠它做类型判断。
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# 保留异常信息：R8 默认会把无参构造的异常消息优化掉，
# 导致 Release 包里 catch 到的异常 message 为 null。
-keepclassmembers,allowoptimization class * {
    @kotlinx.coroutines.internal.* *;
}
-keepclassmembers class * extends java.lang.Throwable {
    <init>(...);
    java.lang.String getMessage();
}


# ---------------------------------------------------------------------------
# 二、Android 组件（由系统反射创建，被裁掉直接 ClassNotFound）
# ---------------------------------------------------------------------------
# Activity/Service/Receiver/Provider 若在 Manifest 中声明，AGP 会自动保留；
# 下面这条兜住"代码里 new 出来但没在 Manifest 声明"的情况。
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}

# 本项目的 Application 与入口 Activity。
-keep class com.deepcode.agent.DeepCoreCodeApp { *; }
-keep class com.deepcode.agent.MainActivity { *; }


# ---------------------------------------------------------------------------
# 三、Compose
# ---------------------------------------------------------------------------
# Compose 编译器会生成 $composited 之类的合成方法，R8 可能误判为无用代码删掉。
# 官方规则随 Compose Runtime 的 consumer-rules 一起下发，这里只补兜底项。
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# Compose 的 runtime 内部实现整体保留。
# 它大量使用内联与合成方法，R8 的跨模块分析容易误伤，
# 而 runtime 本身不算大，全留的代价可接受。
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**


# ---------------------------------------------------------------------------
# 四、Koin（依赖注入）
# ---------------------------------------------------------------------------
# Koin 4.0 主要靠 lambda DSL 装配，本身不依赖类名反射，
# 但保留其内部实现可以避免它在优化后找不到自己的扩展点。
# 代价极小（Koin 本身很小），收益是杜绝一整类启动崩溃。
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# koin.viewModelOf(::XxxViewModel) 用构造引用 + KClass，
# 必须留住构造器和伴生对象，否则启动即崩。
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}


# ---------------------------------------------------------------------------
# 五、kotlinx-serialization（本项目 core:model 在用）
# ---------------------------------------------------------------------------
# Ids.kt / Tool.kt 里有 @Serializable 的值类与数据类。
# 序列化器由编译期插件生成（$$serializer + Companion.serializer()），
# 但 R8 的收缩分析看不出"生成代码会被调用"，会当成死代码删掉。
# 典型症状：SerializationException: Serializer for class X is not found。
#
# 参考 kotlinx-serialization 官方 R8 配置。
-keep,includedescriptorclasses class com.deepcode.core.model.**$$serializer { *; }
-keepclassmembers class com.deepcode.core.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.deepcode.core.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# JsonObject 等 JSON 内部类型的 Companion 必须留
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# 保留 @Serializable 注解本身（配合上面的 -keepattributes *Annotation*）
-keep @kotlinx.serialization.Serializable class * { *; }


# ---------------------------------------------------------------------------
# 六、协程 / WorkManager / Navigation
# ---------------------------------------------------------------------------
# kotlinx.coroutines：调度器与内部状态机的 volatile 字段被 R8 优化掉后，
# 会出现协程不执行或状态错乱，且只在 Release 复现。
-keepclassmembernames class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# WorkManager 靠反射实例化 Worker
-keep class * extends androidx.work.Worker { public <init>(...); }
-keep class * extends androidx.work.ListenableWorker { public <init>(...); }
-keep class * extends androidx.work.CoroutineWorker { public <init>(...); }
-dontwarn androidx.work.**

# Navigation 用字符串路由反射到 destination
-keepclassmembers class * {
    @androidx.navigation.NavDestination *;
}
-dontwarn androidx.navigation.**


# ---------------------------------------------------------------------------
# 七、本项目架构相关
# ---------------------------------------------------------------------------
# core:model 的 AgentEvent 是整条事件流的核心模型，
# 跨模块传递且未来可能做持久化/序列化，整体保留。
-keep class com.deepcode.core.model.** { *; }

# SPI 接口（ModelProvider / Tool / Workspace / Sandbox / Memory / Context 等）
# 当前是编译期装配，不依赖 ServiceLoader 运行时发现，
# 但保留接口名能让崩溃栈和日志仍然可读，代价可忽略。
-keep interface com.deepcode.core.agent.spi.** { *; }

# designsystem 是 UI 统一层，组件被多处引用。
# 保留它主要是为了让 R8 不要因为跨模块分析不到位误删。
-keep class com.deepcode.designsystem.** { *; }


# ---------------------------------------------------------------------------
# 八、日志与清理
# ---------------------------------------------------------------------------
# Release 包移除所有 android.util.Log 调用，避免日志里带出敏感信息。
#
# 注意两件事：
# 1) 这只移除 Log 调用本身，不会消除字符串拼接的开销——
#    想彻底省掉拼接，代码里要用 if (BuildConfig.DEBUG) 包一层。
# 2) 别顺手加 -dontwarn com.deepcode.**：那会把我们自己代码里的
#    缺类告警一起吞掉，正是排查 Release-only 崩溃最需要的信号。
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}


# ---------------------------------------------------------------------------
# 维护提示
# ---------------------------------------------------------------------------
# · 新增第三方库后若 Release 包运行时崩溃，先查该库官方文档的 ProGuard 段，
#   大多数库随 AAR 带了 consumer-rules.pro，AGP 会自动合并，不要重复手写。
# · 排查 Release-only 崩溃时，用 mapping.txt 还原栈：
#     app/build/outputs/mapping/release/mapping.txt
#   （该文件已在 .gitignore 中，从构建产物里取）
# · 想确认某条规则是否生效，看 seeds.txt 与 usage.txt：
#     app/build/outputs/mapping/release/{seeds,usage}.txt
# ===========================================================================
