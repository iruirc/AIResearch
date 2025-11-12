package com.researchai.data.provider.claude

import com.researchai.domain.models.*
import com.researchai.domain.provider.AIModel
import com.researchai.domain.provider.AIProvider
import com.researchai.domain.provider.ModelCapabilities
import com.researchai.domain.tokenizer.TokenCounter
import com.researchai.models.AvailableClaudeModels
import com.researchai.models.LLMModel as LegacyLLMModel
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

            // Подсчёт выходных токенов локально (приблизительная оценка)
            val estimatedOutputTokens = tokenCounter.countTokens(aiResponse.content)

            // Добавляем локально подсчитанные токены
            val finalResponse = aiResponse.copy(
                estimatedInputTokens = estimatedInputTokens,
                estimatedOutputTokens = estimatedOutputTokens
            )

            logger.info("Successfully received response from Claude API")
            logger.info("Actual tokens - Input: ${finalResponse.usage.inputTokens}, Output: ${finalResponse.usage.outputTokens}")
            logger.info("Estimated tokens - Input: $estimatedInputTokens (diff: ${finalResponse.usage.inputTokens - estimatedInputTokens}), Output: $estimatedOutputTokens (diff: ${finalResponse.usage.outputTokens - estimatedOutputTokens})")

            Result.success(finalResponse)

        } catch (e: Exception) {
            logger.error("Exception in ClaudeProvider: ${e.message}", e)
            Result.failure(AIError.NetworkException("Claude API error", e))
        }
    }

    override suspend fun getModels(): Result<List<AIModel>> {
        // Claude не предоставляет endpoint для списка моделей
        // Возвращаем список из AvailableClaudeModels
        val models = AvailableClaudeModels.models.map { llmModel ->
            AIModel(
                id = llmModel.id,
                name = llmModel.displayName,
                providerId = ProviderType.CLAUDE,
                capabilities = when (llmModel.id) {
                    "claude-haiku-4-5-20251001" -> ModelCapabilities(
                        supportsVision = false,
                        supportsStreaming = true,
                        maxTokens = 16*1024,
                        contextWindow = 200*1024
                    )
                    "claude-sonnet-4-5-20250929" -> ModelCapabilities(
                        supportsVision = false,
                        supportsStreaming = true,
                        maxTokens = 64*1024,
                        contextWindow = 200*1024
                    )
                    "claude-opus-4-1-20250805" -> ModelCapabilities(
                        supportsVision = false,
                        supportsStreaming = true,
                        maxTokens = 16*1024,
                        contextWindow = 200*1024
                    )
                    "claude-opus-4-20250514" -> ModelCapabilities(
                        supportsVision = false,
                        supportsStreaming = true,
                        maxTokens = 16*1024,
                        contextWindow = 200*1024
                    )
                    "claude-sonnet-4-20250514" -> ModelCapabilities(
                        supportsVision = false,
                        supportsStreaming = true,
                        maxTokens = 16*1024,
                        contextWindow = 200*1024
                    )
                    "claude-3-7-sonnet-20250219" -> ModelCapabilities(
                        supportsVision = true,
                        supportsStreaming = true,
                        maxTokens = 8*1024,
                        contextWindow = 200*1024
                    )
                    "claude-3-5-haiku-20241022" -> ModelCapabilities(
                        supportsVision = false,
                        supportsStreaming = true,
                        maxTokens = 8*1024,
                        contextWindow = 200*1024
                    )
                    "claude-3-haiku-20240307" -> ModelCapabilities(
                        supportsVision = true,
                        supportsStreaming = true,
                        maxTokens = 4*1024,
                        contextWindow = 200*1024
                    )
                    else -> ModelCapabilities(
                        supportsVision = false,
                        supportsStreaming = true,
                        maxTokens = 4*1024,
                        contextWindow = 200*1024
                    )
                }
            )
        }
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
