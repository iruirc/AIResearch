package com.researchai.data.provider.claude

import com.researchai.domain.models.*
import com.researchai.domain.provider.AIModel
import com.researchai.domain.provider.AIProvider
import com.researchai.domain.provider.ModelCapabilities
import com.researchai.models.ClaudeModel as LegacyClaudeModel
import com.researchai.services.ClaudeMessageFormatter
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Реализация провайдера для Claude API
 */
class ClaudeProvider(
    private val httpClient: HttpClient,
    override val config: ProviderConfig.ClaudeConfig
) : AIProvider {

    override val providerId: ProviderType = ProviderType.CLAUDE

    private val logger = LoggerFactory.getLogger(ClaudeProvider::class.java)
    private val mapper = ClaudeMapper()
    private val formatter = ClaudeMessageFormatter()

    override suspend fun sendMessage(request: AIRequest): Result<AIResponse> {
        return try {
            logger.info("Claude Provider: Sending message")

            // Маппинг domain модели в Claude API модель
            val claudeRequest = mapper.toClaudeRequest(request, config, formatter)

            // HTTP запрос
            val httpResponse: HttpResponse = httpClient.post(config.baseUrl) {
                header("x-api-key", config.apiKey)
                header("anthropic-version", config.apiVersion)
                header("content-type", "application/json")
                setBody(claudeRequest)
            }

            logger.info("Claude API response status: ${httpResponse.status}")

            // Обработка ошибок
            if (!httpResponse.status.isSuccess()) {
                val errorBody = httpResponse.bodyAsText()
                logger.error("Claude API error response: $errorBody")

                return try {
                    val errorResponse: ClaudeApiError = Json.decodeFromString(errorBody)
                    Result.failure(
                        AIError.NetworkException("Claude API Error: ${errorResponse.error.message}")
                    )
                } catch (e: Exception) {
                    Result.failure(
                        AIError.NetworkException("Claude API Error (${httpResponse.status}): $errorBody")
                    )
                }
            }

            val claudeResponse: ClaudeApiResponse = httpResponse.body()

            // Маппинг обратно в domain модель
            val aiResponse = mapper.fromClaudeResponse(claudeResponse, request.parameters.responseFormat, formatter)

            logger.info("Successfully received response from Claude API")
            Result.success(aiResponse)

        } catch (e: Exception) {
            logger.error("Exception in ClaudeProvider: ${e.message}", e)
            Result.failure(AIError.NetworkException("Claude API error", e))
        }
    }

    override suspend fun getModels(): Result<List<AIModel>> {
        // Claude не предоставляет endpoint для списка моделей
        // Возвращаем хардкод список
        return Result.success(listOf(
            AIModel(
                id = "claude-sonnet-4-5-20250929",
                name = "Claude Sonnet 4.5",
                providerId = ProviderType.CLAUDE,
                capabilities = ModelCapabilities(
                    supportsVision = false,
                    supportsStreaming = true,
                    maxTokens = 8192,
                    contextWindow = 200000
                )
            ),
            AIModel(
                id = "claude-haiku-4-5-20251001",
                name = "Claude Haiku 4.5",
                providerId = ProviderType.CLAUDE,
                capabilities = ModelCapabilities(
                    supportsVision = false,
                    supportsStreaming = true,
                    maxTokens = 8192,
                    contextWindow = 200000
                )
            ),
            AIModel(
                id = "claude-opus-4-1-20250805",
                name = "Claude Opus 4.1",
                providerId = ProviderType.CLAUDE,
                capabilities = ModelCapabilities(
                    supportsVision = false,
                    supportsStreaming = true,
                    maxTokens = 16384,
                    contextWindow = 200000
                )
            )
        ))
    }

    override fun validateConfig(): ValidationResult {
        val errors = mutableListOf<String>()

        if (config.apiKey.isBlank()) {
            errors.add("API key is required")
        }
        if (!config.baseUrl.startsWith("https://")) {
            errors.add("Base URL must use HTTPS")
        }

        return if (errors.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(errors)
        }
    }
}
