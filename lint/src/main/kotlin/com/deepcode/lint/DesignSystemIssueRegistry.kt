package com.deepcode.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

class DesignSystemIssueRegistry : IssueRegistry() {

    override val issues: List<Issue> = listOf(
        DesignSystemDetector.ISSUE_DIRECT_MATERIAL3,
        DesignSystemDetector.ISSUE_HARDCODED_TOKEN,
        DesignSystemDetector.ISSUE_DIRECT_COLOR_LITERAL,
        DesignSystemDetector.ISSUE_RAW_TEXT_STYLE,
        DesignSystemDetector.ISSUE_FORBIDDEN_WINDOW_COMPONENT,
        DesignSystemDetector.ISSUE_FORBIDDEN_PLATFORM_TOAST,
        DesignSystemDetector.ISSUE_FORBIDDEN_RAW_DROPDOWN,
        DesignSystemDetector.ISSUE_FORBIDDEN_RAW_TEXT_FIELD,
        DesignSystemDetector.ISSUE_FORBIDDEN_RAW_TOOL_CARD,
        DesignSystemDetector.ISSUE_FORBIDDEN_RAW_JSON_RENDER,
    )

    override val api: Int = CURRENT_API

    override val minApi: Int = 14

    override val vendor: Vendor = Vendor(
        vendorName = "DeepCore-Code",
        identifier = "com.deepcode.lint",
        feedbackUrl = "https://example.invalid/deepcode/lint",
    )
}
