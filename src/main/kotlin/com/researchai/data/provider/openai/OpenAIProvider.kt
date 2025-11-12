package com.researchai.data.provider.openai

import com.researchai.domain.models.*
import com.researchai.domain.provider.AIModel
import com.researchai.domain.provider.AIProvider
import com.researchai.domain.provider.ModelCapabilities
import com.researchai.domain.tokenizer.TokenCounter
import com.researchai.models.AvailableOpenAIModels
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

            // HTTP запрос
            val httpResponse: HttpResponse = httpClient.post(config.baseUrl) {
                header("Authorization", "Bearer ${config.apiKey}")
                config.organization?.let { header("OpenAI-Organization", it) }
                config.projectId?.let { header("OpenAI-Project", it) }
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

            // Подсчёт выходных токенов локально (приблизительная оценка)
            val estimatedOutputTokens = tokenCounter.countTokens(aiResponse.content)

            // Добавляем локально подсчитанные токены
            val finalResponse = aiResponse.copy(
                estimatedInputTokens = estimatedInputTokens,
                estimatedOutputTokens = estimatedOutputTokens
            )

            logger.info("Successfully received response from OpenAI API")
            logger.info("Actual tokens - Input: ${finalResponse.usage.inputTokens}, Output: ${finalResponse.usage.outputTokens}")
            logger.info("Estimated tokens - Input: $estimatedInputTokens (diff: ${finalResponse.usage.inputTokens - estimatedInputTokens}), Output: $estimatedOutputTokens (diff: ${finalResponse.usage.outputTokens - estimatedOutputTokens})")

            Result.success(finalResponse)

        } catch (e: Exception) {
            logger.error("Exception in OpenAIProvider: ${e.message}", e)
            Result.failure(AIError.NetworkException("OpenAI API error", e))
        }
    }

    override suspend fun getModels(): Result<List<AIModel>> {
        // OpenAI возвращает список моделей из AvailableOpenAIModels
        val models = AvailableOpenAIModels.models.map { llmModel ->
            AIModel(
                id = llmModel.id,
                name = llmModel.displayName,
                providerId = ProviderType.OPENAI,
                capabilities = when (llmModel.id) {
                    "gpt-5-nano" -> ModelCapabilities(
                        supportsVision = false,
                        supportsStreaming = true,
                        maxTokens = 4096,
                        contextWindow = 128000
                    )
                    "gpt-5-mini" -> ModelCapabilities(
                        supportsVision = false,
                        supportsStreaming = true,
                        maxTokens = 8192,
                        contextWindow = 128000
                    )
                    "gpt-5" -> ModelCapabilities(
                        supportsVision = true,
                        supportsStreaming = true,
                        maxTokens = 16384,
                        contextWindow = 200000
                    )
                    "gpt-5-pro" -> ModelCapabilities(
                        supportsVision = true,
                        supportsStreaming = true,
                        maxTokens = 32768,
                        contextWindow = 200000
                    )
                    else -> ModelCapabilities(
                        supportsVision = false,
                        supportsStreaming = true,
                        maxTokens = 4096,
                        contextWindow = 128000
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
