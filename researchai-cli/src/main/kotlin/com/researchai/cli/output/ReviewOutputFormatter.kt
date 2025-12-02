package com.researchai.cli.output

import com.researchai.cli.api.PRReviewResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Formatter for PR review output in different formats
 */
class ReviewOutputFormatter {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /**
     * Format as JSON
     */
    fun formatJson(result: PRReviewResult): String {
        return json.encodeToString(result)
    }

    /**
     * Format as human-readable text
     */
    fun formatText(result: PRReviewResult): String = buildString {
        appendLine("=".repeat(80))
        appendLine("📊 PR REVIEW RESULTS")
        appendLine("=".repeat(80))
        appendLine()

        // Overall score
        val scoreEmoji = when {
            result.overallScore >= 90 -> "🟢"
            result.overallScore >= 70 -> "🟡"
            result.overallScore >= 50 -> "🟠"
            else -> "🔴"
        }
        appendLine("$scoreEmoji Overall Score: ${result.overallScore}/100")
        appendLine()

        // Overview
        appendLine("📝 Summary")
        appendLine("-".repeat(80))
        appendLine(result.summary.overview)
        appendLine()

        // Critical issues
        if (result.summary.criticalIssues.isNotEmpty()) {
            appendLine("🔴 Critical Issues (${result.summary.criticalIssues.size})")
            appendLine("-".repeat(80))
            result.summary.criticalIssues.forEach { issue ->
                appendLine("• [${issue.category}] ${issue.title}")
                appendLine("  ${issue.description}")
                if (!issue.suggestedAction.isNullOrBlank()) {
                    appendLine("  💡 ${issue.suggestedAction}")
                }
                appendLine()
            }
        }

        // Important issues
        if (result.summary.importantIssues.isNotEmpty()) {
            appendLine("🟡 Important Issues (${result.summary.importantIssues.size})")
            appendLine("-".repeat(80))
            result.summary.importantIssues.forEach { issue ->
                appendLine("• [${issue.category}] ${issue.title}")
                appendLine("  ${issue.description}")
                if (!issue.suggestedAction.isNullOrBlank()) {
                    appendLine("  💡 ${issue.suggestedAction}")
                }
                appendLine()
            }
        }

        // Suggestions
        if (result.summary.suggestions.isNotEmpty()) {
            appendLine("💭 Suggestions (${result.summary.suggestions.size})")
            appendLine("-".repeat(80))
            result.summary.suggestions.forEach { issue ->
                appendLine("• [${issue.category}] ${issue.title}")
                appendLine("  ${issue.description}")
                appendLine()
            }
        }

        // Positives
        if (result.summary.positives.isNotEmpty()) {
            appendLine("✅ Positive Observations")
            appendLine("-".repeat(80))
            result.summary.positives.forEach { positive ->
                appendLine("• $positive")
            }
            appendLine()
        }

        // File reviews
        if (result.fileReviews.isNotEmpty()) {
            appendLine("📁 File-by-File Review (${result.fileReviews.size} files)")
            appendLine("-".repeat(80))
            result.fileReviews.forEach { file ->
                appendLine("📄 ${file.filePath} (${file.changeType})")
                if (file.fileSummary != null) {
                    appendLine("   ${file.fileSummary}")
                }
                if (file.lineComments.isNotEmpty()) {
                    file.lineComments.forEach { comment ->
                        val icon = when (comment.severity) {
                            "CRITICAL" -> "🔴"
                            "WARNING" -> "🟡"
                            else -> "ℹ️"
                        }
                        appendLine("   $icon Line ${comment.lineNumber}: ${comment.message}")
                    }
                }
                appendLine()
            }
        }

        // Metadata
        appendLine("⚙️ Review Metadata")
        appendLine("-".repeat(80))
        appendLine("Duration: ${result.metadata.reviewDurationMs / 1000.0}s")
        appendLine("Files reviewed: ${result.metadata.filesReviewed}")
        appendLine("Model: ${result.metadata.model} (${result.metadata.provider})")
        appendLine("Tokens used: ${result.metadata.tokensUsed}")
        appendLine()
    }

    /**
     * Format as GitHub-flavored Markdown
     */
    fun formatGitHubMarkdown(result: PRReviewResult): String = buildString {
        appendLine("## 🤖 AI Code Review")
        appendLine()

        val scoreEmoji = when {
            result.overallScore >= 90 -> "🟢"
            result.overallScore >= 70 -> "🟡"
            result.overallScore >= 50 -> "🟠"
            else -> "🔴"
        }
        appendLine("**$scoreEmoji Overall Score:** ${result.overallScore}/100")
        appendLine()

        appendLine("### 📝 Summary")
        appendLine(result.summary.overview)
        appendLine()

        if (result.summary.criticalIssues.isNotEmpty()) {
            appendLine("### 🔴 Critical Issues")
            result.summary.criticalIssues.forEach { issue ->
                appendLine("- **[${issue.category}]** ${issue.title}")
                appendLine("  - ${issue.description}")
                if (!issue.suggestedAction.isNullOrBlank()) {
                    appendLine("  - 💡 _${issue.suggestedAction}_")
                }
            }
            appendLine()
        }

        if (result.summary.importantIssues.isNotEmpty()) {
            appendLine("### 🟡 Important Issues")
            result.summary.importantIssues.forEach { issue ->
                appendLine("- **[${issue.category}]** ${issue.title}")
                appendLine("  - ${issue.description}")
            }
            appendLine()
        }

        if (result.summary.suggestions.isNotEmpty()) {
            appendLine("### 💭 Suggestions")
            result.summary.suggestions.forEach { issue ->
                appendLine("- **[${issue.category}]** ${issue.title}")
            }
            appendLine()
        }

        if (result.summary.positives.isNotEmpty()) {
            appendLine("### ✅ Positive Observations")
            result.summary.positives.forEach { positive ->
                appendLine("- $positive")
            }
            appendLine()
        }

        appendLine("<details>")
        appendLine("<summary>⚙️ Review Details</summary>")
        appendLine()
        appendLine("- **Duration:** ${result.metadata.reviewDurationMs / 1000.0}s")
        appendLine("- **Files reviewed:** ${result.metadata.filesReviewed}")
        appendLine("- **Model:** ${result.metadata.model}")
        appendLine("- **Provider:** ${result.metadata.provider}")
        appendLine("</details>")
    }
}
