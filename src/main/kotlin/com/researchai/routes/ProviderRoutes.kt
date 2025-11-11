package com.researchai.routes

import com.researchai.di.AppModule
import com.researchai.domain.models.ProviderConfig
import com.researchai.domain.models.ProviderType
import com.researchai.domain.models.RequestParameters
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/**
 * Новые API роуты для работы с множественными провайдерами
 */
fun Route.providerRoutes(appModule: AppModule) {
    val logger = LoggerFactory.getLogger("ProviderRoutes")

    route("/api/v2") {
        /**
         * POST /api/v2/chat
         * Отправка сообщения с выбором провайдера
         */
        post("/chat") {
            try {
                val request = call.receive<ChatRequestV2>()

                logger.info("Received chat request: provider=${request.provider}, message length=${request.message.length}")

                val providerId = ProviderType.fromId(request.provider)
                    ?: run {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Unknown provider: ${request.provider}"))
                        return@post
                    }

                val parameters = RequestParameters(
                    temperature = request.temperature,
                    maxTokens = request.maxTokens,
                    responseFormat = request.format ?: com.researchai.models.ResponseFormat.PLAIN_TEXT
                )

                val result = appModule.sendMessageUseCase(
                    message = request.message,
                    sessionId = request.sessionId,
                    providerId = providerId,
                    model = request.model,
                    parameters = parameters
                )

                result.onSuccess { messageResult ->
                    call.respond(
                        ChatResponseV2(
                            response = messageResult.response,
                            sessionId = messageResult.sessionId,
                            usage = UsageInfo(
                                inputTokens = messageResult.usage.inputTokens,
                                outputTokens = messageResult.usage.outputTokens,
                                totalTokens = messageResult.usage.totalTokens
                            ),
                            model = messageResult.model,
                            provider = messageResult.providerId.id
                        )
                    )
                }.onFailure { error ->
                    logger.error("Error processing chat request: ${error.message}", error)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to (error.message ?: "Unknown error"))
                    )
                }
            } catch (e: Exception) {
                logger.error("Exception in chat endpoint: ${e.message}", e)
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            }
        }

        /**
         * POST /api/v2/providers/configure
         * Настройка провайдера
         */
        post("/providers/configure") {
            try {
                val request = call.receive<ConfigureProviderRequest>()

                val providerId = ProviderType.fromId(request.provider)
                    ?: run {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Unknown provider: ${request.provider}"))
                        return@post
                    }

                val config = when (providerId) {
                    ProviderType.OPENAI -> ProviderConfig.OpenAIConfig(
                        apiKey = request.apiKey,
                        organization = request.organization,
                        defaultModel = request.defaultModel ?: "gpt-4-turbo"
                    )
                    ProviderType.HUGGINGFACE -> ProviderConfig.HuggingFaceConfig(
                        apiKey = request.apiKey,
                        defaultModel = request.defaultModel ?: "deepseek-ai/DeepSeek-R1:fastest"
                    )
                    ProviderType.GEMINI -> ProviderConfig.GeminiConfig(
                        apiKey = request.apiKey,
                        defaultModel = request.defaultModel ?: "gemini-pro"
                    )
                    ProviderType.CLAUDE -> {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Claude is already configured via environment"))
                        return@post
                    }
                    ProviderType.CUSTOM -> ProviderConfig.CustomConfig(
                        apiKey = request.apiKey,
                        baseUrl = request.baseUrl ?: "",
                        headers = request.headers ?: emptyMap()
                    )
                }

                val result = appModule.configRepository.saveProviderConfig(providerId, config)

                result.onSuccess {
                    call.respond(
                        HttpStatusCode.OK,
                        mapOf("message" to "Provider ${providerId.displayName} configured successfully")
                    )
                }.onFailure { error ->
                    logger.error("Error configuring provider: ${error.message}", error)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to (error.message ?: "Failed to configure provider"))
                    )
                }
            } catch (e: Exception) {
                logger.error("Exception in configure provider endpoint: ${e.message}", e)
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            }
        }

        /**
         * GET /api/v2/providers/{provider}/models
         * Получение списка моделей провайдера
         */
        get("/providers/{provider}/models") {
            try {
                val providerParam = call.parameters["provider"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Provider parameter is required"))
                    return@get
                }

                val providerId = ProviderType.fromId(providerParam)
                    ?: run {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Unknown provider: $providerParam"))
                        return@get
                    }

                val result = appModule.getModelsUseCase(providerId)

                result.onSuccess { models ->
                    call.respond(
                        ModelsResponse(
                            provider = providerId.id,
                            models = models.map { model ->
                                ModelInfo(
                                    id = model.id,
                                    name = model.name,
                                    capabilities = CapabilitiesInfo(
                                        supportsVision = model.capabilities.supportsVision,
                                        supportsStreaming = model.capabilities.supportsStreaming,
                                        maxTokens = model.capabilities.maxTokens,
                                        contextWindow = model.capabilities.contextWindow
                                    )
                                )
                            }
                        )
                    )
                }.onFailure { error ->
                    logger.error("Error getting models: ${error.message}", error)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to (error.message ?: "Failed to get models"))
                    )
                }
            } catch (e: Exception) {
                logger.error("Exception in get models endpoint: ${e.message}", e)
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            }
        }

        /**
         * GET /api/v2/providers
         * Получение списка доступных провайдеров
         */
        get("/providers") {
            val providers = ProviderType.values().map { provider ->
                ProviderInfo(
                    id = provider.id,
                    displayName = provider.displayName
                )
            }
            call.respond(ProvidersResponse(providers = providers))
        }
    }
}

// Request/Response models для новых эндпоинтов

@Serializable
data class ChatRequestV2(
    val message: String,
    val provider: String = "claude", // "claude", "openai", "gemini"
    val sessionId: String? = null,
    val model: String? = null,
    val temperature: Double = 1.0,
    val maxTokens: Int = 4096,
    val format: com.researchai.models.ResponseFormat? = null
)

@Serializable
data class ChatResponseV2(
    val response: String,
    val sessionId: String,
    val usage: UsageInfo,
    val model: String,
    val provider: String
)

@Serializable
data class UsageInfo(
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int
)

@Serializable
data class ConfigureProviderRequest(
    val provider: String,
    val apiKey: String,
    val organization: String? = null,
    val baseUrl: String? = null,
    val defaultModel: String? = null,
    val headers: Map<String, String>? = null
)

@Serializable
data class ModelsResponse(
    val provider: String,
    val models: List<ModelInfo>
)

@Serializable
data class ModelInfo(
    val id: String,
    val name: String,
    val capabilities: CapabilitiesInfo
)

@Serializable
data class CapabilitiesInfo(
    val supportsVision: Boolean,
    val supportsStreaming: Boolean,
    val maxTokens: Int,
    val contextWindow: Int
)

@Serializable
data class ProvidersResponse(
    val providers: List<ProviderInfo>
)

@Serializable
data class ProviderInfo(
    val id: String,
    val displayName: String
)
