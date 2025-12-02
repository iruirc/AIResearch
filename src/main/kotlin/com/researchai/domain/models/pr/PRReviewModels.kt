package com.researchai.domain.models.pr

import com.researchai.domain.models.ProviderType
import kotlinx.serialization.Serializable

/**
 * Review mode defining the depth of analysis
 */
@Serializable
enum class ReviewMode {
    /** Quick review - summary only, no detailed line comments */
    QUICK,

    /** Standard review - balanced with key issues highlighted */
    STANDARD,

    /** Thorough review - detailed line-by-line analysis */
    THOROUGH
}

/**
 * Focus areas for PR review (all have equal priority)
 */
@Serializable
enum class FocusArea {
    SECURITY,
    PERFORMANCE,
    CODE_STYLE,
    ARCHITECTURE,
    TESTING,
    DOCUMENTATION,
    ERROR_HANDLING,
    KOTLIN_IDIOMS
}

/**
 * Severity level of an issue
 */
@Serializable
enum class Severity {
    CRITICAL,
    WARNING,
    INFO
}

/**
 * Type of change in a file
 */
@Serializable
enum class ChangeType {
    ADDED,
    MODIFIED,
    DELETED,
    RENAMED
}

/**
 * Side of diff (left = old, right = new)
 */
@Serializable
enum class DiffSide {
    LEFT,
    RIGHT
}

/**
 * Request to review a pull request
 */
@Serializable
data class PRReviewRequest(
    val repositoryOwner: String,
    val repositoryName: String,
    val pullRequestNumber: Int,
    val reviewMode: ReviewMode = ReviewMode.STANDARD,
    val focusAreas: List<FocusArea> = FocusArea.entries,
    val providerId: ProviderType = ProviderType.CLAUDE,
    val model: String? = null,
    val maxFilesToReview: Int = 50,
    val includeLineComments: Boolean = true,

    /** Whether to use RAG for codebase context (requires repository to be indexed with `/init`) */
    val useRAG: Boolean = false,

    /** Minimum similarity score for RAG results (0.0-1.0). Higher = more relevant context */
    val ragMinScore: Float = 0.7f,

    /** Maximum number of RAG chunks to include in context. Each chunk is ~1000 characters */
    val ragMaxChunks: Int = 10
)

/**
 * Complete PR review result
 */
@Serializable
data class PRReviewResult(
    val requestId: String,
    val pullRequestUrl: String,
    val summary: ReviewSummary,
    val fileReviews: List<FileReview>,
    val overallScore: Int,
    val metadata: ReviewMetadata
)

/**
 * High-level summary of the review
 */
@Serializable
data class ReviewSummary(
    val overview: String,
    val criticalIssues: List<Issue>,
    val importantIssues: List<Issue>,
    val suggestions: List<Issue>,
    val positives: List<String>
)

/**
 * Review for a specific file
 */
@Serializable
data class FileReview(
    val filePath: String,
    val changeType: ChangeType,
    val lineComments: List<LineComment>,
    val fileSummary: String?
)

/**
 * Comment on a specific line
 */
@Serializable
data class LineComment(
    val lineNumber: Int,
    val side: DiffSide,
    val severity: Severity,
    val category: FocusArea,
    val message: String,
    val suggestedFix: String?
)

/**
 * An issue found during review
 */
@Serializable
data class Issue(
    val severity: Severity,
    val category: FocusArea,
    val title: String,
    val description: String,
    val affectedFiles: List<String>,
    val suggestedAction: String?
)

/**
 * Metadata about the review execution
 */
@Serializable
data class ReviewMetadata(
    val reviewDurationMs: Long,
    val filesReviewed: Int,
    val linesAnalyzed: Int,
    val ragContextUsed: Boolean = false,
    val ragChunksRetrieved: Int = 0,
    val tokensUsed: Int,
    val model: String,
    val provider: String
)

// Internal models for MCP data parsing

/**
 * Complete PR data fetched from GitHub
 */
internal data class PRData(
    val details: PRDetails,
    val diff: String,
    val changedFiles: List<ChangedFile>
)

/**
 * PR metadata from GitHub
 */
internal data class PRDetails(
    val number: Int,
    val title: String,
    val body: String?,
    val author: String,
    val url: String
)

/**
 * Information about a changed file
 */
internal data class ChangedFile(
    val filename: String,
    val status: ChangeType,
    val additions: Int,
    val deletions: Int,
    val patch: String?
)
