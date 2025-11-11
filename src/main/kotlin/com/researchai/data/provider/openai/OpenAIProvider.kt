package com.researchai.data.provider.openai

import com.researchai.domain.models.*
import com.researchai.domain.provider.AIModel
import com.researchai.domain.provider.AIProvider
import com.researchai.domain.provider.ModelCapabilities
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Реализация провайдера для OpenAI API
 */
class OpenAIProvider(
    private val httpClient: HttpClient,
    override val config: ProviderConfig.OpenAIConfig
) : AIProvider {

    override val providerId: ProviderType = ProviderType.OPENAI

    private val logger = LoggerFactory.getLogger(OpenAIProvider::class.java)
    private val mapper = OpenAIMapper()

    override suspend fun sendMessage(request: AIRequest): Result<AIResponse> {
        return try {
            logger.info("OpenAI Provider: Sending message")

            // Маппинг domain модели в OpenAI API модель
            val openAIRequest = mapper.toOpenAIRequest(request, config)

            // HTTP запрос
            val httpResponse: HttpResponse = httpClient.post(config.baseUrl) {
                header("Authorization", "Bearer ${config.apiKey}")
                config.organization?.let { header("OpenAI-Organization", it) }
                header("Content-Type", "application/json")
                setBody(openAIRequest)
            }

            logger.info("OpenAI API response status: ${httpResponse.status}")

            // Обработка ошибок
            if (!httpResponse.status.isSuccess()) {
                val errorBody = httpResponse.bodyAsText()
                logger.error("OpenAI API error response: $errorBody")

                return try {
                    val errorResponse: OpenAIApiError = Json.decodeFromString(errorBody)
                    Result.failure(
                        AIError.NetworkException("OpenAI API Error: ${errorResponse.error.message}")
                    )
                } catch (e: Exception) {
                    Result.failure(
                        AIError.NetworkException("OpenAI API Error (${httpResponse.status}): $errorBody")
                    )
                }
            }

            val openAIResponse: OpenAIApiResponse = httpResponse.body()

            // Маппинг обратно в domain модель
            val aiResponse = mapper.fromOpenAIResponse(openAIResponse)

            logger.info("Successfully received response from OpenAI API")
            Result.success(aiResponse)

        } catch (e: Exception) {
            logger.error("Exception in OpenAIProvider: ${e.message}", e)
            Result.failure(AIError.NetworkException("OpenAI API error", e))
        }
    }

    override suspend fun getModels(): Result<List<AIModel>> {
        return try {
            val httpResponse: HttpResponse = httpClient.get("https://api.openai.com/v1/models") {
                header("Authorization", "Bearer ${config.apiKey}")
            }

            if (!httpResponse.status.isSuccess()) {
                return Result.failure(
                    AIError.NetworkException("Failed to fetch OpenAI models: ${httpResponse.status}")
                )
            }

            val modelsResponse: OpenAIModelsResponse = httpResponse.body()

            val aiModels = modelsResponse.data
                .filter { it.id.startsWith("gpt-") }
                .map { model ->
                    AIModel(
                        id = model.id,
                        name = model.id,
                        providerId = ProviderType.OPENAI,
                        capabilities = ModelCapabilities(
                            supportsVision = model.id.contains("vision") || model.id.contains("4o"),
                            supportsStreaming = true,
                            maxTokens = getMaxTokensForModel(model.id),
                            contextWindow = getContextWindowForModel(model.id)
                        )
                    )
                }

            Result.success(aiModels)
        } catch (e: Exception) {
            logger.error("Failed to get OpenAI models: ${e.message}", e)
            Result.failure(AIError.NetworkException("Failed to get models", e))
        }
    }

    override fun validateConfig(): ValidationResult {
        val errors = mutableListOf<String>()

        if (config.apiKey.isBlank()) {
            errors.add("API key is required")
        }
        if (!config.apiKey.startsWith("sk-")) {
            errors.add("Invalid API key format (should start with 'sk-')")
        }

        return if (errors.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(errors)
        }
    }

    private fun getMaxTokensForModel(modelId: String): Int {
        return when {
            modelId.contains("gpt-4o") -> 16384
            modelId.contains("gpt-4-turbo") -> 4096
            modelId.contains("gpt-4") -> 8192
            modelId.contains("gpt-3.5-turbo") -> 4096
            else -> 4096
        }
    }

    private fun getContextWindowForModel(modelId: String): Int {
        return when {
            modelId.contains("gpt-4o") -> 128000
            modelId.contains("gpt-4-turbo") -> 128000
            modelId.contains("gpt-4-32k") -> 32768
            modelId.contains("gpt-4") -> 8192
            modelId.contains("gpt-3.5-turbo-16k") -> 16384
            modelId.contains("gpt-3.5-turbo") -> 4096
            else -> 4096
        }
    }
}
