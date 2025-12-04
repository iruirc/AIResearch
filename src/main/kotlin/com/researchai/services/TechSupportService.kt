package com.researchai.services

import com.researchai.data.mcp.MCPClientWrapper
import com.researchai.data.mcp.MCPServerManager
import com.researchai.domain.models.*
import com.researchai.domain.models.techsupport.*
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

            TechSupportResponse(
                answer = aiResponse,
                sessionId = request.sessionId ?: "tech-support-${System.currentTimeMillis()}",
                queryType = queryType,
                sourcesUsed = SourcesUsed(
                    ragSourceCount = context.ragContext?.sourceCount ?: 0,
                    trelloTicketCount = context.ticketContext?.relatedTickets?.size ?: 0,
                    ragSources = context.ragContext?.sources ?: emptyList(),
                    trelloSources = context.ticketContext?.relatedTickets?.map { it.cardName } ?: emptyList()
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
- GENERAL: General question that doesn't fit above

Query: "$query"

Respond with ONLY the category name (e.g., "BUG_REPORT"), nothing else.
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
     * Gather context from RAG and Trello in parallel
     */
    private suspend fun gatherContext(
        request: TechSupportRequest,
        queryType: QueryType
    ): TechSupportContext = coroutineScope {
        val startTime = System.currentTimeMillis()

        val ragDeferred = if (request.includeRag) {
            async { fetchRagContext(request.query, request.maxRagResults) }
        } else null

        val trelloDeferred = if (request.includeTrello) {
            async { fetchTrelloContext(request.query, request.trelloBoardId, request.maxTrelloResults) }
        } else null

        TechSupportContext(
            ragContext = ragDeferred?.await(),
            ticketContext = trelloDeferred?.await(),
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

            if (context.ragContext == null && context.ticketContext == null) {
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
                    reason = "Похожая проблема"
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
}
