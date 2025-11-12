package com.researchai.routes

import com.researchai.config.ClaudeConfig
import com.researchai.di.AppModule
import com.researchai.models.*
import com.researchai.services.AgentManager
import com.researchai.services.ChatSessionManager
import com.researchai.services.ClaudeService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.chatRoutes(
    claudeService: ClaudeService,
    sessionManager: ChatSessionManager,
    agentManager: AgentManager,
    claudeConfig: ClaudeConfig,
    appModule: AppModule
) {
    route("/chat") {
        post {
            val startTime = System.currentTimeMillis()

            try {
                val request = call.receive<ChatRequest>()

                if (request.message.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Message cannot be empty"))
                    return@post
                }

                // Определяем провайдер по модели
                val providerId = when {
                    request.model?.startsWith("gpt-") == true -> com.researchai.domain.models.ProviderType.OPENAI
                    request.model?.contains("deepseek", ignoreCase = true) == true -> com.researchai.domain.models.ProviderType.HUGGINGFACE
                    request.model?.contains("/") == true -> com.researchai.domain.models.ProviderType.HUGGINGFACE
                    else -> com.researchai.domain.models.ProviderType.CLAUDE
                }

                // Получаем или создаем сессию
                val (sessionId, session) = sessionManager.getOrCreateSession(request.sessionId)

                // Получаем system prompt если сессия связана с агентом
                val systemPrompt = session.agentId?.let { agentId ->
                    agentManager.getAgent(agentId)?.systemPrompt
                }

                // Используем новую архитектуру с SendMessageUseCase
                val parameters = com.researchai.domain.models.RequestParameters(
                    temperature = request.temperature ?: 1.0,
                    maxTokens = request.maxTokens ?: 4096,
                    responseFormat = request.format
                )

                val result = appModule.sendMessageUseCase(
                    message = request.message,
                    sessionId = sessionId,
                    providerId = providerId,
                    model = request.model,
                    parameters = parameters
                )

                result.onSuccess { messageResult ->
                    val elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0

                    // Логируем результат с временем выполнения
                    call.application.environment.log.info(
                        "Request completed in ${String.format("%.3f", elapsedTime)}s " +
                        "[sessionId: ${messageResult.sessionId}, model: ${messageResult.model}, " +
                        "tokens: ${messageResult.usage.totalTokens}]"
                    )

                    // Обновляем последнее сообщение в сессии с метаданными
                    val session = sessionManager.getSession(messageResult.sessionId)
                    session?.updateLastMessage { lastMessage ->
                        // Добавляем метаданные только если это сообщение ассистента
                        if (lastMessage.role == com.researchai.domain.models.MessageRole.ASSISTANT) {
                            lastMessage.copy(
                                metadata = com.researchai.domain.models.MessageMetadata(
                                    model = messageResult.model,
                                    tokensUsed = messageResult.usage.totalTokens,
                                    responseTime = elapsedTime,
                                    // Токены от API провайдера (реальные)
                                    inputTokens = messageResult.usage.inputTokens,
                                    outputTokens = messageResult.usage.outputTokens,
                                    totalTokens = messageResult.usage.totalTokens,
                                    // Локально подсчитанные токены (оценочные)
                                    estimatedInputTokens = messageResult.estimatedInputTokens,
                                    estimatedOutputTokens = 0, // Выходные токены нельзя оценить локально
                                    estimatedTotalTokens = messageResult.estimatedInputTokens
                                )
                            )
                        } else {
                            lastMessage
                        }
                    }

                    call.respond(ChatResponse(
                        response = messageResult.response,
                        sessionId = messageResult.sessionId,
                        tokensUsed = messageResult.usage.totalTokens,
                        tokenDetails = TokenDetails(
                            inputTokens = messageResult.usage.inputTokens,
                            outputTokens = messageResult.usage.outputTokens,
                            totalTokens = messageResult.usage.totalTokens,
                            estimatedInputTokens = messageResult.estimatedInputTokens,
                            estimatedOutputTokens = messageResult.estimatedOutputTokens,
                            estimatedTotalTokens = messageResult.estimatedInputTokens + messageResult.estimatedOutputTokens
                        )
                    ))
                }.onFailure { error ->
                    val elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0

                    call.application.environment.log.error(
                        "Request failed in ${String.format("%.3f", elapsedTime)}s: ${error.message}"
                    )

                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to (error.message ?: "Failed to process request"))
                    )
                }

            } catch (e: Exception) {
                val elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0

                call.application.environment.log.error(
                    "Request failed in ${String.format("%.3f", elapsedTime)}s: ${e.message}", e
                )

                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to process request: ${e.message}")
                )
            }
        }
    }

    // Получить список всех сессий
    route("/sessions") {
        get {
            try {
                val sessionsInfo = sessionManager.getSessionsInfo()
                val sessionList = sessionsInfo.map { (id, info) ->
                    SessionListItem(
                        id = id,
                        messageCount = info.messageCount,
                        createdAt = info.createdAt,
                        lastAccessedAt = info.lastAccessedAt,
                        agentId = info.agentId
                    )
                }.sortedByDescending { it.lastAccessedAt }

                call.respond(SessionListResponse(sessions = sessionList))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to get sessions: ${e.message}")
                )
            }
        }

        // Получить детальную информацию о сессии с историей
        get("/{sessionId}") {
            try {
                val sessionId = call.parameters["sessionId"]
                if (sessionId == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Session ID is required"))
                    return@get
                }

                val session = sessionManager.getSession(sessionId)
                if (session == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Session not found"))
                    return@get
                }

                val messages = session.messages.map { message ->
                    // Получаем текстовое содержимое сообщения
                    val contentText = when (val content = message.content) {
                        is com.researchai.domain.models.MessageContent.Text -> content.text
                        is com.researchai.domain.models.MessageContent.MultiModal -> content.text ?: ""
                    }

                    // Преобразуем метаданные если они есть
                    val metadataDTO = message.metadata?.let {
                        MessageMetadataDTO(
                            model = it.model,
                            tokensUsed = it.tokensUsed,
                            responseTime = it.responseTime
                        )
                    }

                    MessageItem(
                        role = message.role.name.lowercase(),
                        content = contentText,
                        timestamp = message.timestamp,
                        metadata = metadataDTO
                    )
                }

                call.respond(
                    SessionDetailResponse(
                        id = session.id,
                        messages = messages,
                        createdAt = session.createdAt,
                        lastAccessedAt = session.lastAccessedAt
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to get session: ${e.message}")
                )
            }
        }

        // Удалить сессию
        delete("/{sessionId}") {
            try {
                val sessionId = call.parameters["sessionId"]
                if (sessionId == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Session ID is required"))
                    return@delete
                }

                val success = sessionManager.deleteSession(sessionId)
                if (success) {
                    call.respond(StatusResponse(success = true, message = "Session deleted successfully"))
                } else {
                    call.respond(HttpStatusCode.NotFound, StatusResponse(success = false, message = "Session not found"))
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to delete session: ${e.message}")
                )
            }
        }

        // Очистить историю сообщений в сессии
        post("/{sessionId}/clear") {
            try {
                val sessionId = call.parameters["sessionId"]
                if (sessionId == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Session ID is required"))
                    return@post
                }

                val success = sessionManager.clearSession(sessionId)
                if (success) {
                    call.respond(StatusResponse(success = true, message = "Session cleared successfully"))
                } else {
                    call.respond(HttpStatusCode.NotFound, StatusResponse(success = false, message = "Session not found"))
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to clear session: ${e.message}")
                )
            }
        }
    }

    // Получить список всех доступных агентов
    route("/agents") {
        get {
            try {
                val agents = agentManager.getAllAgents()
                val agentList = agents.map { agent ->
                    AgentListItem(
                        id = agent.id,
                        name = agent.name,
                        description = agent.description
                    )
                }
                call.respond(AgentListResponse(agents = agentList))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to get agents: ${e.message}")
                )
            }
        }

        // Создать новую сессию с агентом
        post("/start") {
            try {
                val request = call.receive<CreateAgentSessionRequest>()

                val agent = agentManager.getAgent(request.agentId)
                if (agent == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Agent not found"))
                    return@post
                }

                // Создаем новую сессию с агентом
                val sessionId = sessionManager.createSession(agentId = agent.id)
                val session = sessionManager.getSession(sessionId)!!

                // Отправляем пустое сообщение с system prompt для инициализации агента
                val initialMessage = claudeService.sendMessage(
                    userMessage = "Привет",
                    format = ResponseFormat.PLAIN_TEXT,
                    messageHistory = emptyList(),
                    systemPrompt = agent.systemPrompt,
                    model = null
                )

                // Сохраняем приветственное сообщение пользователя
                session.addMessage(MessageRole.USER, "Привет")

                // Сохраняем ответ агента
                session.addMessage(MessageRole.ASSISTANT, initialMessage)

                call.respond(
                    CreateAgentSessionResponse(
                        sessionId = sessionId,
                        agentName = agent.name,
                        initialMessage = initialMessage
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to start agent session: ${e.message}")
                )
            }
        }
    }

    // Получить список провайдеров
    route("/providers") {
        get {
            try {
                val providers = listOf(
                    ProviderDTO(
                        id = "claude",
                        name = "Claude (Anthropic)",
                        defaultModel = AvailableClaudeModels.DEFAULT_MODEL
                    ),
                    ProviderDTO(
                        id = "openai",
                        name = "OpenAI",
                        defaultModel = AvailableOpenAIModels.DEFAULT_MODEL
                    ),
                    ProviderDTO(
                        id = "huggingface",
                        name = "HuggingFace",
                        defaultModel = AvailableHuggingFaceModels.DEFAULT_MODEL
                    )
                )
                call.respond(ProvidersListResponse(providers = providers))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to get providers: ${e.message}")
                )
            }
        }
    }

    // Получить список доступных моделей
    route("/models") {
        get {
            try {
                // Получаем query параметр provider (опционально)
                val providerFilter = call.request.queryParameters["provider"]

                // Получаем предустановленные модели Claude, OpenAI и HuggingFace
                val claudeModels = AvailableClaudeModels.models
                val openAIModels = AvailableOpenAIModels.models
                val huggingFaceModels = AvailableHuggingFaceModels.models

                // Фильтруем по провайдеру, если указан
                val filteredModels = when (providerFilter?.lowercase()) {
                    "claude" -> claudeModels
                    "openai" -> openAIModels
                    "huggingface" -> huggingFaceModels
                    null -> claudeModels + openAIModels + huggingFaceModels // Все модели, если фильтр не указан
                    else -> {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Unknown provider: $providerFilter"))
                        return@get
                    }
                }

                call.respond(ModelsListResponse(models = filteredModels))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to get models: ${e.message}")
                )
            }
        }

        // Получить capabilities для конкретной модели
        get("/{modelId}/capabilities") {
            try {
                val modelId = call.parameters["modelId"]
                if (modelId == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Model ID is required"))
                    return@get
                }

                // Определяем провайдер по модели
                val providerId = when {
                    modelId.startsWith("gpt-") -> com.researchai.domain.models.ProviderType.OPENAI
                    modelId.contains("deepseek", ignoreCase = true) -> com.researchai.domain.models.ProviderType.HUGGINGFACE
                    modelId.contains("/") -> com.researchai.domain.models.ProviderType.HUGGINGFACE
                    else -> com.researchai.domain.models.ProviderType.CLAUDE
                }

                // Получаем конфигурацию провайдера
                val configResult = appModule.configRepository.getProviderConfig(providerId)
                if (configResult.isFailure) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Provider not configured: $providerId"))
                    return@get
                }

                val config = configResult.getOrNull()
                if (config == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Provider not configured: $providerId"))
                    return@get
                }

                // Создаем провайдер
                val provider = appModule.providerFactory.create(providerId, config)

                // Получаем список моделей от провайдера
                val modelsResult = provider.getModels()

                modelsResult.onSuccess { models ->
                    // Ищем модель по ID
                    val model = models.find { it.id == modelId }
                    if (model == null) {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Model not found: $modelId"))
                        return@get
                    }

                    // Возвращаем capabilities
                    val capabilitiesDTO = ModelCapabilitiesDTO(
                        modelId = model.id,
                        maxTokens = model.capabilities.maxTokens,
                        contextWindow = model.capabilities.contextWindow,
                        supportsVision = model.capabilities.supportsVision,
                        supportsStreaming = model.capabilities.supportsStreaming
                    )

                    call.respond(capabilitiesDTO)
                }.onFailure { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "Failed to get model capabilities: ${error.message}")
                    )
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to get model capabilities: ${e.message}")
                )
            }
        }
    }

    // Получить текущую конфигурацию
    route("/config") {
        get {
            try {
                call.respond(ConfigResponse(
                    model = claudeConfig.model,
                    temperature = claudeConfig.temperature,
                    maxTokens = claudeConfig.maxTokens,
                    format = "PLAIN_TEXT" // Дефолтный формат
                ))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to get config: ${e.message}")
                )
            }
        }
    }

    get("/health") {
        call.respond(mapOf("status" to "ok"))
    }
}
