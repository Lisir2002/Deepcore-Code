package com.deepcode.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

class DesignSystemIssueRegistry : IssueRegistry() {

    override val issues: List<Issue> = listOf(
        DesignSystemDetector.ISSUE_DIRECT_MATERIAL3,
        DesignSystemDetector.ISSUE_HARDCODED_TOKEN,
    )

    override val api: Int = CURRENT_API

    override val minApi: Int = 14

    override val vendor: Vendor = Vendor(
        vendorName = "DeepCore-Code",
        identifier = "com.deepcode.lint",
        feedbackUrl = "https://example.invalid/deepcode/lint",
    )
}
