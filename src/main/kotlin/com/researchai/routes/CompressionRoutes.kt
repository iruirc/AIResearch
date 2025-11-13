package com.researchai.routes

import com.researchai.domain.models.CompressionConfig
import com.researchai.domain.models.CompressionStrategy
import com.researchai.domain.models.Message
import com.researchai.domain.models.ProviderType
import com.researchai.services.ChatCompressionService
import com.researchai.services.ChatSessionManager
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

/**
 * Routes для управления сжатием диалогов
 */
fun Route.compressionRoutes(
    sessionManager: ChatSessionManager,
    compressionService: ChatCompressionService
) {
    route("/compression") {
        /**
         * POST /compression/compress
         * Выполнить сжатие диалога
         */
        post("/compress") {
            try {
                val request = call.receive<CompressionRequest>()

                // Проверяем наличие sessionId
                if (request.sessionId.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "sessionId is required"))
                    return@post
                }

                // Получаем сессию
                val session = sessionManager.getSession(request.sessionId)
                if (session == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Session not found"))
                    return@post
                }

                // Проверяем, нужно ли сжатие
                if (!compressionService.shouldCompress(session, request.contextWindowSize)) {
                    call.respond(
                        HttpStatusCode.OK,
                        CompressionResponse(
                            success = false,
                            message = "Compression is not needed yet",
                            sessionId = session.id,
                            compressionPerformed = false
                        )
                    )
                    return@post
                }

                // Выполняем сжатие
                val result = compressionService.compressSession(
                    session = session,
                    providerId = request.providerId ?: ProviderType.CLAUDE,
                    model = request.model
                )

                result.onSuccess { compressionResult ->
                    // Помечаем сессию как требующую сохранения
                    sessionManager.markSessionDirty(session.id)

                    call.respond(
                        HttpStatusCode.OK,
                        CompressionResponse(
                            success = true,
                            message = "Compression completed successfully",
                            sessionId = session.id,
                            compressionPerformed = true,
                            originalMessageCount = compressionResult.originalMessageCount,
                            newMessageCount = compressionResult.newMessageCount,
                            compressionRatio = compressionResult.compressionRatio,
                            archivedMessageCount = compressionResult.archivedMessages.size,
                            totalCompressions = session.compressionCount,
                            summaryMessage = compressionResult.summaryMessage,
                            newMessages = session.messages // Актуальные сообщения после сжатия
                        )
                    )
                }.onFailure { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to (error.message ?: "Compression failed"))
                    )
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }

        /**
         * POST /compression/config
         * Обновить конфигурацию сжатия для сессии
         */
        post("/config") {
            try {
                val request = call.receive<UpdateCompressionConfigRequest>()

                // Проверяем наличие sessionId
                if (request.sessionId.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "sessionId is required"))
                    return@post
                }

                // Получаем сессию
                val session = sessionManager.getSession(request.sessionId)
                if (session == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Session not found"))
                    return@post
                }

                // Обновляем конфигурацию
                compressionService.updateCompressionStrategy(session, request.config)

                call.respond(
                    HttpStatusCode.OK,
                    UpdateConfigResponse(
                        success = true,
                        message = "Compression config updated",
                        sessionId = session.id,
                        newStrategy = request.config.strategy.name
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }

        /**
         * GET /compression/config/{sessionId}
         * Получить текущую конфигурацию сжатия для сессии
         */
        get("/config/{sessionId}") {
            val sessionId = call.parameters["sessionId"]
            if (sessionId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "sessionId is required"))
                return@get
            }

            val session = sessionManager.getSession(sessionId)
            if (session == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Session not found"))
                return@get
            }

            call.respond(
                HttpStatusCode.OK,
                CompressionConfigResponse(
                    sessionId = session.id,
                    config = session.compressionConfig,
                    compressionCount = session.compressionCount,
                    currentMessageCount = session.messages.size,
                    archivedMessageCount = session.archivedMessages.size,
                    totalTokens = session.getTotalTokens()
                )
            )
        }

        /**
         * GET /compression/check/{sessionId}
         * Проверить, нужно ли сжатие для сессии
         */
        get("/check/{sessionId}") {
            val sessionId = call.parameters["sessionId"]
            if (sessionId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "sessionId is required"))
                return@get
            }

            val session = sessionManager.getSession(sessionId)
            if (session == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Session not found"))
                return@get
            }

            val contextWindowSize = call.request.queryParameters["contextWindowSize"]?.toIntOrNull()
            val shouldCompress = compressionService.shouldCompress(session, contextWindowSize)

            call.respond(
                HttpStatusCode.OK,
                CompressionCheckResponse(
                    sessionId = session.id,
                    shouldCompress = shouldCompress,
                    currentMessageCount = session.messages.size,
                    currentStrategy = session.compressionConfig.strategy.name,
                    compressionCount = session.compressionCount,
                    totalTokens = session.getTotalTokens()
                )
            )
        }

        /**
         * GET /compression/archived/{sessionId}
         * Получить архивированные сообщения
         */
        get("/archived/{sessionId}") {
            val sessionId = call.parameters["sessionId"]
            if (sessionId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "sessionId is required"))
                return@get
            }

            val session = sessionManager.getSession(sessionId)
            if (session == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Session not found"))
                return@get
            }

            call.respond(
                HttpStatusCode.OK,
                ArchivedMessagesResponse(
                    sessionId = session.id,
                    archivedMessages = session.archivedMessages,
                    archivedCount = session.archivedMessages.size,
                    compressionCount = session.compressionCount
                )
            )
        }
    }
}

// Request/Response models

@Serializable
data class CompressionRequest(
    val sessionId: String,
    val providerId: ProviderType? = null, // Провайдер для генерации суммаризации
    val model: String? = null, // Модель для генерации суммаризации
    val contextWindowSize: Int? = null // Размер context window (для TOKEN_BASED стратегии)
)

@Serializable
data class CompressionResponse(
    val success: Boolean,
    val message: String,
    val sessionId: String,
    val compressionPerformed: Boolean,
    val originalMessageCount: Int = 0,
    val newMessageCount: Int = 0,
    val compressionRatio: Double = 0.0,
    val archivedMessageCount: Int = 0,
    val totalCompressions: Int = 0,
    val summaryMessage: Message? = null, // Сообщение с суммаризацией
    val newMessages: List<Message> = emptyList() // Новые сообщения после сжатия (включая summaryMessage)
)

@Serializable
data class UpdateCompressionConfigRequest(
    val sessionId: String,
    val config: CompressionConfig
)

@Serializable
data class UpdateConfigResponse(
    val success: Boolean,
    val message: String,
    val sessionId: String,
    val newStrategy: String
)

@Serializable
data class CompressionConfigResponse(
    val sessionId: String,
    val config: CompressionConfig,
    val compressionCount: Int,
    val currentMessageCount: Int,
    val archivedMessageCount: Int,
    val totalTokens: Int
)

@Serializable
data class CompressionCheckResponse(
    val sessionId: String,
    val shouldCompress: Boolean,
    val currentMessageCount: Int,
    val currentStrategy: String,
    val compressionCount: Int,
    val totalTokens: Int
)

@Serializable
data class ArchivedMessagesResponse(
    val sessionId: String,
    val archivedMessages: List<Message>,
    val archivedCount: Int,
    val compressionCount: Int
)
