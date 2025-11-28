package com.researchai.data.provider.openai

import com.researchai.domain.models.*
import com.researchai.domain.provider.AIModel
import com.researchai.domain.provider.AIProvider
import com.researchai.domain.provider.ModelCapabilities
import com.researchai.domain.tokenizer.TokenCounter
import com.researchai.domain.utils.RetryUtils
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
    override val config: ProviderConfig.OpenAIConfig,
    private val tokenCounter: TokenCounter
) : AIProvider {

    override val providerId: ProviderType = ProviderType.OPENAI

    private val logger = LoggerFactory.getLogger(OpenAIProvider::class.java)
    private val mapper = OpenAIMapper()

    override suspend fun sendMessage(request: AIRequest): Result<AIResponse> {
        return try {
            logger.info("OpenAI Provider: Sending message")

            // Подсчёт входных токенов локально
            val estimatedInputTokens = tokenCounter.countTokensWithFormatting(
                request.messages,
                request.systemPrompt
            )
            logger.info("Estimated input tokens: $estimatedInputTokens")

            // Маппинг domain модели в OpenAI API модель
            val openAIRequest = mapper.toOpenAIRequest(request, config)

            // Retry config: retry on 429 (rate limit), 529 (overloaded) and network errors
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
                    header("Authorization", "Bearer ${config.apiKey}")
                    config.organization?.let { header("OpenAI-Organization", it) }
                    config.projectId?.let { header("OpenAI-Project", it) }
                    header("Content-Type", "application/json")
                    setBody(openAIRequest)
                }

                logger.info("OpenAI API response status: ${httpResponse.status}")

                // Обработка ошибок 429 (Rate Limit) и 529 (Overloaded) - retry
                if (httpResponse.status.value == 429 || httpResponse.status.value == 529) {
                    val errorBody = httpResponse.bodyAsText()
                    throw java.io.IOException("OpenAI API rate limited or overloaded: $errorBody")
                }

                // Обработка других ошибок - не retry
                if (!httpResponse.status.isSuccess()) {
                    val errorBody = httpResponse.bodyAsText()
                    logger.error("OpenAI API error response: $errorBody")

                    val errorResponse = try {
                        Json.decodeFromString<OpenAIApiError>(errorBody)
                    } catch (e: Exception) {
                        null
                    }

                    throw AIError.NetworkException(
                        errorResponse?.error?.message ?: "OpenAI API Error (${httpResponse.status}): $errorBody"
                    )
                }

                val openAIResponse: OpenAIApiResponse = httpResponse.body()

                // Маппинг обратно в domain модель
                val aiResponse = mapper.fromOpenAIResponse(openAIResponse)

                // Подсчёт выходных токенов локально (приблизительная оценка)
                val estimatedOutputTokens = tokenCounter.countTokens(aiResponse.content)

                // Добавляем локально подсчитанные токены
                aiResponse.copy(
                    estimatedInputTokens = estimatedInputTokens,
                    estimatedOutputTokens = estimatedOutputTokens
                )
            }

            logger.info("Successfully received response from OpenAI API")
            logger.info("Actual tokens - Input: ${finalResponse.usage.inputTokens}, Output: ${finalResponse.usage.outputTokens}")
            logger.info("Estimated tokens - Input: $estimatedInputTokens (diff: ${finalResponse.usage.inputTokens - estimatedInputTokens}), Output: ${finalResponse.estimatedOutputTokens} (diff: ${finalResponse.usage.outputTokens - finalResponse.estimatedOutputTokens})")

            Result.success(finalResponse)

        } catch (e: Exception) {
            logger.error("Exception in OpenAIProvider: ${e.message}", e)
            Result.failure(AIError.fromException(e))
        }
    }

    override suspend fun getModels(): Result<List<AIModel>> {
        // OpenAI возвращает встроенный список доступных моделей
        val models = listOf(
            AIModel(
                id = "gpt-5-nano",
                name = "gpt-5-nano",
                providerId = ProviderType.OPENAI,
                capabilities = ModelCapabilities(
                    supportsVision = false,
                    supportsStreaming = true,
                    maxTokens = 128*1000,
                    contextWindow = 400*1000
                )
            ),
            AIModel(
                id = "gpt-5-mini",
                name = "gpt-5-mini",
                providerId = ProviderType.OPENAI,
                capabilities = ModelCapabilities(
                    supportsVision = false,
                    supportsStreaming = true,
                    maxTokens = 128*1000,
                    contextWindow = 400*1000
                )
            ),
            AIModel(
                id = "gpt-5",
                name = "gpt-5",
                providerId = ProviderType.OPENAI,
                capabilities = ModelCapabilities(
                    supportsVision = true,
                    supportsStreaming = true,
                    maxTokens = 128*1000,
                    contextWindow = 400*1000
                )
            ),
            AIModel(
                id = "gpt-5-pro",
                name = "gpt-5-pro",
                providerId = ProviderType.OPENAI,
                capabilities = ModelCapabilities(
                    supportsVision = true,
                    supportsStreaming = true,
                    maxTokens = 272*1000,
                    contextWindow = 400*1000
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
        if (!config.apiKey.startsWith("sk-")) {
            errors.add("Invalid API key format (should start with 'sk-')")
        }

        return if (errors.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(errors)
        }
    }
}
