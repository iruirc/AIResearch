package com.researchai.routes

import com.researchai.data.provider.ollama.OllamaConnectionManager
import com.researchai.domain.models.OllamaConnection
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("OllamaRoutes")

/**
 * Request для создания/обновления Ollama подключения
 */
@Serializable
data class OllamaConnectionRequest(
    val id: String? = null, // null для создания нового, заполнено для обновления
    val name: String,
    val baseUrl: String,
    val keepAlive: String = "5m"
)

/**
 * Response для операций с подключениями
 */
@Serializable
data class OllamaConnectionResponse(
    val success: Boolean,
    val message: String,
    val connection: OllamaConnection? = null
)

/**
 * Response для списка подключений
 */
@Serializable
data class OllamaConnectionsListResponse(
    val connections: List<OllamaConnection>,
    val activeConnectionId: String?
)

/**
 * Response для тестирования подключения
 */
@Serializable
data class OllamaTestConnectionResponse(
    val success: Boolean,
    val message: String,
    val version: String? = null
)

/**
 * Response для списка моделей
 */
@Serializable
data class OllamaModelsResponse(
    val success: Boolean,
    val models: List<OllamaModelInfo>
)

@Serializable
data class OllamaModelInfo(
    val name: String,
    val size: Long,
    val modifiedAt: String,
    val family: String? = null
)

/**
 * Регистрирует маршруты для работы с Ollama подключениями
 * Доступны по адресу /api/v2/providers/ollama/...
 */
fun Route.ollamaRoutes(connectionManager: OllamaConnectionManager) {
    route("/api/v2/providers/ollama") {

        // GET /api/v2/providers/ollama/connections - Получить список всех подключений
        get("/connections") {
            try {
                val connections = connectionManager.getAllConnections()
                val activeId = connectionManager.getActiveConnectionId()
                call.respond(
                    OllamaConnectionsListResponse(
                        connections = connections,
                        activeConnectionId = activeId
                    )
                )
                logger.debug("Retrieved ${connections.size} Ollama connections")
            } catch (e: Exception) {
                logger.error("Error in GET /connections", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    OllamaConnectionResponse(
                        success = false,
                        message = "Failed to retrieve connections: ${e.message}"
                    )
                )
            }
        }

        // GET /api/v2/providers/ollama/connections/{id} - Получить подключение по ID
        get("/connections/{id}") {
            try {
                val connectionId = call.parameters["id"]
                if (connectionId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        OllamaConnectionResponse(
                            success = false,
                            message = "Connection ID is required"
                        )
                    )
                    return@get
                }

                val connection = connectionManager.getConnection(connectionId)
                if (connection == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        OllamaConnectionResponse(
                            success = false,
                            message = "Connection with ID '$connectionId' not found"
                        )
                    )
                    return@get
                }

                call.respond(
                    OllamaConnectionResponse(
                        success = true,
                        message = "Connection retrieved successfully",
                        connection = connection
                    )
                )
                logger.debug("Retrieved connection: $connectionId")
            } catch (e: Exception) {
                logger.error("Error in GET /connections/{id}", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    OllamaConnectionResponse(
                        success = false,
                        message = "Failed to retrieve connection: ${e.message}"
                    )
                )
            }
        }

        // POST /api/v2/providers/ollama/connections - Создать новое подключение
        post("/connections") {
            try {
                val request = call.receive<OllamaConnectionRequest>()

                // Валидация
                if (request.name.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        OllamaConnectionResponse(
                            success = false,
                            message = "Connection name cannot be empty"
                        )
                    )
                    return@post
                }

                if (request.baseUrl.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        OllamaConnectionResponse(
                            success = false,
                            message = "Base URL cannot be empty"
                        )
                    )
                    return@post
                }

                if (!request.baseUrl.startsWith("http://") && !request.baseUrl.startsWith("https://")) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        OllamaConnectionResponse(
                            success = false,
                            message = "Base URL must start with http:// or https://"
                        )
                    )
                    return@post
                }

                // Создаем подключение
                val connection = OllamaConnection(
                    name = request.name,
                    baseUrl = request.baseUrl,
                    keepAlive = request.keepAlive
                )

                connectionManager.addConnection(connection)
                    .onSuccess { createdConnection ->
                        call.respond(
                            HttpStatusCode.Created,
                            OllamaConnectionResponse(
                                success = true,
                                message = "Connection created successfully",
                                connection = createdConnection
                            )
                        )
                        logger.info("Created Ollama connection: ${createdConnection.id} (${createdConnection.name})")
                    }
                    .onFailure { error ->
                        logger.error("Failed to create connection", error)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            OllamaConnectionResponse(
                                success = false,
                                message = "Failed to create connection: ${error.message}"
                            )
                        )
                    }

            } catch (e: Exception) {
                logger.error("Error in POST /connections", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    OllamaConnectionResponse(
                        success = false,
                        message = "Failed to create connection: ${e.message}"
                    )
                )
            }
        }

        // PUT /api/v2/providers/ollama/connections/{id} - Обновить подключение
        put("/connections/{id}") {
            try {
                val connectionId = call.parameters["id"]
                if (connectionId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        OllamaConnectionResponse(
                            success = false,
                            message = "Connection ID is required"
                        )
                    )
                    return@put
                }

                val request = call.receive<OllamaConnectionRequest>()

                // Валидация
                if (request.name.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        OllamaConnectionResponse(
                            success = false,
                            message = "Connection name cannot be empty"
                        )
                    )
                    return@put
                }

                if (request.baseUrl.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        OllamaConnectionResponse(
                            success = false,
                            message = "Base URL cannot be empty"
                        )
                    )
                    return@put
                }

                if (!request.baseUrl.startsWith("http://") && !request.baseUrl.startsWith("https://")) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        OllamaConnectionResponse(
                            success = false,
                            message = "Base URL must start with http:// or https://"
                        )
                    )
                    return@put
                }

                // Обновляем подключение
                val connection = OllamaConnection(
                    id = connectionId,
                    name = request.name,
                    baseUrl = request.baseUrl,
                    keepAlive = request.keepAlive
                )

                connectionManager.updateConnection(connectionId, connection)
                    .onSuccess { updatedConnection ->
                        call.respond(
                            OllamaConnectionResponse(
                                success = true,
                                message = "Connection updated successfully",
                                connection = updatedConnection
                            )
                        )
                        logger.info("Updated Ollama connection: ${updatedConnection.id}")
                    }
                    .onFailure { error ->
                        logger.error("Failed to update connection", error)
                        val statusCode = when {
                            error.message?.contains("not found") == true -> HttpStatusCode.NotFound
                            else -> HttpStatusCode.InternalServerError
                        }
                        call.respond(
                            statusCode,
                            OllamaConnectionResponse(
                                success = false,
                                message = "Failed to update connection: ${error.message}"
                            )
                        )
                    }

            } catch (e: Exception) {
                logger.error("Error in PUT /connections/{id}", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    OllamaConnectionResponse(
                        success = false,
                        message = "Failed to update connection: ${e.message}"
                    )
                )
            }
        }

        // DELETE /api/v2/providers/ollama/connections/{id} - Удалить подключение
        delete("/connections/{id}") {
            try {
                val connectionId = call.parameters["id"]
                if (connectionId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        OllamaConnectionResponse(
                            success = false,
                            message = "Connection ID is required"
                        )
                    )
                    return@delete
                }

                connectionManager.removeConnection(connectionId)
                    .onSuccess {
                        call.respond(
                            OllamaConnectionResponse(
                                success = true,
                                message = "Connection deleted successfully"
                            )
                        )
                        logger.info("Deleted Ollama connection: $connectionId")
                    }
                    .onFailure { error ->
                        logger.error("Failed to delete connection", error)
                        val statusCode = when {
                            error.message?.contains("not found") == true -> HttpStatusCode.NotFound
                            else -> HttpStatusCode.InternalServerError
                        }
                        call.respond(
                            statusCode,
                            OllamaConnectionResponse(
                                success = false,
                                message = "Failed to delete connection: ${error.message}"
                            )
                        )
                    }

            } catch (e: Exception) {
                logger.error("Error in DELETE /connections/{id}", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    OllamaConnectionResponse(
                        success = false,
                        message = "Failed to delete connection: ${e.message}"
                    )
                )
            }
        }

        // POST /api/v2/providers/ollama/connections/{id}/activate - Установить активное подключение
        post("/connections/{id}/activate") {
            try {
                val connectionId = call.parameters["id"]
                if (connectionId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        OllamaConnectionResponse(
                            success = false,
                            message = "Connection ID is required"
                        )
                    )
                    return@post
                }

                connectionManager.setActiveConnection(connectionId)
                    .onSuccess {
                        call.respond(
                            OllamaConnectionResponse(
                                success = true,
                                message = "Active connection set successfully"
                            )
                        )
                        logger.info("Set active Ollama connection: $connectionId")
                    }
                    .onFailure { error ->
                        logger.error("Failed to set active connection", error)
                        val statusCode = when {
                            error.message?.contains("not found") == true -> HttpStatusCode.NotFound
                            else -> HttpStatusCode.InternalServerError
                        }
                        call.respond(
                            statusCode,
                            OllamaConnectionResponse(
                                success = false,
                                message = "Failed to set active connection: ${error.message}"
                            )
                        )
                    }

            } catch (e: Exception) {
                logger.error("Error in POST /connections/{id}/activate", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    OllamaConnectionResponse(
                        success = false,
                        message = "Failed to set active connection: ${e.message}"
                    )
                )
            }
        }

        // POST /api/v2/providers/ollama/connections/{id}/test - Протестировать подключение
        post("/connections/{id}/test") {
            try {
                val connectionId = call.parameters["id"]
                if (connectionId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        OllamaTestConnectionResponse(
                            success = false,
                            message = "Connection ID is required"
                        )
                    )
                    return@post
                }

                connectionManager.testConnection(connectionId)
                    .onSuccess { isConnected ->
                        call.respond(
                            OllamaTestConnectionResponse(
                                success = true,
                                message = "Connection test successful",
                                version = if (isConnected) "Connected" else null
                            )
                        )
                        logger.info("Tested Ollama connection: $connectionId - Status: $isConnected")
                    }
                    .onFailure { error ->
                        logger.error("Failed to test connection", error)
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            OllamaTestConnectionResponse(
                                success = false,
                                message = "Connection test failed: ${error.message}"
                            )
                        )
                    }

            } catch (e: Exception) {
                logger.error("Error in POST /connections/{id}/test", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    OllamaTestConnectionResponse(
                        success = false,
                        message = "Connection test error: ${e.message}"
                    )
                )
            }
        }

        // GET /api/v2/providers/ollama/models - Получить список моделей с активного подключения
        get("/models") {
            try {
                val provider = connectionManager.getActiveProvider()
                if (provider == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        OllamaModelsResponse(
                            success = false,
                            models = emptyList()
                        )
                    )
                    return@get
                }

                provider.getModels()
                    .onSuccess { models ->
                        val modelInfos = models.map { model ->
                            OllamaModelInfo(
                                name = model.id,
                                size = 0, // Не доступно из AIModel
                                modifiedAt = "", // Не доступно из AIModel
                                family = null // Не доступно из AIModel
                            )
                        }
                        call.respond(
                            OllamaModelsResponse(
                                success = true,
                                models = modelInfos
                            )
                        )
                        logger.debug("Retrieved ${models.size} models from Ollama")
                    }
                    .onFailure { error ->
                        logger.error("Failed to get models", error)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            OllamaModelsResponse(
                                success = false,
                                models = emptyList()
                            )
                        )
                    }

            } catch (e: Exception) {
                logger.error("Error in GET /models", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    OllamaModelsResponse(
                        success = false,
                        models = emptyList()
                    )
                )
            }
        }
    }
}
