package com.deepcode.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
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
 * 拦截两条：
 *   1. feature 层直接引用 androidx.compose.material3
 *   2. feature 层硬编码 16.dp / 14.sp 这类字面量
 */
class DesignSystemDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(
        UImportStatement::class.java,
        USimpleNameReferenceExpression::class.java,
        UQualifiedReferenceExpression::class.java,
    )

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        // designsystem 自己当然可以用 Material3，只拦业务层
        if (!isBusinessLayer(context)) return null

        return object : UElementHandler() {

            override fun visitImportStatement(node: UImportStatement) {
                val reference = node.importReference?.asSourceString() ?: return
                if (reference.startsWith("androidx.compose.material3")) {
                    context.report(
                        issue = ISSUE_DIRECT_MATERIAL3,
                        scope = node,
                        location = context.getNameLocation(node),
                        message = "业务层禁止直接引用 Material3，请改用 :designsystem 提供的组件",
                    )
                }
            }

            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                val name = node.identifier
                if (name in FORBIDDEN_COMPOSABLES) {
                    context.report(
                        issue = ISSUE_DIRECT_MATERIAL3,
                        scope = node,
                        location = context.getLocation(node),
                        message = "禁止自建 $name；请使用 com.deepcode.designsystem.components 下的 App$name",
                    )
                }
            }

            override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression) {
                val selector = node.selector as? UCallExpression ?: return
                if (selector.methodName !in DIMENSION_UNITS) return
                val receiver = node.receiver
                if (receiver is ULiteralExpression) {
                    context.report(
                        issue = ISSUE_HARDCODED_TOKEN,
                        scope = node,
                        location = context.getLocation(node),
                        message = "禁止硬编码尺寸 ${receiver.asSourceString()}.${selector.methodName}，请使用 Dimens / TypeScale 令牌",
                    )
                }
            }
        }
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
        )

        private val DIMENSION_UNITS = setOf("dp", "sp")

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
    }
}
