package com.researchai.cli.handlers

import com.researchai.cli.api.ResearchAiClient
import com.researchai.cli.output.ReviewOutputFormatter
import com.researchai.cli.api.PRReviewRequest
import com.researchai.cli.api.PRReviewResult

/**
 * Handler for PR review command
 */
class ReviewHandler(
    private val client: ResearchAiClient,
    private val echo: (String) -> Unit
) {

    suspend fun handle(
        prUrl: String,
        mode: String,
        focusAreas: String?,
        outputFormat: String,
        postComment: Boolean,
        useRag: Boolean = false,
        ragMinScore: Float = 0.7f,
        ragMaxChunks: Int = 10
    ) {
        try {
            // Parse PR URL
            val (owner, repo, prNumber) = parsePRUrl(prUrl)

            echo("\uD83D\uDD0D Reviewing PR #$prNumber in $owner/$repo...")
            echo("\uD83D\uDCCB Mode: ${mode.uppercase()}\n")

            if (focusAreas != null) {
                echo("\uD83C\uDFAF Focus: $focusAreas\n")
            }

            if (useRag) {
                echo("\uD83D\uDCDA RAG: Enabled (minScore=$ragMinScore, maxChunks=$ragMaxChunks)\n")
            }

            // Build request
            val request = PRReviewRequest(
                repositoryOwner = owner,
                repositoryName = repo,
                pullRequestNumber = prNumber,
                reviewMode = mode.uppercase(),
                focusAreas = parseFocusAreas(focusAreas),
                useRAG = useRag,
                ragMinScore = ragMinScore,
                ragMaxChunks = ragMaxChunks
            )

            // Execute review
            echo("⏳ Analyzing PR... (this may take 1-2 minutes)\n")
            val result = client.reviewPR(request)

            // Format output
            val formatter = ReviewOutputFormatter()
            val formatted = when (outputFormat.lowercase()) {
                "json" -> formatter.formatJson(result)
                "github" -> formatter.formatGitHubMarkdown(result)
                else -> formatter.formatText(result)
            }

            echo(formatted)

            // Post comment if requested
            if (postComment) {
                echo("\n\uD83D\uDCE4 Posting review as PR comment...")

                val githubToken = System.getenv("GITHUB_TOKEN")
                if (githubToken == null) {
                    echo("⚠️  GITHUB_TOKEN environment variable not set")
                    echo("   Set it to post comments: export GITHUB_TOKEN=your_token\n")
                } else {
                    try {
                        client.postReviewComment(result, githubToken)
                        echo("✅ Review comment posted successfully!\n")
                    } catch (e: Exception) {
                        echo("❌ Failed to post comment: ${e.message}\n")
                    }
                }
            }

            // Exit code based on score
            val threshold = 50
            if (result.overallScore < threshold) {
                echo("\n❌ Review score (${result.overallScore}) below threshold ($threshold)")
                System.exit(1)
            } else {
                echo("\n✅ Review score: ${result.overallScore}/100")
            }

        } catch (e: Exception) {
            echo("❌ Error: ${e.message}")
            throw e
        }
    }

    private fun parsePRUrl(url: String): Triple<String, String, Int> {
        // https://github.com/owner/repo/pull/123
        val regex = Regex("""github\.com/([^/]+)/([^/]+)/pull/(\d+)""")
        val match = regex.find(url)
            ?: throw IllegalArgumentException("Invalid GitHub PR URL: $url. Expected format: https://github.com/owner/repo/pull/123")

        val (owner, repo, prNumber) = match.destructured
        return Triple(owner, repo, prNumber.toInt())
    }

    private fun parseFocusAreas(focus: String?): List<String> {
        if (focus.isNullOrBlank()) return emptyList()

        return focus.split(",")
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() }
    }
}
