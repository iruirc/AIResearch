package com.researchai.services

import com.researchai.domain.models.*
import com.researchai.domain.models.pr.*
import com.researchai.domain.models.mcp.MCPToolCallResult
import com.researchai.domain.models.mcp.MCPContent
import com.researchai.domain.provider.AIProviderFactory
import com.researchai.domain.repository.ConfigRepository
import com.researchai.data.mcp.MCPServerManager
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Service for reviewing pull requests using AI and MCP integration
 */
class PRReviewService(
    private val mcpServerManager: MCPServerManager,
    private val aiProviderFactory: AIProviderFactory,
    private val configRepository: ConfigRepository,
    private val assistantManager: AssistantManager
) {
    companion object {
        const val GITHUB_MCP_SERVER_ID = "github"
        const val PR_REVIEWER_ASSISTANT_ID = "pr-reviewer"
        private val logger = LoggerFactory.getLogger(PRReviewService::class.java)
    }

    /**
     * Review a pull request
     */
    suspend fun reviewPullRequest(request: PRReviewRequest): Result<PRReviewResult> {
        val startTime = System.currentTimeMillis()
        val requestId = UUID.randomUUID().toString()

        return runCatching {
            logger.info("Starting PR review: $requestId for ${request.repositoryOwner}/${request.repositoryName}#${request.pullRequestNumber}")

            // 1. Fetch PR data via MCP
            val prData = fetchPRData(request)

            // 2. Build review prompt (without RAG for Phase 1)
            val reviewPrompt = buildReviewPrompt(prData, null, request)

            // 3. Execute AI review
            val aiResponse = executeAIReview(reviewPrompt, request)

            // 4. Parse response
            val result = parseReviewResponse(aiResponse, prData, request, requestId)

            // 5. Add metadata
            result.copy(
                metadata = result.metadata.copy(
                    reviewDurationMs = System.currentTimeMillis() - startTime
                )
            )
        }.onFailure { error ->
            logger.error("PR review failed: $requestId", error)
        }
    }

    /**
     * Fetch PR data from GitHub via MCP
     */
    private suspend fun fetchPRData(request: PRReviewRequest): PRData {
        val githubClient = mcpServerManager.getClient(GITHUB_MCP_SERVER_ID)
            ?: throw IllegalStateException("GitHub MCP server not connected. Please ensure GitHub MCP is configured.")

        // Fetch PR details
        val prDetailsResult = githubClient.callTool(
            "get_pull_request",
            buildJsonObject {
                put("owner", request.repositoryOwner)
                put("repo", request.repositoryName)
                put("pull_number", request.pullRequestNumber)
            }
        )

        // Fetch PR diff
        val diffResult = githubClient.callTool(
            "get_pull_request_diff",
            buildJsonObject {
                put("owner", request.repositoryOwner)
                put("repo", request.repositoryName)
                put("pull_number", request.pullRequestNumber)
            }
        )

        // Fetch changed files
        val filesResult = githubClient.callTool(
            "get_pull_request_files",
            buildJsonObject {
                put("owner", request.repositoryOwner)
                put("repo", request.repositoryName)
                put("pull_number", request.pullRequestNumber)
            }
        )

        return PRData(
            details = parsePRDetails(prDetailsResult),
            diff = parseDiff(diffResult),
            changedFiles = parseChangedFiles(filesResult)
        )
    }

    /**
     * Parse PR details from MCP response
     */
    private fun parsePRDetails(result: MCPToolCallResult): PRDetails {
        if (!result.success) {
            throw IllegalStateException("MCP tool call failed: ${result.error}")
        }

        val content = result.content.firstOrNull()
            ?: throw IllegalStateException("No content in MCP response")

        val json = Json.parseToJsonElement(content.text ?: "{}").jsonObject

        return PRDetails(
            number = json["number"]?.jsonPrimitive?.int ?: 0,
            title = json["title"]?.jsonPrimitive?.content ?: "",
            body = json["body"]?.jsonPrimitive?.contentOrNull,
            author = json["user"]?.jsonObject?.get("login")?.jsonPrimitive?.content ?: "",
            url = json["html_url"]?.jsonPrimitive?.content ?: ""
        )
    }

    /**
     * Parse diff from MCP response
     */
    private fun parseDiff(result: MCPToolCallResult): String {
        if (!result.success) {
            throw IllegalStateException("MCP tool call failed: ${result.error}")
        }

        val content = result.content.firstOrNull()
            ?: throw IllegalStateException("No content in MCP response")

        return content.text ?: ""
    }

    /**
     * Parse changed files from MCP response
     */
    private fun parseChangedFiles(result: MCPToolCallResult): List<ChangedFile> {
        if (!result.success) {
            throw IllegalStateException("MCP tool call failed: ${result.error}")
        }

        val content = result.content.firstOrNull()
            ?: throw IllegalStateException("No content in MCP response")

        val json = Json.parseToJsonElement(content.text ?: "[]")
        val filesArray = json.jsonArray

        return filesArray.map { fileJson ->
            val fileObj = fileJson.jsonObject
            ChangedFile(
                filename = fileObj["filename"]?.jsonPrimitive?.content ?: "",
                status = parseChangeType(fileObj["status"]?.jsonPrimitive?.content ?: "modified"),
                additions = fileObj["additions"]?.jsonPrimitive?.int ?: 0,
                deletions = fileObj["deletions"]?.jsonPrimitive?.int ?: 0,
                patch = fileObj["patch"]?.jsonPrimitive?.contentOrNull
            )
        }
    }

    /**
     * Parse change type from string
     */
    private fun parseChangeType(status: String): ChangeType {
        return when (status.lowercase()) {
            "added" -> ChangeType.ADDED
            "modified" -> ChangeType.MODIFIED
            "deleted" -> ChangeType.DELETED
            "renamed" -> ChangeType.RENAMED
            else -> ChangeType.MODIFIED
        }
    }

    /**
     * Build review prompt for AI
     */
    private suspend fun buildReviewPrompt(
        prData: PRData,
        ragContext: String?,  // null for Phase 1
        request: PRReviewRequest
    ): String {
        val assistant = assistantManager.getAssistant(PR_REVIEWER_ASSISTANT_ID)
        val systemPrompt = assistant?.systemPrompt
            ?: throw IllegalStateException("PR Reviewer assistant not found")

        val focusAreasText = request.focusAreas.joinToString(", ") { it.name }
        val reviewModeText = when (request.reviewMode) {
            ReviewMode.QUICK -> "quick review - summary only, no detailed line comments"
            ReviewMode.STANDARD -> "standard review - balanced with key issues highlighted"
            ReviewMode.THOROUGH -> "thorough review - detailed line-by-line analysis"
        }

        return buildString {
            appendLine("# Pull Request Review")
            appendLine()
            appendLine("## PR Information")
            appendLine("- **Title:** ${prData.details.title}")
            appendLine("- **Author:** ${prData.details.author}")
            appendLine("- **URL:** ${prData.details.url}")
            if (prData.details.body != null) {
                appendLine("- **Description:**")
                appendLine(prData.details.body)
            }
            appendLine()
            appendLine("## Review Configuration")
            appendLine("- **Mode:** $reviewModeText")
            appendLine("- **Focus Areas:** $focusAreasText (all have equal priority)")
            appendLine("- **Include Line Comments:** ${request.includeLineComments}")
            appendLine()

            if (ragContext != null) {
                appendLine("## Codebase Context (from RAG)")
                appendLine(ragContext)
                appendLine()
            }

            appendLine("## Changed Files (${prData.changedFiles.size} files)")
            prData.changedFiles.take(request.maxFilesToReview).forEach { file ->
                appendLine("- ${file.filename} (${file.status.name}, +${file.additions} -${file.deletions})")
            }
            appendLine()
            appendLine("## Unified Diff")
            appendLine("```diff")
            appendLine(prData.diff.take(50000)) // Limit diff size
            appendLine("```")
            appendLine()
            appendLine("Please review this PR following the guidelines in your system prompt.")
        }
    }

    /**
     * Execute AI review
     */
    private suspend fun executeAIReview(
        prompt: String,
        request: PRReviewRequest
    ): String {
        val config = configRepository.getProviderConfig(request.providerId).getOrNull()
            ?: throw IllegalStateException("Provider ${request.providerId} configuration not found")
        val provider = aiProviderFactory.create(request.providerId, config)

        // Get system prompt from assistant
        val assistant = assistantManager.getAssistant(PR_REVIEWER_ASSISTANT_ID)
        val systemPrompt = assistant?.systemPrompt

        // Get model name based on config type
        val model = request.model ?: when (config) {
            is ProviderConfig.ClaudeConfig -> config.defaultModel
            is ProviderConfig.OpenAIConfig -> config.defaultModel
            is ProviderConfig.HuggingFaceConfig -> config.defaultModel
            is ProviderConfig.OllamaConfig -> "llama2"  // Default for Ollama
            else -> throw IllegalStateException("Unknown provider config type")
        }

        val aiRequest = AIRequest(
            messages = listOf(
                Message(
                    role = MessageRole.USER,
                    content = MessageContent.Text(prompt)
                )
            ),
            model = model,
            systemPrompt = systemPrompt,
            parameters = RequestParameters(
                temperature = 0.3,  // Low for consistency
                maxTokens = 8192
            )
        )

        val response = provider.sendMessage(aiRequest).getOrThrow()
        return response.content
    }

    /**
     * Parse AI response into PRReviewResult
     */
    private fun parseReviewResponse(
        aiResponse: String,
        prData: PRData,
        request: PRReviewRequest,
        requestId: String
    ): PRReviewResult {
        // Try to parse JSON response
        val cleanedResponse = aiResponse
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        return try {
            val json = Json { ignoreUnknownKeys = true }
            val parsed = json.decodeFromString<PRReviewResult>(cleanedResponse)

            parsed.copy(
                requestId = requestId,
                pullRequestUrl = prData.details.url
            )
        } catch (e: Exception) {
            // Fallback: create basic result from text response
            logger.warn("Failed to parse JSON response, creating fallback result", e)
            createFallbackResult(aiResponse, prData, requestId)
        }
    }

    /**
     * Create fallback result when JSON parsing fails
     */
    private fun createFallbackResult(
        response: String,
        prData: PRData,
        requestId: String
    ): PRReviewResult {
        return PRReviewResult(
            requestId = requestId,
            pullRequestUrl = prData.details.url,
            summary = ReviewSummary(
                overview = response.take(500),
                criticalIssues = emptyList(),
                importantIssues = emptyList(),
                suggestions = emptyList(),
                positives = emptyList()
            ),
            fileReviews = emptyList(),
            overallScore = 50,
            metadata = ReviewMetadata(
                reviewDurationMs = 0,
                filesReviewed = prData.changedFiles.size,
                linesAnalyzed = 0,
                ragContextUsed = false,
                ragChunksRetrieved = 0,
                tokensUsed = 0,
                model = "unknown",
                provider = "unknown"
            )
        )
    }
}
