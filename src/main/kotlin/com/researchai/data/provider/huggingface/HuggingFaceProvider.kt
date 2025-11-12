package com.researchai.data.provider.huggingface

import com.researchai.domain.models.*
import com.researchai.domain.provider.AIModel
import com.researchai.domain.provider.AIProvider
import com.researchai.domain.provider.ModelCapabilities
import com.researchai.domain.tokenizer.TokenCounter
import com.researchai.models.AvailableHuggingFaceModels
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Реализация провайдера для HuggingFace API
 * HuggingFace использует OpenAI-совместимый Chat Completions API
 */
class HuggingFaceProvider(
    private val httpClient: HttpClient,
    override val config: ProviderConfig.HuggingFaceConfig,
    private val tokenCounter: TokenCounter
) : AIProvider {

    override val providerId: ProviderType = ProviderType.HUGGINGFACE

    private val logger = LoggerFactory.getLogger(HuggingFaceProvider::class.java)
    private val mapper = HuggingFaceMapper()

    override suspend fun sendMessage(request: AIRequest): Result<AIResponse> {
        return try {
            logger.info("HuggingFace Provider: Sending message")

            // Подсчёт входных токенов локально
            val estimatedInputTokens = tokenCounter.countTokensWithFormatting(
                request.messages,
                request.systemPrompt
            )
            logger.info("Estimated input tokens: $estimatedInputTokens")

            // Маппинг domain модели в HuggingFace API модель
            val hfRequest = mapper.toHuggingFaceRequest(request, config)

            // HTTP запрос
            val httpResponse: HttpResponse = httpClient.post(config.baseUrl) {
                header("Authorization", "Bearer ${config.apiKey}")
                header("Content-Type", "application/json")
                setBody(hfRequest)
            }

            logger.info("HuggingFace API response status: ${httpResponse.status}")

            // Обработка ошибок
            if (!httpResponse.status.isSuccess()) {
                val errorBody = httpResponse.bodyAsText()
                logger.error("HuggingFace API error response: $errorBody")

                return try {
                    val errorResponse: HuggingFaceApiError = Json.decodeFromString(errorBody)
                    Result.failure(
                        AIError.NetworkException("HuggingFace API Error: ${errorResponse.error.message}")
                    )
                } catch (e: Exception) {
                    Result.failure(
                        AIError.NetworkException("HuggingFace API Error (${httpResponse.status}): $errorBody")
                    )
                }
            }

            val hfResponse: HuggingFaceApiResponse = httpResponse.body()

            // Маппинг обратно в domain модель (с обработкой reasoning)
            val aiResponse = mapper.fromHuggingFaceResponse(hfResponse)

            // Подсчёт выходных токенов локально (приблизительная оценка)
            val estimatedOutputTokens = tokenCounter.countTokens(aiResponse.content)

            // Добавляем локально подсчитанные токены
            val finalResponse = aiResponse.copy(
                estimatedInputTokens = estimatedInputTokens,
                estimatedOutputTokens = estimatedOutputTokens
            )

            logger.info("Successfully received response from HuggingFace API")
            logger.info("Actual tokens - Input: ${finalResponse.usage.inputTokens}, Output: ${finalResponse.usage.outputTokens}")
            logger.info("Estimated tokens - Input: $estimatedInputTokens (diff: ${finalResponse.usage.inputTokens - estimatedInputTokens}), Output: $estimatedOutputTokens (diff: ${finalResponse.usage.outputTokens - estimatedOutputTokens})")

            Result.success(finalResponse)

        } catch (e: Exception) {
            logger.error("Exception in HuggingFaceProvider: ${e.message}", e)
            Result.failure(AIError.NetworkException("HuggingFace API error", e))
        }
    }

    override suspend fun getModels(): Result<List<AIModel>> {
        // HuggingFace возвращает предустановленный список популярных моделей из AvailableHuggingFaceModels
        val models = AvailableHuggingFaceModels.models.map { llmModel ->
            AIModel(
                id = llmModel.id,
                name = llmModel.displayName,
                providerId = ProviderType.HUGGINGFACE,
                capabilities = when (llmModel.id) {
                    "deepseek-ai/DeepSeek-R1:fastest", "deepseek-ai/DeepSeek-R1" -> ModelCapabilities(
                        supportsVision = false,
                        supportsStreaming = true,
                        maxTokens = 8192,
                        contextWindow = 128000
                    )
                    "meta-llama/Llama-3.3-70B-Instruct" -> ModelCapabilities(
                        supportsVision = false,
                        supportsStreaming = true,
                        maxTokens = 8192,
                        contextWindow = 128000
                    )
                    "Qwen/Qwen2.5-72B-Instruct" -> ModelCapabilities(
                        supportsVision = false,
                        supportsStreaming = true,
                        maxTokens = 8192,
                        contextWindow = 32768
                    )
                    "meta-llama/Llama-3.2-3B-Instruct" -> ModelCapabilities(
                        supportsVision = false,
                        supportsStreaming = true,
                        maxTokens = 2048,
                        contextWindow = 128000
                    )
                    else -> ModelCapabilities(
                        supportsVision = false,
                        supportsStreaming = true,
                        maxTokens = 4096,
                        contextWindow = 32768
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
        if (!config.apiKey.startsWith("hf_")) {
            errors.add("Invalid API key format (should start with 'hf_')")
        }

        return if (errors.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(errors)
        }
    }
}
