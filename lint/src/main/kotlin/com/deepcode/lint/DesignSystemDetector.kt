package com.deepcode.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression

/**
 * 设计系统守卫。
 *
 * 光说"大家统一用组件库"没用，三个月后必然出现某个页面自己写了套 Scaffold。
 * 所以约定必须变成**构建失败**。
 *
 * §11 拦截矩阵：
 *   · 现有三条：Material3 import / 被禁 Composable / 裸 dp·sp
 *   · 八条新规则（§11 表）：
 *       DirectColorLiteral         业务层 Color(0x…) / Color(red=…)
 *       RawTextStyleConstruction   业务层 TextStyle(...) 直接构造
 *       ForbiddenWindowComponent   业务层裸用 Dialog / Popup / ModalBottomSheet
 *       ForbiddenPlatformToast     业务层裸用平台 Toast / Snackbar
 *       ForbiddenRawDropdown       业务层裸用 DropdownMenu / ExposedDropdownMenuBox
 *       ForbiddenRawTextField      业务层裸用 TextField / OutlinedTextField
 *       ForbiddenRawToolCard       业务层裸用 Card/Column 手拼工具调用卡 / 审批卡
 *       ForbiddenRawJsonRender     业务层将工具原始 JSON/参数直接进 UI
 */
class DesignSystemDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(
        UImportStatement::class.java,
        USimpleNameReferenceExpression::class.java,
        UQualifiedReferenceExpression::class.java,
        UCallExpression::class.java,
    )

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        // designsystem / lint 自己当然可以裸用，只拦业务层
        if (!isBusinessLayer(context)) return null

        return object : UElementHandler() {

            override fun visitImportStatement(node: UImportStatement) {
                val reference = node.importReference?.asSourceString() ?: return
                // 平台 Toast（仅 Android 原生引入，无主题跟随）
                if (reference == "android.widget.Toast" || reference.startsWith("android.widget.Toast.")) {
                    context.report(
                        ISSUE_FORBIDDEN_PLATFORM_TOAST,
                        node,
                        context.getNameLocation(node),
                        "禁止裸用平台 Toast，请使用 AppToast / AppBanner（6.6.3）",
                    )
                }
                // org.json 原始 JSON 直接进业务层 UI（未走工具注册表摘要路由）
                if (reference == "org.json.JSONObject" || reference == "org.json.JSONArray") {
                    context.report(
                        ISSUE_FORBIDDEN_RAW_JSON_RENDER,
                        node,
                        context.getNameLocation(node),
                        "工具原始 JSON 须经注册表摘要路由，原始 JSON 仅入折叠区（6.8.2）",
                    )
                }
                if (reference.startsWith("androidx.compose.material3") &&
                    // 图标原语无 App* 封装，是约定内的唯一出口；结构性组件由下例调用名规则兜底
                    reference.substringAfterLast('.').let { it !in BENIGN_MATERIAL3 }
                ) {
                    context.report(
                        ISSUE_DIRECT_MATERIAL3,
                        node,
                        context.getNameLocation(node),
                        "业务层禁止直接引用 Material3，请改用 :designsystem 提供的组件",
                    )
                }
            }

            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                val name = node.identifier
                if (name in FORBIDDEN_COMPOSABLES) {
                    context.report(
                        ISSUE_DIRECT_MATERIAL3,
                        node,
                        context.getLocation(node),
                        "禁止自建 $name；请使用 com.deepcode.designsystem.components 下的 App$name",
                    )
                }
            }

            override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression) {
                val selector = node.selector as? UCallExpression ?: return
                if (selector.methodName !in DIMENSION_UNITS) return
                val receiver = node.receiver
                if (receiver is ULiteralExpression) {
                    context.report(
                        ISSUE_HARDCODED_TOKEN,
                        node,
                        context.getLocation(node),
                        "禁止硬编码尺寸 ${receiver.asSourceString()}.${selector.methodName}，请使用 Dimens / TypeScale 令牌",
                    )
                }
            }

            override fun visitCallExpression(node: UCallExpression) {
                val methodName = node.methodName ?: return

                // 带接收者的调用：只关心 Toast.makeText(...)
                val receiver = node.receiver
                if (receiver != null) {
                    if (methodName == "makeText" && receiver.asSourceString().substringAfterLast('.') == "Toast") {
                        report(context, node, "禁止裸用平台 Toast，请使用 AppToast / AppBanner（6.6.3）",
                            ISSUE_FORBIDDEN_PLATFORM_TOAST)
                    }
                    return
                }

                // Kotlin 普通 class 的构造函数调用 methodName == "<init>"，
                // 需要从源码里提取类名来匹配（inline class 例外会直接显示类名）。
                val matchName = if (methodName == "<init>") {
                    // 构造函数调用源码格式：ClassName(...)，取括号前的 identifier
                    node.asSourceString().substringBefore('(').trim().substringAfterLast('.')
                } else {
                    methodName
                }

                when (matchName) {
                    // DirectColorLiteral：业务层直接构造 Color 字面量（inline class 显示类名，普通 class 构造函数已在上面 resolve）
                    "Color" -> report(context, node,
                        "禁止硬编码颜色，请使用 appTokens().colors.<语义名>", ISSUE_DIRECT_COLOR_LITERAL)

                    // RawTextStyleConstruction
                    "TextStyle" -> report(context, node,
                        "禁止直接构造 TextStyle，请使用 AppTextStyle 角色", ISSUE_RAW_TEXT_STYLE)

                    // ForbiddenWindowComponent（AlertDialog 对应 AppDialog，ModalBottomSheet 对应 AppModalSheet）
                    "AlertDialog", "Popup", "ModalBottomSheet" -> report(context, node,
                        "禁止裸用 $matchName，请使用 AppDialog / AppModalSheet", ISSUE_FORBIDDEN_WINDOW_COMPONENT)

                    // ForbiddenPlatformToast：Compose Snackbar
                    "Snackbar" -> report(context, node,
                        "禁止裸用 Snackbar，请使用 AppToast / AppBanner（6.6.3）", ISSUE_FORBIDDEN_PLATFORM_TOAST)

                    // ForbiddenRawDropdown
                    "DropdownMenu", "ExposedDropdownMenuBox" -> report(context, node,
                        "禁止裸用 $matchName，请使用 AppDropdownMenu（6.6.2）", ISSUE_FORBIDDEN_RAW_DROPDOWN)

                    // ForbiddenRawTextField
                    "TextField", "OutlinedTextField" -> report(context, node,
                        "禁止裸用 $matchName，请使用 AppTextField（6.7.1）", ISSUE_FORBIDDEN_RAW_TEXT_FIELD)

                    // ForbiddenRawToolCard：Card 家族裸用手拼工具卡/审批卡
                    "Card", "ElevatedCard", "OutlinedCard", "FilledCard" -> report(context, node,
                        "禁止裸用 $matchName 手拼工具卡/审批卡，请使用 AppToolCard / AppBlockGroup / AppApprovalCard（6.8）",
                        ISSUE_FORBIDDEN_RAW_TOOL_CARD)

                    // ForbiddenRawJsonRender：原始 JSON/参数直接进 UI（仅 Android org.json 裸构造；kotlinx 序列化不算）
                    "JSONObject", "JSONArray" -> report(context, node,
                        "工具结果须经注册表摘要路由，原始 JSON 仅入折叠区（6.8.2）", ISSUE_FORBIDDEN_RAW_JSON_RENDER)
                }
            }
        }
    }

    private fun report(
        context: JavaContext,
        node: UElement,
        message: String,
        issue: Issue,
    ) {
        context.report(issue, node, context.getLocation(node), message)
    }

    private fun isBusinessLayer(context: Context): Boolean {
        val path = context.file.path.replace('\\', '/')
        if (path.contains("/designsystem/") || path.contains("/lint/")) return false
        return path.contains("/feature/") || path.contains("/app/")
    }

    companion object {

        private val FORBIDDEN_COMPOSABLES = setOf(
            "Scaffold", "TopAppBar", "CenterAlignedTopAppBar", "Button", "OutlinedButton",
            "TextButton", "Card", "ElevatedCard", "OutlinedCard", "FilledCard",
            "FloatingActionButton", "NavigationBar", "SnackbarHost", "OutlinedTextField",
            "DropdownMenu", "TextField", "AlertDialog", "ModalBottomSheet",
        )

        private val DIMENSION_UNITS = setOf("dp", "sp")

        // 无 App* 封装的 benign Material3 图标原语，import 级豁免（结构性组件另有调用名规则兜底）
        private val BENIGN_MATERIAL3 = setOf("Icon", "IconButton", "IconToggleButton")

        val ISSUE_DIRECT_MATERIAL3: Issue = Issue.create(
            id = "DirectMaterial3Usage",
            briefDescription = "业务层禁止直接使用 Material3 组件",
            explanation = """
                所有 Material3 用法必须收敛到 :designsystem 模块。
                业务页面直接使用 Material3 会导致各页面视觉逐渐分叉，
                请改用 com.deepcode.designsystem.components 下的 App* 组件。
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.ERROR,
            implementation = Implementation(
                DesignSystemDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )

        val ISSUE_HARDCODED_TOKEN: Issue = Issue.create(
            id = "HardcodedDesignToken",
            briefDescription = "禁止硬编码尺寸与字号",
            explanation = """
                硬编码 16.dp / 14.sp 会产生大量"差不多"的数值，界面间距逐渐失控。
                请改用 designsystem 的 Dimens / TypeScale 令牌。
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                DesignSystemDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )

        val ISSUE_DIRECT_COLOR_LITERAL: Issue = Issue.create(
            id = "DirectColorLiteral",
            briefDescription = "业务层禁止直接构造 Color 字面量",
            explanation = """
                业务层 Color(0x...) / Color(red=...) 会绕开语义色令牌，导致各页面色值漂移。
                请使用 appTokens().colors.<语义名>。
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(DesignSystemDetector::class.java, Scope.JAVA_FILE_SCOPE),
        )

        val ISSUE_RAW_TEXT_STYLE: Issue = Issue.create(
            id = "RawTextStyleConstruction",
            briefDescription = "业务层禁止直接构造 TextStyle",
            explanation = """
                直接 new TextStyle(...) 会绕过 AppTextStyle 的角色体系，字号/字重/行距失控。
                请使用 AppTextStyle 角色。
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.ERROR,
            implementation = Implementation(DesignSystemDetector::class.java, Scope.JAVA_FILE_SCOPE),
        )

        val ISSUE_FORBIDDEN_WINDOW_COMPONENT: Issue = Issue.create(
            id = "ForbiddenWindowComponent",
            briefDescription = "业务层禁止裸用窗口类组件",
            explanation = """
                Dialog / Popup / ModalBottomSheet 必须收敛到 AppDialog / AppModalSheet 才能获得
                统一的浮层变体与交互态。业务层裸用会分叉。
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(DesignSystemDetector::class.java, Scope.JAVA_FILE_SCOPE),
        )

        val ISSUE_FORBIDDEN_PLATFORM_TOAST: Issue = Issue.create(
            id = "ForbiddenPlatformToast",
            briefDescription = "业务层禁止裸用平台 Toast / Snackbar",
            explanation = """
                平台 Toast 无主题跟随，Snackbar 也是 Material3 裸件。请使用 AppToast / AppBanner（6.6.3）
                获得轻提示双件套的统一行为。
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(DesignSystemDetector::class.java, Scope.JAVA_FILE_SCOPE),
        )

        val ISSUE_FORBIDDEN_RAW_DROPDOWN: Issue = Issue.create(
            id = "ForbiddenRawDropdown",
            briefDescription = "业务层禁止裸用下拉组件",
            explanation = """
                DropdownMenu / ExposedDropdownMenuBox 必须走 AppDropdownMenu（6.6.2）以获得选中态、分组与浮层层级。
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.ERROR,
            implementation = Implementation(DesignSystemDetector::class.java, Scope.JAVA_FILE_SCOPE),
        )

        val ISSUE_FORBIDDEN_RAW_TEXT_FIELD: Issue = Issue.create(
            id = "ForbiddenRawTextField",
            briefDescription = "业务层禁止裸用输入框",
            explanation = """
                TextField / OutlinedTextField 必须走 AppTextField（6.7.1）以保证浮动标签、
                error 图标、形态分工与统一外围样式。
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(DesignSystemDetector::class.java, Scope.JAVA_FILE_SCOPE),
        )

        val ISSUE_FORBIDDEN_RAW_TOOL_CARD: Issue = Issue.create(
            id = "ForbiddenRawToolCard",
            briefDescription = "业务层禁止裸用手拼工具卡/审批卡",
            explanation = """
                用 Card/Column 手拼工具调用卡/审批卡会各写各的。请使用 AppToolCard / AppBlockGroup /
                AppApprovalCard（6.8）以保证执行状态、展开折叠与活光标的行为一致。
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(DesignSystemDetector::class.java, Scope.JAVA_FILE_SCOPE),
        )

        val ISSUE_FORBIDDEN_RAW_JSON_RENDER: Issue = Issue.create(
            id = "ForbiddenRawJsonRender",
            briefDescription = "业务层禁止把原始 JSON/参数直接进 UI",
            explanation = """
                工具原始 JSON/参数必须经注册表摘要路由后再渲染，原始 JSON 仅入折叠区（6.8.2）。
                直接构造/解析 JSON 进入 UI 会导致内容杂乱且无法精简摘要。
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.ERROR,
            implementation = Implementation(DesignSystemDetector::class.java, Scope.JAVA_FILE_SCOPE),
        )
    }
}