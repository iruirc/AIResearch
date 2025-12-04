package com.researchai.services

import com.researchai.data.mcp.MCPClientWrapper
import com.researchai.data.mcp.MCPServerManager
import com.researchai.domain.models.*
import com.researchai.domain.models.techsupport.*
import com.researchai.domain.models.workflow.*
import com.researchai.domain.provider.AIProviderFactory
import com.researchai.domain.repository.ConfigRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Service for handling tech support requests with RAG and Trello integration
 */
class TechSupportService(
    private val mcpServerManager: MCPServerManager,
    private val ragManager: RAGManager,
    private val aiProviderFactory: AIProviderFactory,
    private val configRepository: ConfigRepository,
    private val assistantManager: AssistantManager,
    private val preferencesManager: PreferencesManager,
    private val taskWorkflowService: TaskWorkflowService,
    private val config: TechSupportConfig = TechSupportConfig()
) {
    private val logger = LoggerFactory.getLogger(TechSupportService::class.java)

    companion object {
        const val TECH_SUPPORT_ASSISTANT_ID = "tech-support-assistant"
    }

    /**
     * Process a tech support request
     */
    suspend fun processRequest(request: TechSupportRequest): Result<TechSupportResponse> {
        val startTime = System.currentTimeMillis()

        return runCatching {
            logger.info("Processing tech support request: ${request.query.take(50)}...")

            // Check if this is a workflow query first (before AI classification to save tokens)
            if (taskWorkflowService.isWorkflowQuery(request.query)) {
                logger.info("Detected as workflow query, processing with TaskWorkflowService")
                return@runCatching processWorkflowRequest(request, startTime)
            }

            // 1. Classify the query type using AI
            val queryType = classifyQuery(request.query)
            logger.info("Query classified as: $queryType")

            // 2. Gather context in parallel from RAG and Trello
            val context = gatherContext(request, queryType)

            // 3. Use existing answer if provided, otherwise generate new one
            val aiResponse = if (request.existingAnswer != null) {
                logger.info("Using existing answer from main chat (skipping AI call)")
                request.existingAnswer
            } else {
                // Build enriched prompt with context
                val enrichedPrompt = buildEnrichedPrompt(request.query, context)
                // Execute AI request
                executeAIRequest(enrichedPrompt, request)
            }

            // 4. Extract suggested actions
            val suggestedActions = extractSuggestedActions(aiResponse, context, queryType, request.query)

            // Calculate GitHub item count
            val githubItemCount = (context.githubContext?.branches?.size ?: 0) +
                    (context.githubContext?.commits?.size ?: 0) +
                    (context.githubContext?.issues?.size ?: 0) +
                    (context.githubContext?.pullRequests?.size ?: 0)

            val githubSources = mutableListOf<String>()
            context.githubContext?.repositoryInfo?.let { githubSources.add(it.fullName) }

            TechSupportResponse(
                answer = aiResponse,
                sessionId = request.sessionId ?: "tech-support-${System.currentTimeMillis()}",
                queryType = queryType,
                sourcesUsed = SourcesUsed(
                    ragSourceCount = context.ragContext?.sourceCount ?: 0,
                    trelloTicketCount = context.ticketContext?.relatedTickets?.size ?: 0,
                    githubItemCount = githubItemCount,
                    ragSources = context.ragContext?.sources ?: emptyList(),
                    trelloSources = context.ticketContext?.relatedTickets?.map { it.cardName } ?: emptyList(),
                    githubSources = githubSources
                ),
                suggestedActions = suggestedActions,
                relatedTickets = context.ticketContext?.relatedTickets ?: emptyList(),
                processingTimeMs = System.currentTimeMillis() - startTime
            )
        }.onFailure { error ->
            logger.error("Tech support request failed", error)
        }
    }

    /**
     * Fast query classification using keyword heuristics (no AI call)
     * Can be used as fallback or for quick classification
     */
    @Suppress("unused")
    private fun classifyQueryFast(query: String): QueryType {
        val lowerQuery = query.lowercase()

        // Bug report keywords
        val bugKeywords = listOf("ошибка", "баг", "bug", "error", "crash", "не работает", "doesn't work",
            "сломалось", "broken", "падает", "exception", "failed", "fail", "проблема", "issue", "500", "404")
        if (bugKeywords.any { lowerQuery.contains(it) }) {
            return QueryType.BUG_REPORT
        }

        // How-to keywords
        val howToKeywords = listOf("как ", "how to", "how do", "как сделать", "как настроить",
            "как использовать", "configure", "setup", "set up", "настройка", "can i", "могу ли")
        if (howToKeywords.any { lowerQuery.contains(it) }) {
            return QueryType.HOW_TO
        }

        // Status check keywords
        val statusKeywords = listOf("статус", "status", "где мой", "what happened", "progress",
            "когда будет", "when will", "ticket", "тикет", "заявка")
        if (statusKeywords.any { lowerQuery.contains(it) }) {
            return QueryType.STATUS_CHECK
        }

        // Feature request keywords
        val featureKeywords = listOf("хотелось бы", "было бы хорошо", "feature", "добавить",
            "add", "implement", "want", "хочу", "можно ли добавить", "suggestion")
        if (featureKeywords.any { lowerQuery.contains(it) }) {
            return QueryType.FEATURE_REQUEST
        }

        // Project management keywords
        val projectKeywords = listOf("задачи", "tasks", "приоритет", "priority", "high", "medium", "low",
            "что делать", "what to do", "первым", "first", "статус проекта", "project status",
            "покажи задачи", "show tasks", "list tasks", "рекомендации", "recommend")
        if (projectKeywords.any { lowerQuery.contains(it) }) {
            return QueryType.PROJECT_MANAGEMENT
        }

        // GitHub keywords
        val githubKeywords = listOf("ветк", "branch", "коммит", "commit", "pull request", "pr", "issue",
            "репозитор", "repository", "repo", "git", "merge", "push", "код", "code review",
            "github", "ветви", "branches", "commits", "история изменений", "последние изменения")
        if (githubKeywords.any { lowerQuery.contains(it) }) {
            return QueryType.GITHUB_INFO
        }

        return QueryType.GENERAL
    }

    /**
     * Classify query type using AI
     */
    private suspend fun classifyQuery(query: String): QueryType {
        val classificationPrompt = """
Classify this user query into one of these categories:
- BUG_REPORT: User is reporting a bug, error, or something not working
- HOW_TO: User is asking how to do something
- STATUS_CHECK: User is asking about status of a ticket or issue
- FEATURE_REQUEST: User is requesting a new feature
- PROJECT_MANAGEMENT: User is asking about project status, task priorities, what to do next, or listing tasks
- GITHUB_INFO: User is asking about GitHub repository info (branches, commits, pull requests, issues, code changes)
- GENERAL: General question that doesn't fit above

Query: "$query"

Respond with ONLY the category name (e.g., "PROJECT_MANAGEMENT"), nothing else.
        """.trim()

        return try {
            val preferences = preferencesManager.getPreferences()
            val providerId = ProviderType.valueOf(preferences.providerId.uppercase())
            val providerConfig = configRepository.getProviderConfig(providerId)
                .getOrNull() ?: throw IllegalStateException("Provider $providerId not configured")
            val provider = aiProviderFactory.create(providerId, providerConfig)

            val aiRequest = AIRequest(
                messages = listOf(Message(MessageRole.USER, MessageContent.Text(classificationPrompt))),
                model = preferences.model,
                parameters = RequestParameters(temperature = 0.0, maxTokens = 50)
            )

            val response = provider.sendMessage(aiRequest).getOrThrow()
            val classification = response.content.trim().uppercase()

            QueryType.entries.find { it.name == classification } ?: QueryType.GENERAL
        } catch (e: Exception) {
            logger.warn("Failed to classify query, defaulting to GENERAL", e)
            QueryType.GENERAL
        }
    }

    /**
     * Gather context from RAG, Trello, and GitHub in parallel
     */
    private suspend fun gatherContext(
        request: TechSupportRequest,
        queryType: QueryType
    ): TechSupportContext = coroutineScope {
        val startTime = System.currentTimeMillis()

        val ragDeferred = if (request.includeRag) {
            async { fetchRagContext(request.query, request.maxRagResults) }
        } else null

        // For PROJECT_MANAGEMENT, fetch tasks by priority instead of text search
        val trelloDeferred = if (request.includeTrello) {
            async {
                if (queryType == QueryType.PROJECT_MANAGEMENT) {
                    val boardId = request.trelloBoardId ?: config.defaultBoardId
                    if (boardId != null) {
                        val priority = extractPriorityFromQuery(request.query)
                        val tasks = fetchTasksByPriority(
                            boardId = boardId,
                            priority = priority,
                            maxResults = request.maxTrelloResults * 3  // Get more for prioritization
                        )
                        if (tasks.isNotEmpty()) {
                            TicketContextResult(
                                relatedTickets = tasks,
                                formattedContext = formatTasksForPrompt(tasks, priority)
                            )
                        } else null
                    } else {
                        logger.warn("No board ID configured for PROJECT_MANAGEMENT")
                        null
                    }
                } else {
                    fetchTrelloContext(request.query, request.trelloBoardId, request.maxTrelloResults)
                }
            }
        } else null

        // Fetch GitHub context for GITHUB_INFO queries or when explicitly requested
        val githubDeferred = if (request.includeGithub && queryType == QueryType.GITHUB_INFO) {
            async {
                val owner = request.githubOwner ?: config.defaultGithubOwner
                val repo = request.githubRepo ?: config.defaultGithubRepo
                if (owner != null && repo != null) {
                    fetchGithubContext(request.query, owner, repo, request.maxGithubResults)
                } else {
                    logger.warn("No GitHub owner/repo configured for GITHUB_INFO query")
                    null
                }
            }
        } else null

        TechSupportContext(
            ragContext = ragDeferred?.await(),
            ticketContext = trelloDeferred?.await(),
            githubContext = githubDeferred?.await(),
            queryType = queryType,
            processingTimeMs = System.currentTimeMillis() - startTime
        )
    }

    /**
     * Fetch context from RAG knowledge base
     */
    private suspend fun fetchRagContext(query: String, maxResults: Int): RagContextResult? {
        return try {
            val results = ragManager.searchRelevantContext(
                query = query,
                topK = maxResults,
                minScore = config.ragMinScore
            )

            if (results.isEmpty()) {
                logger.info("No RAG results found for query")
                null
            } else {
                logger.info("Found ${results.size} RAG results")
                RagContextResult(
                    formattedContext = formatRagResults(results),
                    sourceCount = results.size,
                    sources = results.map { it.documentName }.distinct()
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to fetch RAG context", e)
            null
        }
    }

    /**
     * Set active board in Trello MCP server to ensure operations work with the correct board
     * @return true if board was set successfully, false otherwise
     */
    private suspend fun ensureActiveBoard(trelloClient: MCPClientWrapper, boardId: String): Boolean {
        val result = trelloClient.callTool(
            name = "set_active_board",
            arguments = buildJsonObject {
                put("boardId", boardId)
            }
        )
        if (!result.success) {
            logger.warn("Failed to set active board $boardId: ${result.error}")
            return false
        }
        logger.debug("Active board set to: $boardId")
        return true
    }

    /**
     * Fetch related tickets from Trello via MCP
     * Uses get_lists + get_cards_by_list_id since search_cards is not available
     */
    private suspend fun fetchTrelloContext(
        query: String,
        boardId: String?,
        maxResults: Int
    ): TicketContextResult? {
        return try {
            val trelloClient = mcpServerManager.getClient(config.trelloMcpServerId)
            if (trelloClient == null) {
                logger.warn("Trello MCP server not connected")
                return null
            }

            val effectiveBoardId = boardId ?: config.defaultBoardId
            if (effectiveBoardId == null) {
                logger.warn("No board ID configured for Trello search")
                return null
            }

            // Set active board to ensure we work with the correct board
            if (!ensureActiveBoard(trelloClient, effectiveBoardId)) {
                logger.warn("Could not set active board, continuing anyway")
            }

            // Get all lists on the board
            val listsResult = trelloClient.callTool(
                name = "get_lists",
                arguments = buildJsonObject {
                    put("boardId", effectiveBoardId)
                }
            )

            if (!listsResult.success) {
                logger.warn("Failed to get Trello lists: ${listsResult.error}")
                return null
            }

            val lists = parseListsFromMCP(listsResult)
            if (lists.isEmpty()) {
                logger.info("No lists found on board")
                return null
            }

            // Get cards from each list and filter by query
            val allTickets = mutableListOf<TrelloTicketInfo>()
            val queryWords = query.lowercase().split(Regex("\\s+")).filter { it.length > 2 }

            for (list in lists) {
                if (allTickets.size >= maxResults) break

                val cardsResult = trelloClient.callTool(
                    name = "get_cards_by_list_id",
                    arguments = buildJsonObject {
                        put("listId", list.id)
                    }
                )

                if (cardsResult.success) {
                    val cards = parseCardsFromListMCP(cardsResult, list.name)
                    // Filter cards that match the query
                    val matchingCards = cards.filter { card ->
                        val cardText = "${card.cardName} ${card.description ?: ""}".lowercase()
                        queryWords.any { word -> cardText.contains(word) }
                    }
                    allTickets.addAll(matchingCards.take(maxResults - allTickets.size))
                }
            }

            if (allTickets.isEmpty()) {
                logger.info("No related tickets found")
                null
            } else {
                logger.info("Found ${allTickets.size} related tickets")
                TicketContextResult(
                    relatedTickets = allTickets,
                    formattedContext = formatTicketResults(allTickets)
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to fetch Trello context", e)
            null
        }
    }

    /**
     * Parse lists from MCP result
     */
    private fun parseListsFromMCP(result: com.researchai.domain.models.mcp.MCPToolCallResult): List<TrelloListInfo> {
        val content = result.content.firstOrNull()?.text ?: return emptyList()

        return try {
            val json = Json.parseToJsonElement(content)
            val lists = when {
                json is JsonArray -> json
                json is JsonObject && json.containsKey("lists") -> json["lists"]?.jsonArray
                else -> null
            } ?: return emptyList()

            lists.mapNotNull { listJson ->
                try {
                    val list = listJson.jsonObject
                    TrelloListInfo(
                        id = list["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                        name = list["name"]?.jsonPrimitive?.content ?: "Unknown"
                    )
                } catch (e: Exception) {
                    logger.debug("Failed to parse list: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to parse lists MCP response", e)
            emptyList()
        }
    }

    /**
     * Parse cards from list MCP result
     */
    private fun parseCardsFromListMCP(
        result: com.researchai.domain.models.mcp.MCPToolCallResult,
        listName: String
    ): List<TrelloTicketInfo> {
        val content = result.content.firstOrNull()?.text ?: return emptyList()

        return try {
            val json = Json.parseToJsonElement(content)
            val cards = when {
                json is JsonArray -> json
                json is JsonObject && json.containsKey("cards") -> json["cards"]?.jsonArray
                else -> null
            } ?: return emptyList()

            cards.mapNotNull { cardJson ->
                try {
                    val card = cardJson.jsonObject
                    TrelloTicketInfo(
                        cardId = card["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                        cardName = card["name"]?.jsonPrimitive?.content ?: "Untitled",
                        listName = listName,
                        description = card["desc"]?.jsonPrimitive?.contentOrNull,
                        labels = card["labels"]?.jsonArray?.mapNotNull {
                            it.jsonObject["name"]?.jsonPrimitive?.contentOrNull
                        } ?: emptyList(),
                        lastActivity = card["dateLastActivity"]?.jsonPrimitive?.contentOrNull,
                        url = card["url"]?.jsonPrimitive?.contentOrNull
                            ?: card["shortUrl"]?.jsonPrimitive?.contentOrNull
                    )
                } catch (e: Exception) {
                    logger.debug("Failed to parse card from list: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to parse cards MCP response", e)
            emptyList()
        }
    }

    /**
     * Simple data class for Trello list info
     */
    private data class TrelloListInfo(
        val id: String,
        val name: String
    )

    /**
     * Parse tickets from MCP result
     */
    private fun parseTicketsFromMCP(result: com.researchai.domain.models.mcp.MCPToolCallResult): List<TrelloTicketInfo> {
        val content = result.content.firstOrNull()?.text ?: return emptyList()

        return try {
            val json = Json.parseToJsonElement(content)
            val cards = when {
                json is JsonArray -> json
                json is JsonObject && json.containsKey("cards") -> json["cards"]?.jsonArray
                else -> null
            } ?: return emptyList()

            cards.mapNotNull { cardJson ->
                try {
                    val card = cardJson.jsonObject
                    TrelloTicketInfo(
                        cardId = card["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                        cardName = card["name"]?.jsonPrimitive?.content ?: "Untitled",
                        listName = card["list"]?.jsonObject?.get("name")?.jsonPrimitive?.content
                            ?: card["idList"]?.jsonPrimitive?.content ?: "Unknown",
                        description = card["desc"]?.jsonPrimitive?.contentOrNull,
                        labels = card["labels"]?.jsonArray?.mapNotNull {
                            it.jsonObject["name"]?.jsonPrimitive?.contentOrNull
                        } ?: emptyList(),
                        lastActivity = card["dateLastActivity"]?.jsonPrimitive?.contentOrNull,
                        url = card["url"]?.jsonPrimitive?.contentOrNull
                            ?: card["shortUrl"]?.jsonPrimitive?.contentOrNull
                    )
                } catch (e: Exception) {
                    logger.debug("Failed to parse card: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to parse MCP response", e)
            emptyList()
        }
    }

    /**
     * Format RAG results for the prompt
     */
    private fun formatRagResults(results: List<SearchResult>): String {
        return buildString {
            appendLine("=== DOCUMENTATION CONTEXT ===")
            results.forEachIndexed { idx, result ->
                appendLine("\n[Source ${idx + 1}: ${result.documentName}]")
                result.sourceFileName?.let { appendLine("File: $it") }
                appendLine(result.text.trim())
            }
        }
    }

    /**
     * Format tickets for the prompt
     */
    private fun formatTicketResults(tickets: List<TrelloTicketInfo>): String {
        return buildString {
            appendLine("=== RELATED TICKETS ===")
            tickets.forEachIndexed { idx, ticket ->
                appendLine("\n[Ticket ${idx + 1}: ${ticket.cardName}]")
                appendLine("Status: ${ticket.listName}")
                appendLine("Labels: ${ticket.labels.joinToString(", ").ifEmpty { "None" }}")
                ticket.description?.take(300)?.let { appendLine("Description: $it") }
                ticket.lastActivity?.let { appendLine("Last Activity: $it") }
                ticket.url?.let { appendLine("URL: $it") }
            }
        }
    }

    /**
     * Build enriched prompt with context
     */
    private fun buildEnrichedPrompt(query: String, context: TechSupportContext): String {
        // Use specialized prompt for PROJECT_MANAGEMENT queries
        if (context.queryType == QueryType.PROJECT_MANAGEMENT) {
            return buildProjectManagementPrompt(query, context)
        }

        // Use specialized prompt for GITHUB_INFO queries
        if (context.queryType == QueryType.GITHUB_INFO) {
            return buildGithubPrompt(query, context)
        }

        return buildString {
            appendLine("# User Query")
            appendLine(query)
            appendLine()
            appendLine("# Query Type: ${context.queryType}")
            appendLine()

            context.ragContext?.let {
                appendLine(it.formattedContext)
                appendLine()
            }

            context.ticketContext?.let {
                appendLine(it.formattedContext)
                appendLine()
            }

            context.githubContext?.let {
                appendLine(it.formattedContext)
                appendLine()
            }

            if (context.ragContext == null && context.ticketContext == null && context.githubContext == null) {
                appendLine("# Note: No additional context available")
                appendLine()
            }

            appendLine("# Instructions")
            appendLine("Please provide a helpful response based on the context above.")
            appendLine("If the issue cannot be resolved, suggest creating a support ticket.")
        }
    }

    /**
     * Execute AI request with tech support assistant
     */
    private suspend fun executeAIRequest(prompt: String, request: TechSupportRequest): String {
        val preferences = preferencesManager.getPreferences()
        val providerId = request.providerId
        val providerConfig = configRepository.getProviderConfig(providerId)
            .getOrNull() ?: throw IllegalStateException("Provider $providerId not configured")
        val provider = aiProviderFactory.create(providerId, providerConfig)

        val assistant = assistantManager.getAssistant(TECH_SUPPORT_ASSISTANT_ID)
        val systemPrompt = assistant?.systemPrompt

        val aiRequest = AIRequest(
            messages = listOf(Message(MessageRole.USER, MessageContent.Text(prompt))),
            model = request.model ?: preferences.model,
            systemPrompt = systemPrompt,
            parameters = RequestParameters(
                temperature = preferences.temperature,
                maxTokens = preferences.maxTokens
            )
        )

        return provider.sendMessage(aiRequest).getOrThrow().content
    }

    /**
     * Extract suggested actions from response and context
     */
    private fun extractSuggestedActions(
        response: String,
        context: TechSupportContext,
        queryType: QueryType,
        originalQuery: String
    ): List<SuggestedActionWrapper> {
        val actions = mutableListOf<SuggestedActionWrapper>()
        val hasRelatedTickets = context.ticketContext?.relatedTickets?.isNotEmpty() == true
        val hasRagContext = context.ragContext != null && context.ragContext.sourceCount > 0
        val ragSourceCount = context.ragContext?.sourceCount ?: 0
        val tasks = context.ticketContext?.relatedTickets ?: emptyList()

        // Handle PROJECT_MANAGEMENT queries specially
        if (queryType == QueryType.PROJECT_MANAGEMENT && tasks.isNotEmpty()) {
            val priority = extractPriorityFromQuery(originalQuery)

            // 1. Add LIST_TASKS action
            actions.add(SuggestedActionWrapper(
                actionType = "LIST_TASKS",
                listTasks = ListTasksAction(
                    filter = priority ?: "all",
                    tasks = tasks.map { task ->
                        TaskSummary(
                            cardId = task.cardId,
                            cardName = task.cardName,
                            listName = task.listName,
                            priority = task.labels.firstOrNull {
                                it.lowercase() in listOf("high", "medium", "low")
                            },
                            url = task.url
                        )
                    },
                    totalCount = tasks.size
                )
            ))

            // 2. Add PRIORITIZE action with AI recommendations if multiple tasks
            if (tasks.size > 1) {
                val recommendations = extractPrioritization(response, tasks)
                if (recommendations.isNotEmpty()) {
                    actions.add(SuggestedActionWrapper(
                        actionType = "PRIORITIZE",
                        prioritize = PrioritizeAction(
                            recommendedOrder = recommendations
                        )
                    ))
                }
            }

            // 3. Add VIEW_TICKET for top priority tasks (clickable links)
            tasks.take(3).forEach { task ->
                actions.add(SuggestedActionWrapper(
                    actionType = "VIEW_TICKET",
                    viewTicket = ViewTicketAction(
                        cardId = task.cardId,
                        cardName = task.cardName,
                        reason = "Priority task",
                        url = task.url
                    )
                ))
            }

            return actions
        }

        // Handle GITHUB_INFO queries specially
        if (queryType == QueryType.GITHUB_INFO && context.githubContext != null) {
            val github = context.githubContext

            // Add LIST_BRANCHES action if we have branches
            if (github.branches.isNotEmpty()) {
                actions.add(SuggestedActionWrapper(
                    actionType = "LIST_BRANCHES",
                    listBranches = ListBranchesAction(
                        branches = github.branches,
                        defaultBranch = github.branches.find { it.isDefault }?.name,
                        totalCount = github.branches.size
                    )
                ))
            }

            // Add VIEW_REPO action
            github.repositoryInfo?.let { repo ->
                actions.add(SuggestedActionWrapper(
                    actionType = "VIEW_REPO",
                    viewRepo = ViewRepoAction(
                        owner = repo.owner,
                        name = repo.name,
                        defaultBranch = repo.defaultBranch,
                        description = repo.description,
                        url = repo.url
                    )
                ))
            }

            // Add VIEW_BRANCH for each branch (limit to 5)
            github.branches.take(5).forEach { branch ->
                actions.add(SuggestedActionWrapper(
                    actionType = "VIEW_BRANCH",
                    viewBranch = ViewBranchAction(
                        branchName = branch.name,
                        sha = branch.sha,
                        isDefault = branch.isDefault,
                        url = branch.url
                    )
                ))
            }

            // Add VIEW_COMMIT for recent commits (limit to 3)
            github.commits.take(3).forEach { commit ->
                actions.add(SuggestedActionWrapper(
                    actionType = "VIEW_COMMIT",
                    viewCommit = ViewCommitAction(
                        sha = commit.sha,
                        message = commit.message,
                        author = commit.author,
                        url = commit.url
                    )
                ))
            }

            // Add VIEW_ISSUE for open issues (limit to 3)
            github.issues.take(3).forEach { issue ->
                actions.add(SuggestedActionWrapper(
                    actionType = "VIEW_ISSUE",
                    viewIssue = ViewIssueAction(
                        number = issue.number,
                        title = issue.title,
                        state = issue.state,
                        url = issue.url
                    )
                ))
            }

            // Add VIEW_PR for open pull requests (limit to 3)
            github.pullRequests.take(3).forEach { pr ->
                actions.add(SuggestedActionWrapper(
                    actionType = "VIEW_PR",
                    viewPullRequest = ViewPullRequestAction(
                        number = pr.number,
                        title = pr.title,
                        state = pr.state,
                        sourceBranch = pr.sourceBranch,
                        targetBranch = pr.targetBranch,
                        url = pr.url
                    )
                ))
            }

            return actions
        }

        // 1. CREATE_TICKET - для багов, feature requests и HOW_TO вопросов
        when (queryType) {
            QueryType.BUG_REPORT -> {
                actions.add(SuggestedActionWrapper(
                    actionType = "CREATE_TICKET",
                    createTicket = CreateTicketAction(
                        title = "Bug: ${extractBugTitle(originalQuery)}",
                        description = "Пользователь сообщил о проблеме:\n\n$originalQuery",
                        suggestedList = "Bugs",
                        suggestedLabels = listOf("bug", "needs-triage")
                    )
                ))
            }
            QueryType.FEATURE_REQUEST -> {
                actions.add(SuggestedActionWrapper(
                    actionType = "CREATE_TICKET",
                    createTicket = CreateTicketAction(
                        title = "Feature: ${extractBugTitle(originalQuery)}",
                        description = "Запрос на новую функцию:\n\n$originalQuery",
                        suggestedList = "Ideas",
                        suggestedLabels = listOf("feature-request", "needs-review")
                    )
                ))
            }
            QueryType.HOW_TO -> {
                // Всегда предлагаем создать тикет для HOW_TO вопросов (если ответ не помог)
                actions.add(SuggestedActionWrapper(
                    actionType = "CREATE_TICKET",
                    createTicket = CreateTicketAction(
                        title = extractBugTitle(originalQuery),
                        description = "Вопрос пользователя:\n\n$originalQuery",
                        suggestedList = "Support",
                        suggestedLabels = listOf("question", "how-to")
                    )
                ))
            }
            else -> {}
        }

        // 2. VIEW_TICKET - показываем связанные тикеты
        context.ticketContext?.relatedTickets?.take(3)?.forEach { ticket ->
            actions.add(SuggestedActionWrapper(
                actionType = "VIEW_TICKET",
                viewTicket = ViewTicketAction(
                    cardId = ticket.cardId,
                    cardName = ticket.cardName,
                    reason = "Похожая проблема",
                    url = ticket.url
                )
            ))
        }

        // 3. ADD_TO_FAQ - для HOW_TO вопросов если RAG нашёл релевантный контекст
        if (queryType == QueryType.HOW_TO && hasRagContext && ragSourceCount >= 1) {
            actions.add(SuggestedActionWrapper(
                actionType = "ADD_TO_FAQ",
                addToFaq = AddToFaqAction(
                    question = originalQuery,
                    suggestedAnswer = extractShortAnswer(response),
                    category = "how-to"
                )
            ))
        }

        // 4. ESCALATE - если нет полезного контекста и это серьёзный вопрос
        if (!hasRagContext && !hasRelatedTickets && queryType in listOf(QueryType.BUG_REPORT, QueryType.GENERAL)) {
            actions.add(SuggestedActionWrapper(
                actionType = "ESCALATE",
                escalate = EscalateAction(
                    reason = "Не найдено релевантной информации для ответа",
                    priority = if (queryType == QueryType.BUG_REPORT) "high" else "normal",
                    suggestedTeam = "support"
                )
            ))
        }

        // 5. CONTACT_SUPPORT - для STATUS_CHECK без найденных тикетов
        if (queryType == QueryType.STATUS_CHECK && !hasRelatedTickets) {
            actions.add(SuggestedActionWrapper(
                actionType = "CONTACT_SUPPORT",
                contactSupport = ContactSupportAction(
                    reason = "Не удалось найти тикет по запросу",
                    suggestedChannel = "email"
                )
            ))
        }

        // 6. Общее предложение создать тикет если нет других действий и вопрос нетривиальный
        if (actions.isEmpty() && originalQuery.length > 10) {
            actions.add(SuggestedActionWrapper(
                actionType = "CREATE_TICKET",
                createTicket = CreateTicketAction(
                    title = extractBugTitle(originalQuery),
                    description = "Вопрос пользователя:\n\n$originalQuery",
                    suggestedList = "Inbox",
                    suggestedLabels = listOf("support", "needs-triage")
                )
            ))
        }

        return actions
    }

    /**
     * Extract a short answer from AI response for FAQ
     */
    private fun extractShortAnswer(response: String): String {
        // Take first 500 characters or first paragraph
        val firstParagraph = response.split("\n\n").firstOrNull() ?: response
        return if (firstParagraph.length > 500) {
            firstParagraph.take(497) + "..."
        } else {
            firstParagraph
        }
    }

    /**
     * Extract a short bug title from the response
     */
    private fun extractBugTitle(response: String): String {
        // Try to find a summary or use first sentence
        val firstSentence = response.split(Regex("[.!?]")).firstOrNull()?.trim() ?: "Issue"
        return if (firstSentence.length > 50) {
            firstSentence.take(47) + "..."
        } else {
            firstSentence
        }
    }

    /**
     * Get list ID by name from Trello board
     */
    private suspend fun getListIdByName(trelloClient: MCPClientWrapper, boardId: String, listName: String): String? {
        val result = trelloClient.callTool(
            name = "get_lists",
            arguments = buildJsonObject {
                put("boardId", boardId)
            }
        )

        if (!result.success) {
            logger.warn("Failed to get lists: ${result.error}")
            return null
        }

        val content = result.content.firstOrNull()?.text ?: return null
        return try {
            val json = Json.parseToJsonElement(content)
            val lists = when {
                json is JsonArray -> json
                json is JsonObject && json.containsKey("lists") -> json["lists"]?.jsonArray
                else -> null
            } ?: return null

            lists.firstOrNull { list ->
                list.jsonObject["name"]?.jsonPrimitive?.content?.equals(listName, ignoreCase = true) == true
            }?.jsonObject?.get("id")?.jsonPrimitive?.content
        } catch (e: Exception) {
            logger.warn("Failed to parse lists response: ${e.message}")
            null
        }
    }

    /**
     * Create a ticket in Trello
     */
    suspend fun createTicket(request: CreateTicketRequest): Result<CreateTicketResponse> {
        return runCatching {
            val trelloClient = mcpServerManager.getClient(config.trelloMcpServerId)
                ?: throw IllegalStateException("Trello MCP server not connected")

            val boardId = request.boardId ?: config.defaultBoardId
                ?: throw IllegalArgumentException("Board ID is required. Set TRELLO_SUPPORT_BOARD_ID env variable or provide boardId.")

            // Set active board to ensure we work with the correct board
            if (!ensureActiveBoard(trelloClient, boardId)) {
                logger.warn("Could not set active board for createTicket, continuing anyway")
            }

            // Get list ID by name
            val listId = getListIdByName(trelloClient, boardId, request.listName)
                ?: throw IllegalArgumentException("List '${request.listName}' not found on board $boardId")

            logger.info("Creating ticket: ${request.title} on board: $boardId, list: $listId (${request.listName})")

            val result = trelloClient.callTool(
                name = "add_card_to_list",
                arguments = buildJsonObject {
                    put("boardId", boardId)
                    put("listId", listId)
                    put("name", request.title)
                    put("description", request.description)
                    if (request.labels.isNotEmpty()) {
                        put("labels", JsonArray(request.labels.map { JsonPrimitive(it) }))
                    }
                }
            )

            if (!result.success) {
                throw RuntimeException("Failed to create card: ${result.error}")
            }

            val content = result.content.firstOrNull()?.text ?: "{}"
            val json = Json.parseToJsonElement(content).jsonObject

            CreateTicketResponse(
                success = true,
                cardId = json["id"]?.jsonPrimitive?.content,
                cardUrl = json["url"]?.jsonPrimitive?.content
                    ?: json["shortUrl"]?.jsonPrimitive?.content
            )
        }.onFailure { error ->
            logger.error("Failed to create ticket", error)
        }
    }

    /**
     * Add FAQ entry to RAG knowledge base
     */
    suspend fun addFaq(request: AddFaqRequest): Result<AddFaqResponse> {
        return runCatching {
            val category = request.category ?: "general"
            val timestamp = System.currentTimeMillis()
            val docName = "FAQ_${category}_$timestamp"

            // Create markdown content for FAQ
            val content = buildString {
                appendLine("# FAQ: ${request.question}")
                appendLine()
                appendLine("**Category:** $category")
                appendLine()
                appendLine("## Question")
                appendLine(request.question)
                appendLine()
                appendLine("## Answer")
                appendLine(request.answer)
                appendLine()
                appendLine("---")
                appendLine("*Added to FAQ: ${java.time.Instant.ofEpochMilli(timestamp)}*")
            }

            logger.info("Adding FAQ to RAG: $docName")

            val document = ragManager.addDocument(
                name = docName,
                content = content,
                chunkingStrategy = ChunkingStrategy.SEMANTIC,
                enabled = true,
                originalFileName = "faq_${category}_$timestamp.md"
            )

            logger.info("FAQ added successfully: ${document.id}, chunks: ${document.chunks.size}")

            AddFaqResponse(
                success = true,
                documentId = document.id,
                documentName = document.name
            )
        }.onFailure { error ->
            logger.error("Failed to add FAQ", error)
        }
    }

    /**
     * Check if Trello is connected
     */
    fun isTrelloConnected(): Boolean {
        return mcpServerManager.getClient(config.trelloMcpServerId) != null
    }

    // ==================== PROJECT MANAGEMENT METHODS ====================

    /**
     * Extract priority filter from user query
     * @return "high", "medium", "low", or null for all priorities
     */
    private fun extractPriorityFromQuery(query: String): String? {
        val lowerQuery = query.lowercase()
        return when {
            lowerQuery.contains("high") || lowerQuery.contains("высок") || lowerQuery.contains("важн") -> "high"
            lowerQuery.contains("medium") || lowerQuery.contains("средн") -> "medium"
            lowerQuery.contains("low") || lowerQuery.contains("низк") -> "low"
            else -> null  // Return all tasks
        }
    }

    /**
     * Fetch tasks from Trello filtered by priority label
     * @param boardId Trello board ID
     * @param priority Priority filter: "high", "medium", "low", or null for all
     * @param maxResults Maximum tasks to return
     */
    private suspend fun fetchTasksByPriority(
        boardId: String,
        priority: String?,
        maxResults: Int
    ): List<TrelloTicketInfo> {
        val trelloClient = mcpServerManager.getClient(config.trelloMcpServerId)
            ?: return emptyList()

        // Set active board to ensure we work with the correct board
        if (!ensureActiveBoard(trelloClient, boardId)) {
            logger.warn("Could not set active board for fetchTasksByPriority, continuing anyway")
        }

        // Get all lists
        val listsResult = trelloClient.callTool(
            name = "get_lists",
            arguments = buildJsonObject { put("boardId", boardId) }
        )
        if (!listsResult.success) {
            logger.warn("Failed to get lists for project management: ${listsResult.error}")
            return emptyList()
        }

        val lists = parseListsFromMCP(listsResult)
        val allTasks = mutableListOf<TrelloTicketInfo>()

        // Get cards from each list
        for (list in lists) {
            if (allTasks.size >= maxResults) break

            val cardsResult = trelloClient.callTool(
                name = "get_cards_by_list_id",
                arguments = buildJsonObject { put("listId", list.id) }
            )

            if (cardsResult.success) {
                val cards = parseCardsFromListMCP(cardsResult, list.name)

                // Filter by priority if specified
                val filtered = if (priority != null) {
                    cards.filter { card ->
                        card.labels.any { it.equals(priority, ignoreCase = true) }
                    }
                } else {
                    cards
                }

                allTasks.addAll(filtered.take(maxResults - allTasks.size))
            }
        }

        logger.info("Fetched ${allTasks.size} tasks with priority filter: ${priority ?: "all"}")
        return allTasks
    }

    /**
     * Get project status summary from Trello board
     */
    private suspend fun getProjectStatus(boardId: String): ProjectStatusAction? {
        val trelloClient = mcpServerManager.getClient(config.trelloMcpServerId)
            ?: return null

        // Set active board to ensure we work with the correct board
        if (!ensureActiveBoard(trelloClient, boardId)) {
            logger.warn("Could not set active board for getProjectStatus, continuing anyway")
        }

        val listsResult = trelloClient.callTool(
            name = "get_lists",
            arguments = buildJsonObject { put("boardId", boardId) }
        )
        if (!listsResult.success) {
            logger.warn("Failed to get lists for project status: ${listsResult.error}")
            return null
        }

        val lists = parseListsFromMCP(listsResult)
        val byStatus = mutableMapOf<String, Int>()
        val byPriority = mutableMapOf("high" to 0, "medium" to 0, "low" to 0, "none" to 0)
        var totalTasks = 0

        for (list in lists) {
            val cardsResult = trelloClient.callTool(
                name = "get_cards_by_list_id",
                arguments = buildJsonObject { put("listId", list.id) }
            )

            if (cardsResult.success) {
                val cards = parseCardsFromListMCP(cardsResult, list.name)
                byStatus[list.name] = cards.size
                totalTasks += cards.size

                // Count by priority
                cards.forEach { card ->
                    val cardPriority = card.labels.firstOrNull {
                        it.lowercase() in listOf("high", "medium", "low")
                    }?.lowercase() ?: "none"
                    byPriority[cardPriority] = (byPriority[cardPriority] ?: 0) + 1
                }
            }
        }

        logger.info("Project status: $totalTasks total tasks")
        return ProjectStatusAction(
            totalTasks = totalTasks,
            byPriority = byPriority.filterValues { it > 0 },
            byStatus = byStatus
        )
    }

    /**
     * Format tasks for AI prompt in PROJECT_MANAGEMENT queries
     */
    private fun formatTasksForPrompt(tasks: List<TrelloTicketInfo>, priority: String?): String {
        return buildString {
            appendLine("=== PROJECT TASKS ===")
            appendLine("Filter: ${priority ?: "all priorities"}")
            appendLine("Total: ${tasks.size} tasks")
            appendLine()

            tasks.forEachIndexed { idx, task ->
                appendLine("[Task ${idx + 1}]")
                appendLine("Name: ${task.cardName}")
                appendLine("Status: ${task.listName}")
                val taskPriority = task.labels.firstOrNull {
                    it.lowercase() in listOf("high", "medium", "low")
                } ?: "none"
                appendLine("Priority: $taskPriority")
                appendLine("Labels: ${task.labels.joinToString(", ").ifEmpty { "none" }}")
                task.description?.take(200)?.let { appendLine("Description: $it") }
                appendLine()
            }
        }
    }

    /**
     * Build prompt specifically for PROJECT_MANAGEMENT queries
     */
    private fun buildProjectManagementPrompt(
        query: String,
        context: TechSupportContext
    ): String {
        return buildString {
            appendLine("# User Query")
            appendLine(query)
            appendLine()

            appendLine("# Query Type: PROJECT_MANAGEMENT")
            appendLine()

            // Add task context
            context.ticketContext?.let {
                appendLine(it.formattedContext)
                appendLine()
            }

            // Add RAG context (project documentation)
            context.ragContext?.let {
                appendLine(it.formattedContext)
                appendLine()
            }

            appendLine("# Instructions")
            appendLine("""
You are a project management assistant. Based on the tasks and project context above:

1. If user asks about task priorities or what to do first:
   - Analyze each task's importance based on priority labels (high > medium > low)
   - Consider task status (To Do tasks need attention first)
   - Consider any dependencies mentioned in descriptions
   - Recommend a clear order to tackle tasks
   - Explain your reasoning briefly for each recommendation

2. If user asks about project status:
   - Summarize the current state (total tasks, by priority, by status)
   - Highlight any blockers or risks
   - Suggest next steps

3. Format your response clearly with:
   - Numbered recommendations
   - Brief reasoning for each
   - Any warnings about blockers or dependencies

Respond in the same language as the user's query.
            """.trimIndent())
        }
    }

    /**
     * Extract task prioritization recommendations from AI response
     */
    private fun extractPrioritization(
        response: String,
        tasks: List<TrelloTicketInfo>
    ): List<TaskRecommendation> {
        val recommendations = mutableListOf<TaskRecommendation>()

        // Match task names mentioned in response and extract order
        val mentionedTasks = tasks.filter { task ->
            response.contains(task.cardName, ignoreCase = true)
        }

        // Add mentioned tasks in order they appear in response
        mentionedTasks.forEachIndexed { idx, task ->
            recommendations.add(TaskRecommendation(
                order = idx + 1,
                cardId = task.cardId,
                cardName = task.cardName,
                reason = "Recommended by AI analysis"
            ))
        }

        // Add remaining tasks at the end (those not mentioned by AI)
        tasks.filter { it !in mentionedTasks }.forEachIndexed { idx, task ->
            recommendations.add(TaskRecommendation(
                order = recommendations.size + idx + 1,
                cardId = task.cardId,
                cardName = task.cardName,
                reason = "Additional task"
            ))
        }

        return recommendations
    }

    // ==================== GitHub Integration Methods ====================

    /**
     * Fetch context from GitHub via MCP
     */
    private suspend fun fetchGithubContext(
        query: String,
        owner: String,
        repo: String,
        maxResults: Int
    ): GitHubContextResult? {
        return try {
            val githubClient = mcpServerManager.getClient(config.githubMcpServerId)
            if (githubClient == null) {
                logger.warn("GitHub MCP server not connected")
                return null
            }

            logger.info("Fetching GitHub context for $owner/$repo")

            // Determine what type of GitHub info to fetch based on query
            val queryLower = query.lowercase()
            val branches = mutableListOf<GitHubBranchInfo>()
            val commits = mutableListOf<GitHubCommitInfo>()
            val issues = mutableListOf<GitHubIssueInfo>()
            val pullRequests = mutableListOf<GitHubPRInfo>()

            // Fetch branches if query mentions branches
            if (queryLower.contains("ветк") || queryLower.contains("branch")) {
                branches.addAll(fetchGithubBranches(githubClient, owner, repo, maxResults))
            }

            // Fetch commits if query mentions commits
            if (queryLower.contains("коммит") || queryLower.contains("commit") ||
                queryLower.contains("изменен") || queryLower.contains("change")) {
                commits.addAll(fetchGithubCommits(githubClient, owner, repo, maxResults))
            }

            // Fetch issues if query mentions issues
            if (queryLower.contains("issue") || queryLower.contains("проблем") ||
                queryLower.contains("баг") || queryLower.contains("bug")) {
                issues.addAll(fetchGithubIssues(githubClient, owner, repo, maxResults))
            }

            // Fetch PRs if query mentions pull requests
            if (queryLower.contains("pr") || queryLower.contains("pull") ||
                queryLower.contains("мерж") || queryLower.contains("merge")) {
                pullRequests.addAll(fetchGithubPullRequests(githubClient, owner, repo, maxResults))
            }

            // If no specific query, fetch basic info (branches and recent commits)
            if (branches.isEmpty() && commits.isEmpty() && issues.isEmpty() && pullRequests.isEmpty()) {
                branches.addAll(fetchGithubBranches(githubClient, owner, repo, maxResults))
                commits.addAll(fetchGithubCommits(githubClient, owner, repo, 5))
            }

            val repoInfo = GitHubRepoInfo(
                owner = owner,
                name = repo,
                fullName = "$owner/$repo",
                defaultBranch = branches.find { it.isDefault }?.name ?: "main",
                description = null,
                url = "https://github.com/$owner/$repo"
            )

            GitHubContextResult(
                formattedContext = formatGithubContext(branches, commits, issues, pullRequests, repoInfo),
                branches = branches,
                commits = commits,
                issues = issues,
                pullRequests = pullRequests,
                repositoryInfo = repoInfo
            )
        } catch (e: Exception) {
            logger.error("Failed to fetch GitHub context", e)
            null
        }
    }

    /**
     * Fetch branches from GitHub via MCP
     */
    private suspend fun fetchGithubBranches(
        githubClient: MCPClientWrapper,
        owner: String,
        repo: String,
        maxResults: Int
    ): List<GitHubBranchInfo> {
        val result = githubClient.callTool(
            name = "list_branches",
            arguments = buildJsonObject {
                put("owner", owner)
                put("repo", repo)
            }
        )

        if (!result.success) {
            logger.warn("Failed to fetch branches: ${result.error}")
            return emptyList()
        }

        val content = result.content.firstOrNull()?.text ?: return emptyList()
        return try {
            val json = Json.parseToJsonElement(content)
            val branchesArray = when {
                json is JsonArray -> json
                json is JsonObject && json.containsKey("branches") -> json["branches"]?.jsonArray
                else -> null
            } ?: return emptyList()

            branchesArray.take(maxResults).mapNotNull { branchJson ->
                try {
                    val branch = branchJson.jsonObject
                    GitHubBranchInfo(
                        name = branch["name"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                        sha = branch["commit"]?.jsonObject?.get("sha")?.jsonPrimitive?.content
                            ?: branch["sha"]?.jsonPrimitive?.content ?: "",
                        isProtected = branch["protected"]?.jsonPrimitive?.booleanOrNull ?: false,
                        isDefault = branch["name"]?.jsonPrimitive?.content == "main" ||
                                   branch["name"]?.jsonPrimitive?.content == "master",
                        url = "https://github.com/$owner/$repo/tree/${branch["name"]?.jsonPrimitive?.content}"
                    )
                } catch (e: Exception) {
                    logger.debug("Failed to parse branch: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to parse branches response", e)
            emptyList()
        }
    }

    /**
     * Fetch commits from GitHub via MCP
     */
    private suspend fun fetchGithubCommits(
        githubClient: MCPClientWrapper,
        owner: String,
        repo: String,
        maxResults: Int
    ): List<GitHubCommitInfo> {
        val result = githubClient.callTool(
            name = "list_commits",
            arguments = buildJsonObject {
                put("owner", owner)
                put("repo", repo)
                put("per_page", maxResults)
            }
        )

        if (!result.success) {
            logger.warn("Failed to fetch commits: ${result.error}")
            return emptyList()
        }

        val content = result.content.firstOrNull()?.text ?: return emptyList()
        return try {
            val json = Json.parseToJsonElement(content)
            val commitsArray = when {
                json is JsonArray -> json
                json is JsonObject && json.containsKey("commits") -> json["commits"]?.jsonArray
                else -> null
            } ?: return emptyList()

            commitsArray.take(maxResults).mapNotNull { commitJson ->
                try {
                    val commit = commitJson.jsonObject
                    val commitData = commit["commit"]?.jsonObject
                    GitHubCommitInfo(
                        sha = commit["sha"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                        message = commitData?.get("message")?.jsonPrimitive?.content?.take(100) ?: "No message",
                        author = commitData?.get("author")?.jsonObject?.get("name")?.jsonPrimitive?.content
                            ?: commit["author"]?.jsonObject?.get("login")?.jsonPrimitive?.content ?: "Unknown",
                        date = commitData?.get("author")?.jsonObject?.get("date")?.jsonPrimitive?.content ?: "",
                        url = commit["html_url"]?.jsonPrimitive?.content
                    )
                } catch (e: Exception) {
                    logger.debug("Failed to parse commit: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to parse commits response", e)
            emptyList()
        }
    }

    /**
     * Fetch issues from GitHub via MCP
     */
    private suspend fun fetchGithubIssues(
        githubClient: MCPClientWrapper,
        owner: String,
        repo: String,
        maxResults: Int
    ): List<GitHubIssueInfo> {
        val result = githubClient.callTool(
            name = "list_issues",
            arguments = buildJsonObject {
                put("owner", owner)
                put("repo", repo)
                put("state", "open")
                put("per_page", maxResults)
            }
        )

        if (!result.success) {
            logger.warn("Failed to fetch issues: ${result.error}")
            return emptyList()
        }

        val content = result.content.firstOrNull()?.text ?: return emptyList()
        return try {
            val json = Json.parseToJsonElement(content)
            val issuesArray = when {
                json is JsonArray -> json
                json is JsonObject && json.containsKey("issues") -> json["issues"]?.jsonArray
                else -> null
            } ?: return emptyList()

            issuesArray.take(maxResults).mapNotNull { issueJson ->
                try {
                    val issue = issueJson.jsonObject
                    // Skip pull requests (they appear in issues list)
                    if (issue.containsKey("pull_request")) return@mapNotNull null

                    GitHubIssueInfo(
                        number = issue["number"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null,
                        title = issue["title"]?.jsonPrimitive?.content ?: "Untitled",
                        state = issue["state"]?.jsonPrimitive?.content ?: "open",
                        author = issue["user"]?.jsonObject?.get("login")?.jsonPrimitive?.content ?: "Unknown",
                        labels = issue["labels"]?.jsonArray?.mapNotNull {
                            it.jsonObject["name"]?.jsonPrimitive?.content
                        } ?: emptyList(),
                        createdAt = issue["created_at"]?.jsonPrimitive?.content ?: "",
                        updatedAt = issue["updated_at"]?.jsonPrimitive?.contentOrNull,
                        body = issue["body"]?.jsonPrimitive?.contentOrNull?.take(200),
                        url = issue["html_url"]?.jsonPrimitive?.content
                    )
                } catch (e: Exception) {
                    logger.debug("Failed to parse issue: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to parse issues response", e)
            emptyList()
        }
    }

    /**
     * Fetch pull requests from GitHub via MCP
     */
    private suspend fun fetchGithubPullRequests(
        githubClient: MCPClientWrapper,
        owner: String,
        repo: String,
        maxResults: Int
    ): List<GitHubPRInfo> {
        val result = githubClient.callTool(
            name = "list_pull_requests",
            arguments = buildJsonObject {
                put("owner", owner)
                put("repo", repo)
                put("state", "open")
                put("per_page", maxResults)
            }
        )

        if (!result.success) {
            logger.warn("Failed to fetch pull requests: ${result.error}")
            return emptyList()
        }

        val content = result.content.firstOrNull()?.text ?: return emptyList()
        return try {
            val json = Json.parseToJsonElement(content)
            val prsArray = when {
                json is JsonArray -> json
                json is JsonObject && json.containsKey("pull_requests") -> json["pull_requests"]?.jsonArray
                else -> null
            } ?: return emptyList()

            prsArray.take(maxResults).mapNotNull { prJson ->
                try {
                    val pr = prJson.jsonObject
                    GitHubPRInfo(
                        number = pr["number"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null,
                        title = pr["title"]?.jsonPrimitive?.content ?: "Untitled",
                        state = pr["state"]?.jsonPrimitive?.content ?: "open",
                        author = pr["user"]?.jsonObject?.get("login")?.jsonPrimitive?.content ?: "Unknown",
                        sourceBranch = pr["head"]?.jsonObject?.get("ref")?.jsonPrimitive?.content ?: "unknown",
                        targetBranch = pr["base"]?.jsonObject?.get("ref")?.jsonPrimitive?.content ?: "main",
                        labels = pr["labels"]?.jsonArray?.mapNotNull {
                            it.jsonObject["name"]?.jsonPrimitive?.content
                        } ?: emptyList(),
                        createdAt = pr["created_at"]?.jsonPrimitive?.content ?: "",
                        updatedAt = pr["updated_at"]?.jsonPrimitive?.contentOrNull,
                        isDraft = pr["draft"]?.jsonPrimitive?.booleanOrNull ?: false,
                        url = pr["html_url"]?.jsonPrimitive?.content
                    )
                } catch (e: Exception) {
                    logger.debug("Failed to parse pull request: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to parse pull requests response", e)
            emptyList()
        }
    }

    /**
     * Format GitHub context for prompt
     */
    private fun formatGithubContext(
        branches: List<GitHubBranchInfo>,
        commits: List<GitHubCommitInfo>,
        issues: List<GitHubIssueInfo>,
        pullRequests: List<GitHubPRInfo>,
        repoInfo: GitHubRepoInfo
    ): String {
        return buildString {
            appendLine("=== GITHUB REPOSITORY CONTEXT ===")
            appendLine("Repository: ${repoInfo.fullName}")
            appendLine("URL: ${repoInfo.url}")
            appendLine()

            if (branches.isNotEmpty()) {
                appendLine("## Branches (${branches.size})")
                branches.forEach { branch ->
                    val defaultMarker = if (branch.isDefault) " [DEFAULT]" else ""
                    val protectedMarker = if (branch.isProtected) " [PROTECTED]" else ""
                    appendLine("- ${branch.name}$defaultMarker$protectedMarker")
                    appendLine("  SHA: ${branch.sha.take(7)}")
                }
                appendLine()
            }

            if (commits.isNotEmpty()) {
                appendLine("## Recent Commits (${commits.size})")
                commits.forEach { commit ->
                    appendLine("- [${commit.sha.take(7)}] ${commit.message}")
                    appendLine("  Author: ${commit.author} | Date: ${commit.date}")
                }
                appendLine()
            }

            if (issues.isNotEmpty()) {
                appendLine("## Open Issues (${issues.size})")
                issues.forEach { issue ->
                    appendLine("- #${issue.number}: ${issue.title}")
                    appendLine("  State: ${issue.state} | Author: ${issue.author}")
                    if (issue.labels.isNotEmpty()) {
                        appendLine("  Labels: ${issue.labels.joinToString(", ")}")
                    }
                }
                appendLine()
            }

            if (pullRequests.isNotEmpty()) {
                appendLine("## Open Pull Requests (${pullRequests.size})")
                pullRequests.forEach { pr ->
                    val draftMarker = if (pr.isDraft) " [DRAFT]" else ""
                    appendLine("- #${pr.number}: ${pr.title}$draftMarker")
                    appendLine("  ${pr.sourceBranch} → ${pr.targetBranch} | Author: ${pr.author}")
                    if (pr.labels.isNotEmpty()) {
                        appendLine("  Labels: ${pr.labels.joinToString(", ")}")
                    }
                }
                appendLine()
            }
        }
    }

    /**
     * Check if GitHub MCP server is connected
     */
    fun isGithubConnected(): Boolean {
        return mcpServerManager.getClient(config.githubMcpServerId) != null
    }

    /**
     * Build GitHub-specific prompt
     */
    private fun buildGithubPrompt(query: String, context: TechSupportContext): String {
        return buildString {
            appendLine("# User Query")
            appendLine(query)
            appendLine()

            appendLine("# Query Type: GITHUB_INFO")
            appendLine()

            // Add GitHub context
            context.githubContext?.let {
                appendLine(it.formattedContext)
                appendLine()
            }

            // Add RAG context (if relevant documentation exists)
            context.ragContext?.let {
                appendLine(it.formattedContext)
                appendLine()
            }

            appendLine("# Instructions")
            appendLine("""
You are a helpful assistant answering questions about a GitHub repository.
Based on the repository information above:

1. Answer the user's question accurately using the provided data
2. If asking about branches:
   - List available branches
   - Highlight the default branch
   - Mention protected branches if any
3. If asking about commits:
   - Summarize recent changes
   - Mention key contributors
4. If asking about issues or pull requests:
   - Summarize open items
   - Highlight important labels
5. Provide URLs where helpful so user can click through

Respond in the same language as the user's query.
            """.trimIndent())
        }
    }

    // ==================== Task Workflow Integration ====================

    /**
     * Process a workflow request (start/complete task)
     */
    private suspend fun processWorkflowRequest(request: TechSupportRequest, startTime: Long): TechSupportResponse {
        val workflowRequest = TaskWorkflowRequest(
            query = request.query,
            githubOwner = request.githubOwner,
            githubRepo = request.githubRepo,
            trelloBoardId = request.trelloBoardId
        )

        val result = taskWorkflowService.processWorkflow(workflowRequest)

        // Build suggested actions from workflow result
        val suggestedActions = buildWorkflowSuggestedActions(result)

        return TechSupportResponse(
            answer = result.message,
            sessionId = request.sessionId ?: "workflow-${System.currentTimeMillis()}",
            queryType = QueryType.TASK_WORKFLOW,
            sourcesUsed = SourcesUsed(
                ragSourceCount = 0,
                trelloTicketCount = if (result.trelloResult != null) 1 else 0,
                githubItemCount = if (result.githubResult != null) 1 else 0,
                ragSources = emptyList(),
                trelloSources = result.trelloResult?.let { listOf(it.cardName) } ?: emptyList(),
                githubSources = result.githubResult?.branchName?.let { listOf(it) } ?: emptyList()
            ),
            suggestedActions = suggestedActions,
            relatedTickets = emptyList(),
            processingTimeMs = System.currentTimeMillis() - startTime
        )
    }

    /**
     * Build suggested actions from workflow result
     */
    private fun buildWorkflowSuggestedActions(result: TaskWorkflowResult): List<SuggestedActionWrapper> {
        val actions = mutableListOf<SuggestedActionWrapper>()

        // If successful and has Trello result, suggest viewing the ticket
        result.trelloResult?.takeIf { it.success }?.let { trello ->
            actions.add(
                SuggestedActionWrapper(
                    actionType = "VIEW_TICKET",
                    viewTicket = ViewTicketAction(
                        cardId = trello.cardId,
                        cardName = trello.cardName,
                        reason = "Задача ${result.taskId} перемещена в ${trello.toList}",
                        url = trello.cardUrl
                    )
                )
            )
        }

        // If successful and has GitHub result with PR, suggest viewing PR
        result.githubResult?.takeIf { it.prCreated }?.let { github ->
            actions.add(
                SuggestedActionWrapper(
                    actionType = "VIEW_PR",
                    viewPullRequest = ViewPullRequestAction(
                        number = github.prNumber ?: 0,
                        title = "PR для ${result.taskId}",
                        state = "open",
                        sourceBranch = github.branchName ?: "",
                        targetBranch = "main",
                        url = github.prUrl
                    )
                )
            )
        }

        // If successful and has GitHub result with branch, suggest viewing branch
        result.githubResult?.takeIf { it.branchCreated || it.branchAlreadyExists }?.let { github ->
            if (!github.prCreated) {  // Only if no PR was created
                actions.add(
                    SuggestedActionWrapper(
                        actionType = "VIEW_BRANCH",
                        viewBranch = ViewBranchAction(
                            branchName = github.branchName ?: "",
                            sha = "",
                            isDefault = false
                        )
                    )
                )
            }
        }

        return actions
    }
}
