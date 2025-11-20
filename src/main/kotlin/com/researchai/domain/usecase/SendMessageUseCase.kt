package com.researchai.domain.usecase

import com.researchai.domain.mcp.MCPOrchestrationService
import com.researchai.domain.models.*
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
    private val mcpOrchestrationService: MCPOrchestrationService? = null
) {
    private val logger = LoggerFactory.getLogger(SendMessageUseCase::class.java)
    private val claudeMapper = ClaudeMapper()

    companion object {
        private const val MAX_TOOL_ITERATIONS = 5 // Prevent infinite loops
    }

    suspend operator fun invoke(
        message: String,
        sessionId: String? = null,
        providerId: ProviderType = ProviderType.CLAUDE,
        model: String? = null,
        parameters: RequestParameters = RequestParameters()
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

            // 2. Получаем конфигурацию провайдера
            val config = configRepository.getProviderConfig(providerId)
                .getOrNull() ?: return Result.failure(
                    AIError.ConfigurationException("Provider $providerId not configured")
                )

            logger.info("Provider config loaded for: $providerId")

            // 3. Создаем провайдера
            val provider = providerFactory.create(providerId, config)

            // 4. Добавляем пользовательское сообщение в историю
            val userMessage = Message(
                role = MessageRole.USER,
                content = MessageContent.Text(message)
            )
            sessionRepository.addMessage(session.id, userMessage).getOrThrow()

            logger.info("User message added to session")

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

            // 7. Определяем модель
            val selectedModel = model ?: when (config) {
                is ProviderConfig.ClaudeConfig -> config.defaultModel
                is ProviderConfig.OpenAIConfig -> config.defaultModel
                is ProviderConfig.HuggingFaceConfig -> config.defaultModel
                is ProviderConfig.GeminiConfig -> config.defaultModel
                is ProviderConfig.CustomConfig -> "default"
            }

            // 8. Получаем доступные MCP tools если включена оркестрация
            // Поддержка для Claude и OpenAI
            val mcpTools = if (mcpOrchestrationService != null &&
                              (providerId == ProviderType.CLAUDE || providerId == ProviderType.OPENAI)) {
                val tools = mcpOrchestrationService.getAvailableTools()
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
                systemPrompt = systemPrompt,
                sessionId = session.id,
                tools = mcpTools
            )

            logger.info("Sending request to provider: $providerId, model: $selectedModel, tools: ${mcpTools.size}")

            // 10. Отправляем запрос и обрабатываем возможные tool calls
            val (finalResponse, toolResults) = handleToolUseLoop(provider, request, mcpTools)

            logger.info("Response received from provider: ${finalResponse.usage.totalTokens} tokens")

            // 11. Сохраняем ответ в историю (включая информацию о tool uses если были)
            val responseContent = if (toolResults.isNotEmpty()) {
                val toolInfo = toolResults.joinToString("\n") { "[Tool: ${it.toolName}]" }
                "${finalResponse.content}\n\n$toolInfo"
            } else {
                finalResponse.content
            }

            val assistantMessage = Message(
                role = MessageRole.ASSISTANT,
                content = MessageContent.Text(responseContent)
            )
            sessionRepository.addMessage(session.id, assistantMessage).getOrThrow()

            // 11. Обновляем lastAccessedAt
            sessionRepository.updateSession(
                session.copy(lastAccessedAt = System.currentTimeMillis())
            ).getOrThrow()

            logger.info("Message sent successfully")

            Result.success(
                MessageResult(
                    response = finalResponse.content,
                    sessionId = session.id,
                    usage = finalResponse.usage,
                    model = finalResponse.model,
                    providerId = providerId,
                    estimatedInputTokens = finalResponse.estimatedInputTokens,
                    estimatedOutputTokens = finalResponse.estimatedOutputTokens
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
    val estimatedOutputTokens: Int = 0 // Локально подсчитанные выходные токены
)
