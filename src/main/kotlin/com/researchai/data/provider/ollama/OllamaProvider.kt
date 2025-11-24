package com.researchai.data.provider.ollama

import com.researchai.domain.models.*
import com.researchai.domain.provider.AIModel
import com.researchai.domain.provider.AIProvider
import com.researchai.domain.provider.ModelCapabilities
import com.researchai.domain.tokenizer.TokenCounter
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.slf4j.LoggerFactory

/**
 * Реализация провайдера для Ollama API
 */
class OllamaProvider(
    private val httpClient: HttpClient,
    override val config: ProviderConfig.OllamaConfig,
    private val tokenCounter: TokenCounter
) : AIProvider {

    override val providerId = ProviderType.OLLAMA

    private val logger = LoggerFactory.getLogger(OllamaProvider::class.java)
    private val mapper = OllamaMapper()

    override suspend fun sendMessage(request: AIRequest): Result<AIResponse> {
        return try {
            logger.info("Ollama Provider: Sending message to ${config.baseUrl}")

            // Маппинг domain модели в Ollama API модель
            val ollamaRequest = mapper.toOllamaRequest(request, config)

            // HTTP запрос
            val httpResponse: HttpResponse = httpClient.post("${config.baseUrl}/api/chat") {
                header("Content-Type", "application/json")
                setBody(ollamaRequest)
            }

            logger.info("Ollama API response status: ${httpResponse.status}")

            // Обработка ошибок
            if (!httpResponse.status.isSuccess()) {
                val errorBody = httpResponse.bodyAsText()
                logger.error("Ollama API error response: $errorBody")

                throw AIError.NetworkException(
                    "Ollama API Error (${httpResponse.status}): $errorBody"
                )
            }

            // Success response
            val ollamaResponse: OllamaChatResponse = httpResponse.body()

            // Маппинг обратно в domain модель
            val aiResponse = mapper.fromOllamaResponse(ollamaResponse)

            logger.info("Successfully received response from Ollama API")
            logger.info("Tokens - Input: ${aiResponse.usage.inputTokens}, Output: ${aiResponse.usage.outputTokens}")

            Result.success(aiResponse)

        } catch (e: Exception) {
            logger.error("Exception in OllamaProvider: ${e.message}", e)
            Result.failure(AIError.fromException(e))
        }
    }

    override suspend fun getModels(): Result<List<AIModel>> {
        return try {
            logger.info("Fetching models from Ollama: ${config.baseUrl}/api/tags")

            val httpResponse: HttpResponse = httpClient.get("${config.baseUrl}/api/tags")

            if (!httpResponse.status.isSuccess()) {
                val errorBody = httpResponse.bodyAsText()
                logger.error("Failed to fetch models: $errorBody")
                return Result.failure(AIError.NetworkException("Failed to fetch models from Ollama"))
            }

            val response: OllamaModelsResponse = httpResponse.body()

            val models = response.models.map { modelInfo ->
                AIModel(
                    id = modelInfo.name,
                    name = modelInfo.name,
                    providerId = ProviderType.OLLAMA,
                    capabilities = inferCapabilities(modelInfo)
                )
            }

            logger.info("Successfully fetched ${models.size} models from Ollama")
            Result.success(models)

        } catch (e: Exception) {
            logger.error("Exception while fetching models: ${e.message}", e)
            Result.failure(AIError.fromException(e))
        }
    }

    override fun validateConfig(): ValidationResult {
        val errors = mutableListOf<String>()

        if (config.baseUrl.isBlank()) {
            errors.add("Base URL is required")
        }
        if (!config.baseUrl.startsWith("http://") && !config.baseUrl.startsWith("https://")) {
            errors.add("Base URL must start with http:// or https://")
        }

        return if (errors.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(errors)
        }
    }

    /**
     * Определяет capabilities модели на основе её метаданных
     */
    private fun inferCapabilities(modelInfo: OllamaModelInfo): ModelCapabilities {
        val family = modelInfo.details?.family?.lowercase() ?: ""

        // Определяем поддержку vision по семейству модели
        val supportsVision = family.contains("llava") || family.contains("vision")

        // Примерные значения для context window и max tokens
        val contextWindow = when {
            family.contains("llama") -> 8192
            family.contains("mistral") -> 8192
            family.contains("gemma") -> 8192
            else -> 4096
        }

        return ModelCapabilities(
            supportsVision = supportsVision,
            supportsStreaming = true,
            maxTokens = 4096,
            contextWindow = contextWindow
        )
    }

    /**
     * Проверка доступности Ollama сервера
     */
    suspend fun testConnection(): Result<Boolean> {
        return try {
            val httpResponse: HttpResponse = httpClient.get("${config.baseUrl}/api/version")

            if (httpResponse.status.isSuccess()) {
                Result.success(true)
            } else {
                Result.failure(AIError.NetworkException("Ollama server not accessible"))
            }
        } catch (e: Exception) {
            Result.failure(AIError.fromException(e))
        }
    }
}
