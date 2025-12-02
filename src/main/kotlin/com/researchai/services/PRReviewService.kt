package com.researchai.services

import com.researchai.domain.models.*
import com.researchai.domain.models.pr.*
import com.researchai.domain.models.mcp.MCPToolCallResult
import com.researchai.domain.models.mcp.MCPContent
import com.researchai.domain.provider.AIProviderFactory
import com.researchai.domain.repository.ConfigRepository
import com.researchai.data.mcp.MCPServerManager
import kotlinx.serialization.Serializable
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
    private val assistantManager: AssistantManager,
    private val ragManager: RAGManager,
    private val preferencesManager: PreferencesManager
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

            // 2. Retrieve RAG context if enabled
            val ragContext = if (request.useRAG) {
                logger.info("Retrieving RAG context for PR review...")
                retrieveRAGContext(prData, request)
            } else {
                null
            }

            // 3. Build review prompt with RAG context
            val reviewPrompt = buildReviewPrompt(prData, ragContext, request)

            // 4. Execute AI review
            val aiResponse = executeAIReview(reviewPrompt, request)

            // 5. Parse response
            val result = parseReviewResponse(aiResponse, prData, request, requestId)

            // 6. Add metadata including RAG usage
            result.copy(
                metadata = result.metadata.copy(
                    reviewDurationMs = System.currentTimeMillis() - startTime,
                    ragContextUsed = request.useRAG && ragContext != null,
                    ragChunksRetrieved = ragContext?.let { extractChunkCount(it) } ?: 0
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

        // Fetch PR details using pull_request_read with method="get"
        val prDetailsResult = githubClient.callTool(
            "pull_request_read",
            buildJsonObject {
                put("method", "get")
                put("owner", request.repositoryOwner)
                put("repo", request.repositoryName)
                put("pullNumber", request.pullRequestNumber)
            }
        )

        // Fetch PR diff using pull_request_read with method="get_diff"
        val diffResult = githubClient.callTool(
            "pull_request_read",
            buildJsonObject {
                put("method", "get_diff")
                put("owner", request.repositoryOwner)
                put("repo", request.repositoryName)
                put("pullNumber", request.pullRequestNumber)
            }
        )

        // Fetch changed files using pull_request_read with method="get_files"
        val filesResult = githubClient.callTool(
            "pull_request_read",
            buildJsonObject {
                put("method", "get_files")
                put("owner", request.repositoryOwner)
                put("repo", request.repositoryName)
                put("pullNumber", request.pullRequestNumber)
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
        // Get global preferences if providerId/model not specified in request
        val preferences = preferencesManager.getPreferences()

        // Use request values or fallback to global preferences
        val providerId = if (request.providerId == ProviderType.CLAUDE && request.model == null) {
            // Default CLAUDE not explicitly set, use preferences
            ProviderType.valueOf(preferences.providerId.uppercase())
        } else {
            request.providerId
        }

        val config = configRepository.getProviderConfig(providerId).getOrNull()
            ?: throw IllegalStateException("Provider $providerId configuration not found")
        val provider = aiProviderFactory.create(providerId, config)

        // Get system prompt from assistant
        val assistant = assistantManager.getAssistant(PR_REVIEWER_ASSISTANT_ID)
        val systemPrompt = assistant?.systemPrompt

        // Get model: request.model > preferences.model > config.defaultModel
        val model = request.model ?: preferences.model

        // Validate and correct temperature based on model capabilities
        val validatedTemperature = validateTemperature(
            provider = provider,
            model = model,
            temperature = preferences.temperature
        )

        // Get maxTokens from preferences (user set 64000 for gpt-5-nano)
        val maxTokens = preferences.maxTokens

        logger.info("Model: $model, Temperature: ${preferences.temperature} → $validatedTemperature, MaxTokens: $maxTokens")

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
                temperature = validatedTemperature,  // Use validated temperature
                maxTokens = maxTokens  // Use from global preferences
            )
        )

        val response = provider.sendMessage(aiRequest).getOrThrow()
        return response.content
    }

    /**
     * Validate temperature against model capabilities
     */
    private suspend fun validateTemperature(
        provider: com.researchai.domain.provider.AIProvider,
        model: String,
        temperature: Double
    ): Double {
        // Get model capabilities
        val modelsResult = provider.getModels()
        if (modelsResult.isFailure) {
            logger.warn("Failed to get models, using temperature as-is: $temperature")
            return temperature
        }

        val models = modelsResult.getOrNull() ?: emptyList()
        val modelCapabilities = models.find { it.id == model }?.capabilities

        if (modelCapabilities == null) {
            logger.warn("Model $model not found in provider models, using temperature as-is: $temperature")
            return temperature
        }

        // Validate temperature range
        val validatedTemp = temperature.coerceIn(
            modelCapabilities.temperatureMin,
            modelCapabilities.temperatureMax
        )

        if (validatedTemp != temperature) {
            logger.info("Temperature adjusted for model $model: $temperature → $validatedTemp (allowed range: ${modelCapabilities.temperatureMin}..${modelCapabilities.temperatureMax})")
        }

        return validatedTemp
    }

    /**
     * Intermediate structure for parsing AI response (without metadata, requestId, pullRequestUrl)
     */
    @Serializable
    private data class AIReviewResponse(
        val summary: ReviewSummary,
        val fileReviews: List<FileReview>,
        val overallScore: Int
    )

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
            // Parse into intermediate structure (AI doesn't return metadata, requestId, pullRequestUrl)
            val aiResponse = json.decodeFromString<AIReviewResponse>(cleanedResponse)

            // Convert to PRReviewResult with metadata
            PRReviewResult(
                requestId = requestId,
                pullRequestUrl = prData.details.url,
                summary = aiResponse.summary,
                fileReviews = aiResponse.fileReviews,
                overallScore = aiResponse.overallScore,
                metadata = ReviewMetadata(
                    reviewDurationMs = 0, // Will be updated in reviewPullRequest()
                    filesReviewed = prData.changedFiles.size,
                    linesAnalyzed = 0,
                    ragContextUsed = false, // Will be updated in reviewPullRequest()
                    ragChunksRetrieved = 0, // Will be updated in reviewPullRequest()
                    tokensUsed = 0,
                    model = request.model ?: "unknown",
                    provider = request.providerId.id
                )
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
        // Log the raw response to file for debugging
        try {
            java.io.File("/tmp/pr-review-raw-response-$requestId.txt").writeText(response)
            logger.warn("Raw AI response saved to /tmp/pr-review-raw-response-$requestId.txt (length: ${response.length})")
        } catch (e: Exception) {
            logger.warn("Failed to save raw response to file", e)
        }

        return PRReviewResult(
            requestId = requestId,
            pullRequestUrl = prData.details.url,
            summary = ReviewSummary(
                overview = response.take(50000), // Increased from 500 to 50000
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

    /**
     * Retrieve RAG context based on changed files in the PR
     */
    private suspend fun retrieveRAGContext(
        prData: PRData,
        request: PRReviewRequest
    ): String? {
        return try {
            // Build search queries from changed files
            val queries = buildSearchQueries(prData)

            if (queries.isEmpty()) {
                logger.warn("No search queries generated from PR data")
                return null
            }

            logger.info("Generated ${queries.size} search queries from ${prData.changedFiles.size} changed files")

            // Execute searches and collect results
            val allResults = mutableSetOf<SearchResult>()
            for (query in queries) {
                val results = ragManager.searchRelevantContext(
                    query = query,
                    topK = 5,
                    minScore = request.ragMinScore
                )
                allResults.addAll(results)
                logger.debug("Query '${query.take(50)}...' returned ${results.size} results")
            }

            if (allResults.isEmpty()) {
                logger.warn("No relevant context found in RAG for any query")
                return null
            }

            // Sort by score and take top N
            val topResults = allResults
                .sortedByDescending { it.score }
                .take(request.ragMaxChunks)

            logger.info("Retrieved ${topResults.size} unique chunks from RAG (${allResults.size} total before deduplication)")

            // Format context for LLM
            formatRAGContext(topResults)
        } catch (e: Exception) {
            logger.error("Failed to retrieve RAG context", e)
            null
        }
    }

    /**
     * Build search queries from changed files
     */
    private fun buildSearchQueries(prData: PRData): List<String> {
        val queries = mutableListOf<String>()

        for (file in prData.changedFiles.take(10)) { // Limit to first 10 files
            // Query 1: File path context
            val filePathQuery = "${file.filename} architecture implementation patterns"
            queries.add(filePathQuery)

            // Query 2: Extract identifiers from patch and search for usage examples
            file.patch?.let { patch ->
                val identifiers = extractIdentifiers(patch)
                identifiers.take(3).forEach { identifier ->
                    queries.add("$identifier usage examples best practices")
                }
            }
        }

        // Deduplicate and limit total queries
        return queries.distinct().take(20)
    }

    /**
     * Extract identifiers (class/function names) from a patch
     */
    private fun extractIdentifiers(patch: String): List<String> {
        val identifiers = mutableSetOf<String>()

        // Kotlin/Java class definitions
        val classPattern = Regex("""(?:class|interface|object|enum class)\s+(\w+)""")
        classPattern.findAll(patch).forEach {
            identifiers.add(it.groupValues[1])
        }

        // Kotlin/Java function definitions
        val functionPattern = Regex("""(?:fun|def|function)\s+(\w+)""")
        functionPattern.findAll(patch).forEach {
            identifiers.add(it.groupValues[1])
        }

        // JavaScript/TypeScript function/class definitions
        val jsPattern = Regex("""(?:function|class|const|let)\s+(\w+)""")
        jsPattern.findAll(patch).forEach {
            identifiers.add(it.groupValues[1])
        }

        return identifiers.toList()
    }

    /**
     * Format RAG results into context string for LLM
     */
    private fun formatRAGContext(results: List<SearchResult>): String {
        if (results.isEmpty()) return ""

        return buildString {
            appendLine("# Codebase Context (from RAG)")
            appendLine()
            appendLine("The following code examples and documentation from the repository may provide helpful context:")
            appendLine()

            results.groupBy { it.documentName }.forEach { (docName, chunks) ->
                appendLine("## Document: $docName")
                appendLine()
                chunks.forEachIndexed { idx, chunk ->
                    val relevancePercent = (chunk.score * 100).toInt()
                    appendLine("### Context ${idx + 1} (relevance: $relevancePercent%)")
                    chunk.sourceFileName?.let {
                        appendLine("**Source:** $it")
                        appendLine()
                    }
                    appendLine("```")
                    appendLine(chunk.text.trim())
                    appendLine("```")
                    appendLine()
                }
            }
        }
    }

    /**
     * Extract chunk count from formatted RAG context string
     */
    private fun extractChunkCount(context: String): Int {
        // Count "### Context N" occurrences
        return Regex("""### Context \d+""").findAll(context).count()
    }
}
