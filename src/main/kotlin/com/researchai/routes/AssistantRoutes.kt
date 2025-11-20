package com.researchai.routes

import com.researchai.models.Assistant
import com.researchai.services.AssistantManager
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("AssistantRoutes")

/**
 * Request для создания/обновления ассистента
 */
@Serializable
data class AssistantRequest(
    val id: String,
    val name: String,
    val systemPrompt: String,
    val description: String = ""
)

/**
 * Response для операций с ассистентами
 */
@Serializable
data class AssistantResponse(
    val success: Boolean,
    val message: String,
    val assistant: Assistant? = null
)

/**
 * Response для списка ассистентов
 */
@Serializable
data class AssistantsListResponse(
    val assistants: List<Assistant>
)

/**
 * Регистрирует маршруты для работы с ассистентами
 */
fun Route.assistantRoutes(assistantManager: AssistantManager) {
    route("/assistants") {

        // GET /assistants - Получить список всех ассистентов
        get {
            try {
                val assistants = assistantManager.getAllAssistants()
                call.respond(AssistantsListResponse(assistants = assistants))
                logger.debug("Retrieved ${assistants.size} assistants")
            } catch (e: Exception) {
                logger.error("Error in GET /assistants", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    AssistantResponse(
                        success = false,
                        message = "Failed to retrieve assistants: ${e.message}"
                    )
                )
            }
        }

        // GET /assistants/{id} - Получить ассистента по ID
        get("/{id}") {
            try {
                val assistantId = call.parameters["id"]
                if (assistantId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        AssistantResponse(
                            success = false,
                            message = "Assistant ID is required"
                        )
                    )
                    return@get
                }

                val assistant = assistantManager.getAssistant(assistantId)
                if (assistant == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        AssistantResponse(
                            success = false,
                            message = "Assistant with ID '$assistantId' not found"
                        )
                    )
                    return@get
                }

                call.respond(
                    AssistantResponse(
                        success = true,
                        message = "Assistant retrieved successfully",
                        assistant = assistant
                    )
                )
                logger.debug("Retrieved assistant: $assistantId")
            } catch (e: Exception) {
                logger.error("Error in GET /assistants/{id}", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    AssistantResponse(
                        success = false,
                        message = "Failed to retrieve assistant: ${e.message}"
                    )
                )
            }
        }

        // POST /assistants - Создать нового ассистента
        post {
            try {
                val request = call.receive<AssistantRequest>()

                // Валидация
                if (request.id.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        AssistantResponse(
                            success = false,
                            message = "Assistant ID cannot be empty"
                        )
                    )
                    return@post
                }

                if (request.name.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        AssistantResponse(
                            success = false,
                            message = "Assistant name cannot be empty"
                        )
                    )
                    return@post
                }

                if (request.systemPrompt.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        AssistantResponse(
                            success = false,
                            message = "System prompt cannot be empty"
                        )
                    )
                    return@post
                }

                // Создаем ассистента
                val assistant = Assistant(
                    id = request.id,
                    name = request.name,
                    systemPrompt = request.systemPrompt,
                    description = request.description,
                    isSystem = false
                )

                assistantManager.createAssistant(assistant)
                    .onSuccess { createdAssistant ->
                        call.respond(
                            HttpStatusCode.Created,
                            AssistantResponse(
                                success = true,
                                message = "Assistant created successfully",
                                assistant = createdAssistant
                            )
                        )
                        logger.info("Created assistant: ${createdAssistant.id}")
                    }
                    .onFailure { error ->
                        logger.error("Failed to create assistant", error)
                        val statusCode = when {
                            error.message?.contains("already exists") == true -> HttpStatusCode.Conflict
                            else -> HttpStatusCode.InternalServerError
                        }
                        call.respond(
                            statusCode,
                            AssistantResponse(
                                success = false,
                                message = "Failed to create assistant: ${error.message}"
                            )
                        )
                    }

            } catch (e: Exception) {
                logger.error("Error in POST /assistants", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    AssistantResponse(
                        success = false,
                        message = "Failed to create assistant: ${e.message}"
                    )
                )
            }
        }

        // PUT /assistants/{id} - Обновить ассистента
        put("/{id}") {
            try {
                val assistantId = call.parameters["id"]
                if (assistantId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        AssistantResponse(
                            success = false,
                            message = "Assistant ID is required"
                        )
                    )
                    return@put
                }

                val request = call.receive<AssistantRequest>()

                // Проверяем, что ID в URL совпадает с ID в теле запроса
                if (request.id != assistantId) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        AssistantResponse(
                            success = false,
                            message = "Assistant ID in URL does not match ID in request body"
                        )
                    )
                    return@put
                }

                // Валидация
                if (request.name.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        AssistantResponse(
                            success = false,
                            message = "Assistant name cannot be empty"
                        )
                    )
                    return@put
                }

                if (request.systemPrompt.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        AssistantResponse(
                            success = false,
                            message = "System prompt cannot be empty"
                        )
                    )
                    return@put
                }

                // Обновляем ассистента
                val assistant = Assistant(
                    id = request.id,
                    name = request.name,
                    systemPrompt = request.systemPrompt,
                    description = request.description,
                    isSystem = false
                )

                assistantManager.updateAssistant(assistant)
                    .onSuccess { updatedAssistant ->
                        call.respond(
                            AssistantResponse(
                                success = true,
                                message = "Assistant updated successfully",
                                assistant = updatedAssistant
                            )
                        )
                        logger.info("Updated assistant: ${updatedAssistant.id}")
                    }
                    .onFailure { error ->
                        logger.error("Failed to update assistant", error)
                        val statusCode = when {
                            error.message?.contains("not found") == true -> HttpStatusCode.NotFound
                            error.message?.contains("system assistant") == true -> HttpStatusCode.Forbidden
                            else -> HttpStatusCode.InternalServerError
                        }
                        call.respond(
                            statusCode,
                            AssistantResponse(
                                success = false,
                                message = "Failed to update assistant: ${error.message}"
                            )
                        )
                    }

            } catch (e: Exception) {
                logger.error("Error in PUT /assistants/{id}", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    AssistantResponse(
                        success = false,
                        message = "Failed to update assistant: ${e.message}"
                    )
                )
            }
        }

        // DELETE /assistants/{id} - Удалить ассистента
        delete("/{id}") {
            try {
                val assistantId = call.parameters["id"]
                if (assistantId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        AssistantResponse(
                            success = false,
                            message = "Assistant ID is required"
                        )
                    )
                    return@delete
                }

                assistantManager.deleteAssistant(assistantId)
                    .onSuccess {
                        call.respond(
                            AssistantResponse(
                                success = true,
                                message = "Assistant deleted successfully"
                            )
                        )
                        logger.info("Deleted assistant: $assistantId")
                    }
                    .onFailure { error ->
                        logger.error("Failed to delete assistant", error)
                        val statusCode = when {
                            error.message?.contains("not found") == true -> HttpStatusCode.NotFound
                            error.message?.contains("system assistant") == true -> HttpStatusCode.Forbidden
                            else -> HttpStatusCode.InternalServerError
                        }
                        call.respond(
                            statusCode,
                            AssistantResponse(
                                success = false,
                                message = "Failed to delete assistant: ${error.message}"
                            )
                        )
                    }

            } catch (e: Exception) {
                logger.error("Error in DELETE /assistants/{id}", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    AssistantResponse(
                        success = false,
                        message = "Failed to delete assistant: ${e.message}"
                    )
                )
            }
        }
    }
}
