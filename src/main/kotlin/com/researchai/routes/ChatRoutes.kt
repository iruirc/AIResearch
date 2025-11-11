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
            try {
                val request = call.receive<ChatRequest>()

                if (request.message.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Message cannot be empty"))
                    return@post
                }

                // Определяем провайдер по модели
                val providerId = when {
                    request.model?.startsWith("gpt-") == true -> com.researchai.domain.models.ProviderType.OPENAI
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
                    call.respond(ChatResponse(
                        response = messageResult.response,
                        sessionId = messageResult.sessionId
                    ))
                }.onFailure { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to (error.message ?: "Failed to process request"))
                    )
                }

            } catch (e: Exception) {
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
                    MessageItem(
                        role = message.role.name.lowercase(),
                        content = message.content
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

    // Получить список доступных моделей
    route("/models") {
        get {
            try {
                // Получаем модели Claude
                val claudeModels = AvailableModels.models

                // Получаем модели OpenAI и фильтруем только gpt-5 основные версии
                val openAIModelsResult = appModule.getModelsUseCase(com.researchai.domain.models.ProviderType.OPENAI)
                val allowedGPT5Models = setOf("gpt-5-nano", "gpt-5-mini", "gpt-5", "gpt-5-pro")
                val openAIModels = openAIModelsResult.getOrNull()
                    ?.filter { model -> model.id in allowedGPT5Models }
                    ?.map { model ->
                        ClaudeModel(
                            id = model.id,
                            displayName = model.name,
                            createdAt = "2025-01-01T00:00:00Z"
                        )
                    } ?: emptyList()

                // Объединяем списки
                val allModels = claudeModels + openAIModels

                call.respond(ModelsListResponse(models = allModels))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to get models: ${e.message}")
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
