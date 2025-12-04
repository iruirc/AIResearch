package com.researchai.domain.usecase

import com.researchai.data.provider.ollama.OllamaConnectionManager
import com.researchai.domain.mcp.MCPOrchestrationService
import com.researchai.domain.models.*
import com.researchai.domain.models.RAGDebugInfo
import com.researchai.domain.provider.AIProviderFactory
import com.researchai.domain.repository.ChatSession
import com.researchai.domain.repository.ConfigRepository
import com.researchai.domain.repository.SessionRepository
import com.researchai.services.AssistantManager
import com.researchai.data.provider.claude.ClaudeMapper
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory

/**
 * Use case для отправки сообщения AI-провайдеру
 */
class SendMessageUseCase(
    private val providerFactory: AIProviderFactory,
    private val sessionRepository: SessionRepository,
    private val configRepository: ConfigRepository,
    private val assistantManager: AssistantManager,
    private val mcpOrchestrationService: MCPOrchestrationService? = null,
    private val ollamaConnectionManager: OllamaConnectionManager? = null,
    private val ragManager: com.researchai.services.RAGManager? = null,
    private val ragPreferencesStorage: com.researchai.persistence.RAGPreferencesStorage? = null,
    private val citationExtractor: com.researchai.services.CitationExtractor? = null
) {
    private val logger = LoggerFactory.getLogger(SendMessageUseCase::class.java)
    private val claudeMapper = ClaudeMapper()

    companion object {
        private const val MAX_TOOL_ITERATIONS = 5 // Prevent infinite loops

        // Tools that return too much data and should be filtered for Tech Support sessions
        private val TECH_SUPPORT_BLOCKED_TOOLS = setOf(
            "get_my_cards",      // Returns cards from ALL boards - huge response
            "list_boards",       // Lists all boards - not needed for support
            "list_workspaces"    // Lists all workspaces - not needed for support
        )
    }

    suspend operator fun invoke(
        message: String,
        sessionId: String? = null,
        providerId: ProviderType = ProviderType.CLAUDE,
        model: String? = null,
        parameters: RequestParameters = RequestParameters(),
        skipUserMessage: Boolean = false,
        useRerankingOverride: Boolean? = null, // null = use global settings, true/false = override
        isTechSupport: Boolean = false, // Filter MCP tools for Tech Support sessions
        techSupportGithubOwner: String? = null, // Default GitHub owner for Tech Support
        techSupportGithubRepo: String? = null // Default GitHub repo for Tech Support
    ): Result<MessageResult> {
        return try {
            logger.info("SendMessageUseCase: Processing message for provider $providerId")

            // 1. Получаем или создаем сессию
            val session = sessionId?.let {
                logger.info("Getting existing session: $it")
                sessionRepository.getSession(it).getOrThrow()
            } ?: run {
                logger.info("Creating new session for provider: $providerId")
                sessionRepository.createSession(providerId).getOrThrow()
            }

            logger.info("Using session: ${session.id}")

            // 2. Получаем провайдера
            val provider = if (providerId == ProviderType.OLLAMA) {
                // Для Ollama получаем провайдер через OllamaConnectionManager
                ollamaConnectionManager?.getActiveProvider()
                    ?: return Result.failure(
                        AIError.ConfigurationException("No active Ollama connection. Please create and activate an Ollama connection first.")
                    )
            } else {
                // Для других провайдеров используем configRepository + providerFactory
                val config = configRepository.getProviderConfig(providerId)
                    .getOrNull() ?: return Result.failure(
                        AIError.ConfigurationException("Provider $providerId not configured")
                    )

                logger.info("Provider config loaded for: $providerId")
                providerFactory.create(providerId, config)
            }

            logger.info("Provider created for: $providerId")

            // 4. Добавляем пользовательское сообщение в историю (если не пропущено)
            if (!skipUserMessage) {
                val userMessage = Message(
                    role = MessageRole.USER,
                    content = MessageContent.Text(message)
                )
                sessionRepository.addMessage(session.id, userMessage).getOrThrow()
                logger.info("User message added to session")
            } else {
                logger.info("Skipping user message - will use system prompt only")
            }

            // 5. Получаем обновленную историю
            val messages = sessionRepository.getMessages(session.id).getOrThrow()

            logger.info("Message history retrieved: ${messages.size} messages")

            // 6. Получаем системный промпт от ассистента если есть
            val systemPrompt = session.assistantId?.let { assistantId ->
                logger.info("Retrieving systemPrompt for assistant: $assistantId")
                val assistant = assistantManager.getAssistant(assistantId)
                if (assistant != null) {
                    logger.info("Assistant found: ${assistant.name}, systemPrompt length: ${assistant.systemPrompt.length}")
                    assistant.systemPrompt
                } else {
                    logger.warn("Assistant not found: $assistantId")
                    null
                }
            }

            // 6.5. Получаем RAG контекст если доступен
            val ragPrefs = ragPreferencesStorage?.load()
            var ragDebugInfo: RAGDebugInfo? = null

            val ragContext = ragManager?.let { manager ->
                try {
                    // Проверяем есть ли включенные документы
                    val documents = manager.getAllDocuments()
                    val enabledDocs = documents.filter { it.enabled }

                    if (enabledDocs.isEmpty()) {
                        logger.info("RAG: No enabled documents found")
                        null
                    } else {
                        logger.info("RAG: Searching context from ${enabledDocs.size} enabled documents")

                        // Build reranker config from preferences
                        // Use override if provided, otherwise use global settings
                        val useReranking = useRerankingOverride ?: (ragPrefs?.enableReranking == true)
                        logger.info("RAG: useReranking=${useReranking} (override=${useRerankingOverride}, global=${ragPrefs?.enableReranking})")
                        val rerankerConfig = if (useReranking && ragPrefs != null) {
                            com.researchai.domain.models.RerankerConfig(
                                strategy = ragPrefs.strategy,
                                secondaryThreshold = ragPrefs.scoreThreshold,
                                stdDevMultiplier = ragPrefs.stdDevMultiplier,
                                minResultsToKeep = ragPrefs.minResultsToKeep,
                                crossEncoderModel = ragPrefs.crossEncoderModel ?: "llama3.2:latest",
                                crossEncoderMinScore = ragPrefs.crossEncoderMinScore
                            )
                        } else {
                            com.researchai.domain.models.RerankerConfig()
                        }

                        val topK = ragPrefs?.searchTopK ?: 5
                        val minScore = ragPrefs?.searchMinScore ?: 0.7f

                        // Always collect debug info for RAG context (needed for tests and analytics)
                        val result = manager.getContextForChatWithDebug(
                            query = message,
                            topK = topK,
                            minScore = minScore,
                            useReranking = useReranking,
                            rerankerConfig = rerankerConfig
                        )
                        ragDebugInfo = result.debugInfo
                        if (result.context.isNotBlank()) {
                            logger.info("RAG: Found relevant context (${result.context.length} characters)")
                            result.context
                        } else {
                            logger.info("RAG: No relevant context found")
                            null
                        }
                    }
                } catch (e: Exception) {
                    logger.error("RAG: Error retrieving context: ${e.message}", e)
                    null
                }
            }

            // 6.6. Объединяем системный промпт и RAG контекст
            val finalSystemPrompt = buildString {
                if (ragContext != null) {
                    append(ragContext)
                    append("\n\n")
                }
                // Inject GitHub defaults for Tech Support sessions
                if (isTechSupport && (techSupportGithubOwner != null || techSupportGithubRepo != null)) {
                    append("## GitHub Configuration\n")
                    append("For GitHub operations, use the following default repository:\n")
                    if (techSupportGithubOwner != null) {
                        append("- Owner: $techSupportGithubOwner\n")
                    }
                    if (techSupportGithubRepo != null) {
                        append("- Repository: $techSupportGithubRepo\n")
                    }
                    append("Use these defaults when the user asks about branches, commits, issues, or pull requests without specifying a repository.\n\n")
                }
                if (systemPrompt != null) {
                    append(systemPrompt)
                }
            }.takeIf { it.isNotBlank() }

            // 7. Определяем модель
            val selectedModel: String = when {
                // Для Ollama модель должна быть явно указана
                providerId == ProviderType.OLLAMA -> {
                    model ?: throw AIError.ConfigurationException("Model must be specified for Ollama provider")
                }
                // Для других провайдеров, если модель не указана, используем defaultModel из конфига
                model != null -> model
                else -> {
                    val config = configRepository.getProviderConfig(providerId).getOrThrow()
                    when (config) {
                        is ProviderConfig.ClaudeConfig -> config.defaultModel
                        is ProviderConfig.OpenAIConfig -> config.defaultModel
                        is ProviderConfig.HuggingFaceConfig -> config.defaultModel
                        is ProviderConfig.OllamaConfig -> "llama3.2"  // Не должно случиться
                        else -> throw AIError.ConfigurationException("Unknown provider config type")
                    }
                }
            }

            // 8. Получаем доступные MCP tools если включена оркестрация
            // Поддержка для Claude и OpenAI
            val mcpTools = if (mcpOrchestrationService != null &&
                              (providerId == ProviderType.CLAUDE || providerId == ProviderType.OPENAI)) {
                var tools = mcpOrchestrationService.getAvailableTools()

                // Filter out problematic tools for Tech Support sessions
                // These tools return data from ALL boards which exceeds context limits
                if (isTechSupport && tools.isNotEmpty()) {
                    val originalSize = tools.size
                    tools = tools.filter { tool -> tool.name !in TECH_SUPPORT_BLOCKED_TOOLS }
                    val filteredCount = originalSize - tools.size
                    if (filteredCount > 0) {
                        logger.info("Tech Support: Filtered out $filteredCount tools (blocked: ${TECH_SUPPORT_BLOCKED_TOOLS.joinToString()})")
                    }
                }

                if (tools.isNotEmpty()) {
                    logger.info("MCP orchestration enabled: ${tools.size} tools available for $providerId")
                    mcpOrchestrationService.convertToClaudeTools(tools)
                } else {
                    logger.info("No MCP tools available")
                    emptyList()
                }
            } else {
                logger.info("MCP tools not enabled for provider: $providerId")
                emptyList()
            }

            // 9. Создаем запрос
            val request = AIRequest(
                messages = messages,
                model = selectedModel,
                parameters = parameters,
                systemPrompt = finalSystemPrompt,
                sessionId = session.id,
                tools = mcpTools
            )

            logger.info("Sending request to provider: $providerId, model: $selectedModel, tools: ${mcpTools.size}")

            // 10. Отправляем запрос и обрабатываем возможные tool calls
            val (finalResponse, toolResults) = handleToolUseLoop(provider, request, mcpTools)

            logger.info("Response received from provider: ${finalResponse.usage.totalTokens} tokens")

            // 10.5. Format response with citations if RAG was used
            val formattedResponse = if (citationExtractor != null && ragDebugInfo != null) {
                citationExtractor.formatWithSources(
                    response = finalResponse.content,
                    sourceMapping = ragDebugInfo.sourceMapping,
                    ragContextWasProvided = ragContext != null
                )
            } else {
                finalResponse.content
            }

            // 11. Сохраняем ответ в историю (включая информацию о tool uses если были)
            val responseContent = if (toolResults.isNotEmpty()) {
                val toolInfo = toolResults.joinToString("\n") { "[Tool: ${it.toolName}]" }
                "${formattedResponse}\n\n$toolInfo"
            } else {
                formattedResponse
            }

            val assistantMessage = Message(
                role = MessageRole.ASSISTANT,
                content = MessageContent.Text(responseContent)
            )
            sessionRepository.addMessage(session.id, assistantMessage).getOrThrow()

            // 12. Обновляем lastAccessedAt
            sessionRepository.updateSession(
                session.copy(lastAccessedAt = System.currentTimeMillis())
            ).getOrThrow()

            logger.info("Message sent successfully")

            Result.success(
                MessageResult(
                    response = formattedResponse,
                    sessionId = session.id,
                    usage = finalResponse.usage,
                    model = finalResponse.model,
                    providerId = providerId,
                    estimatedInputTokens = finalResponse.estimatedInputTokens,
                    estimatedOutputTokens = finalResponse.estimatedOutputTokens,
                    ragDebugInfo = ragDebugInfo
                )
            )
        } catch (e: Exception) {
            logger.error("Error in SendMessageUseCase: ${e.message}", e)
            Result.failure(AIError.fromException(e as? Exception ?: Exception(e.message)))
        }
    }

    /**
     * Handle the tool use loop: send request, check for tool uses, execute tools, send results back
     */
    private suspend fun handleToolUseLoop(
        provider: com.researchai.domain.provider.AIProvider,
        initialRequest: AIRequest,
        mcpTools: List<com.researchai.domain.mcp.ClaudeTool>
    ): Pair<AIResponse, List<ToolExecutionResult>> {
        var currentRequest = initialRequest
        val toolExecutionResults = mutableListOf<ToolExecutionResult>()
        var iteration = 0

        while (iteration < MAX_TOOL_ITERATIONS) {
            iteration++

            // Send request to AI provider
            val response = provider.sendMessage(currentRequest).getOrThrow()

            // Check if the response contains tool uses
            if (response.finishReason != FinishReason.TOOL_USE || mcpOrchestrationService == null || response.toolUses.isEmpty()) {
                // No tool use, return the final response
                return Pair(response, toolExecutionResults)
            }

            logger.info("Detected ${response.toolUses.size} tool use(s) in AI response")

            // Execute tools in parallel for better performance (3-5x speedup)
            val toolResults = coroutineScope {
                response.toolUses.map { toolUse ->
                    async {
                        logger.info("Executing tool: ${toolUse.name}")

                        val result = mcpOrchestrationService.executeToolCall(
                            toolName = toolUse.name,
                            arguments = toolUse.input
                        )

                        val resultText = if (result.success) {
                            result.content.joinToString("\n") { it.text ?: "" }
                        } else {
                            "Error: ${result.error}"
                        }

                        // Add to execution results
                        toolExecutionResults.add(
                            ToolExecutionResult(
                                toolName = toolUse.name,
                                success = result.success,
                                result = resultText
                            )
                        )

                        logger.info("Tool ${toolUse.name} executed: success=${result.success}")

                        Pair(toolUse.id, resultText)
                    }
                }.awaitAll()
            }

            // Prepare next request with tool results
            // Add assistant message with tool uses and user message with tool results
            val updatedMessages = currentRequest.messages.toMutableList()

            // Add assistant message with tool_use blocks (proper format for Claude/OpenAI)
            val toolUseBlocks = response.toolUses.map { toolUse ->
                ContentBlock.ToolUseBlock(
                    id = toolUse.id,
                    name = toolUse.name,
                    input = toolUse.input
                )
            }
            updatedMessages.add(
                Message(
                    role = MessageRole.ASSISTANT,
                    content = MessageContent.Structured(toolUseBlocks)
                )
            )

            // Add user message with tool_result blocks (proper format)
            val toolResultBlocks = toolResults.map { (id, result) ->
                ContentBlock.ToolResultBlock(
                    toolUseId = id,
                    content = result,
                    isError = false
                )
            }
            updatedMessages.add(
                Message(
                    role = MessageRole.USER,
                    content = MessageContent.Structured(toolResultBlocks)
                )
            )

            currentRequest = currentRequest.copy(messages = updatedMessages)
        }

        // Max iterations reached
        logger.warn("Max tool use iterations ($MAX_TOOL_ITERATIONS) reached")
        throw AIError.ConfigurationException("Tool use loop exceeded maximum iterations")
    }
}

/**
 * Result of a tool execution
 */
data class ToolExecutionResult(
    val toolName: String,
    val success: Boolean,
    val result: String
)

/**
 * Результат отправки сообщения
 */
data class MessageResult(
    val response: String,
    val sessionId: String,
    val usage: TokenUsage, // Токены от API провайдера
    val model: String,
    val providerId: ProviderType,
    val estimatedInputTokens: Int = 0, // Локально подсчитанные входные токены
    val estimatedOutputTokens: Int = 0, // Локально подсчитанные выходные токены
    val ragDebugInfo: RAGDebugInfo? = null // Debug info о RAG контексте (если debugMode включен)
)
