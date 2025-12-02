package com.researchai.data.provider.claude

import com.researchai.domain.models.*
import com.researchai.domain.provider.AIModel
import com.researchai.domain.provider.AIProvider
import com.researchai.domain.provider.ModelCapabilities
import com.researchai.domain.tokenizer.TokenCounter
import com.researchai.domain.utils.RetryUtils
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
    override val config: ProviderConfig.ClaudeConfig,
    private val tokenCounter: TokenCounter
) : AIProvider {

    override val providerId: ProviderType = ProviderType.CLAUDE

    private val logger = LoggerFactory.getLogger(ClaudeProvider::class.java)
    private val mapper = ClaudeMapper()
    private val formatter = ClaudeMessageFormatter()

    override suspend fun sendMessage(request: AIRequest): Result<AIResponse> {
        return try {
            logger.info("Claude Provider: Sending message")

            // Подсчёт входных токенов локально (приблизительная оценка)
            val estimatedInputTokens = tokenCounter.countTokensWithFormatting(
                request.messages,
                request.systemPrompt
            )
            logger.info("Estimated input tokens: $estimatedInputTokens")

            // Маппинг domain модели в Claude API модель
            val claudeRequest = mapper.toClaudeRequest(request, config, formatter)

            // Retry config: retry on 529 (overloaded) and network errors
            val retryConfig = RetryUtils.RetryConfig(
                maxRetries = 3,
                baseDelayMs = 1000L,
                shouldRetry = { exception ->
                    // Retry on network errors and connection issues
                    exception is java.net.SocketException ||
                    exception is java.net.ConnectException ||
                    exception is java.io.IOException
                }
            )

            // Execute with retry
            val finalResponse = RetryUtils.withRetry(retryConfig) {
                // HTTP запрос
                val httpResponse: HttpResponse = httpClient.post(config.baseUrl) {
                    header("x-api-key", config.apiKey)
                    header("anthropic-version", config.apiVersion)
                    header("content-type", "application/json")
                    setBody(claudeRequest)
                }

                logger.info("Claude API response status: ${httpResponse.status}")

                // Обработка ошибок 529 (Overloaded) - throw to trigger retry
                if (httpResponse.status.value == 529) {
                    val errorBody = httpResponse.bodyAsText()
                    throw java.io.IOException("Claude API overloaded: $errorBody")
                }

                // Обработка других ошибок - не retry
                if (!httpResponse.status.isSuccess()) {
                    val errorBody = httpResponse.bodyAsText()
                    logger.error("Claude API error response: $errorBody")

                    val errorResponse = try {
                        Json.decodeFromString<ClaudeApiError>(errorBody)
                    } catch (e: Exception) {
                        null
                    }

                    throw AIError.NetworkException(
                        errorResponse?.error?.message ?: "Claude API Error (${httpResponse.status}): $errorBody"
                    )
                }

                // Success response
                val claudeResponse: ClaudeApiResponse = httpResponse.body()

                // Маппинг обратно в domain модель
                val aiResponse = mapper.fromClaudeResponse(claudeResponse, request.parameters.responseFormat, formatter)

                // Подсчёт выходных токенов локально (приблизительная оценка)
                val estimatedOutputTokens = tokenCounter.countTokens(aiResponse.content)

                // Добавляем локально подсчитанные токены
                aiResponse.copy(
                    estimatedInputTokens = estimatedInputTokens,
                    estimatedOutputTokens = estimatedOutputTokens
                )
            }

            logger.info("Successfully received response from Claude API")
            logger.info("Actual tokens - Input: ${finalResponse.usage.inputTokens}, Output: ${finalResponse.usage.outputTokens}")
            logger.info("Estimated tokens - Input: $estimatedInputTokens (diff: ${finalResponse.usage.inputTokens - estimatedInputTokens}), Output: ${finalResponse.estimatedOutputTokens} (diff: ${finalResponse.usage.outputTokens - finalResponse.estimatedOutputTokens})")

            Result.success(finalResponse)

        } catch (e: Exception) {
            logger.error("Exception in ClaudeProvider: ${e.message}", e)
            Result.failure(AIError.fromException(e))
        }
    }

    override suspend fun getModels(): Result<List<AIModel>> {
        // Claude не предоставляет endpoint для списка моделей
        // Возвращаем встроенный список доступных моделей
        val models = listOf(
            AIModel(
                id = "claude-haiku-4-5-20251001",
                name = "Claude Haiku 4.5",
                providerId = ProviderType.CLAUDE,
                capabilities = ModelCapabilities(
                    supportsVision = false,
                    supportsStreaming = true,
                    maxTokens = 16*1000,
                    contextWindow = 200*1000,
                    temperatureMin = 0.0,
                    temperatureMax = 1.0,
                    defaultTemperature = 1.0
                )
            ),
            AIModel(
                id = "claude-sonnet-4-5-20250929",
                name = "Claude Sonnet 4.5",
                providerId = ProviderType.CLAUDE,
                capabilities = ModelCapabilities(
                    supportsVision = false,
                    supportsStreaming = true,
                    maxTokens = 64*1000,
                    contextWindow = 200*1000,
                    temperatureMin = 0.0,
                    temperatureMax = 1.0,
                    defaultTemperature = 1.0
                )
            ),
            AIModel(
                id = "claude-opus-4-1-20250805",
                name = "Claude Opus 4.1",
                providerId = ProviderType.CLAUDE,
                capabilities = ModelCapabilities(
                    supportsVision = false,
                    supportsStreaming = true,
                    maxTokens = 16*1000,
                    contextWindow = 200*1000,
                    temperatureMin = 0.0,
                    temperatureMax = 1.0,
                    defaultTemperature = 1.0
                )
            ),
            AIModel(
                id = "claude-opus-4-20250514",
                name = "Claude Opus 4",
                providerId = ProviderType.CLAUDE,
                capabilities = ModelCapabilities(
                    supportsVision = false,
                    supportsStreaming = true,
                    maxTokens = 16*1000,
                    contextWindow = 200*1000,
                    temperatureMin = 0.0,
                    temperatureMax = 1.0,
                    defaultTemperature = 1.0
                )
            ),
            AIModel(
                id = "claude-sonnet-4-20250514",
                name = "Claude Sonnet 4",
                providerId = ProviderType.CLAUDE,
                capabilities = ModelCapabilities(
                    supportsVision = false,
                    supportsStreaming = true,
                    maxTokens = 16*1000,
                    contextWindow = 200*1000,
                    temperatureMin = 0.0,
                    temperatureMax = 1.0,
                    defaultTemperature = 1.0
                )
            ),
            AIModel(
                id = "claude-3-7-sonnet-20250219",
                name = "Claude Sonnet 3.7",
                providerId = ProviderType.CLAUDE,
                capabilities = ModelCapabilities(
                    supportsVision = true,
                    supportsStreaming = true,
                    maxTokens = 8*1000,
                    contextWindow = 200*1000,
                    temperatureMin = 0.0,
                    temperatureMax = 1.0,
                    defaultTemperature = 1.0
                )
            ),
            AIModel(
                id = "claude-3-5-haiku-20241022",
                name = "Claude Haiku 3.5",
                providerId = ProviderType.CLAUDE,
                capabilities = ModelCapabilities(
                    supportsVision = false,
                    supportsStreaming = true,
                    maxTokens = 8*1000,
                    contextWindow = 200*1000,
                    temperatureMin = 0.0,
                    temperatureMax = 1.0,
                    defaultTemperature = 1.0
                )
            ),
            AIModel(
                id = "claude-3-haiku-20240307",
                name = "Claude Haiku 3",
                providerId = ProviderType.CLAUDE,
                capabilities = ModelCapabilities(
                    supportsVision = true,
                    supportsStreaming = true,
                    maxTokens = 4*1000,
                    contextWindow = 200*1000,
                    temperatureMin = 0.0,
                    temperatureMax = 1.0,
                    defaultTemperature = 1.0
                )
            )
        )
        return Result.success(models)
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
