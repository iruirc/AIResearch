package com.example.routes

import com.example.models.*
import com.example.services.ChatSessionManager
import com.example.services.ClaudeService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.chatRoutes(claudeService: ClaudeService, sessionManager: ChatSessionManager) {
    route("/chat") {
        post {
            try {
                val request = call.receive<ChatRequest>()

                if (request.message.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Message cannot be empty"))
                    return@post
                }

                // Получаем или создаем сессию
                val (sessionId, session) = sessionManager.getOrCreateSession(request.sessionId)

                // Получаем историю сообщений из сессии
                val messageHistory = session.messages

                // Отправляем сообщение в Claude API с историей
                val claudeResponse = claudeService.sendMessage(
                    userMessage = request.message,
                    format = request.format,
                    messageHistory = messageHistory
                )

                // Сохраняем сообщение пользователя в историю (без форматирования)
                session.addMessage(MessageRole.USER, request.message)

                // Сохраняем ответ ассистента в историю
                session.addMessage(MessageRole.ASSISTANT, claudeResponse)

                // Возвращаем ответ вместе с sessionId
                call.respond(ChatResponse(response = claudeResponse, sessionId = sessionId))

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
                        lastAccessedAt = info.lastAccessedAt
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

    get("/health") {
        call.respond(mapOf("status" to "ok"))
    }
}
