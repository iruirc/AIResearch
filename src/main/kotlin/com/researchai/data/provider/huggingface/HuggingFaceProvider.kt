package com.researchai.data.provider.huggingface

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
 * Реализация провайдера для HuggingFace API
 * HuggingFace использует OpenAI-совместимый Chat Completions API
 */
class HuggingFaceProvider(
    private val httpClient: HttpClient,
    override val config: ProviderConfig.HuggingFaceConfig
) : AIProvider {

    override val providerId: ProviderType = ProviderType.HUGGINGFACE

    private val logger = LoggerFactory.getLogger(HuggingFaceProvider::class.java)
    private val mapper = HuggingFaceMapper()

    override suspend fun sendMessage(request: AIRequest): Result<AIResponse> {
        return try {
            logger.info("HuggingFace Provider: Sending message")

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

            logger.info("Successfully received response from HuggingFace API")
            Result.success(aiResponse)

        } catch (e: Exception) {
            logger.error("Exception in HuggingFaceProvider: ${e.message}", e)
            Result.failure(AIError.NetworkException("HuggingFace API error", e))
        }
    }

    override suspend fun getModels(): Result<List<AIModel>> {
        // HuggingFace возвращает предустановленный список популярных моделей
        val models = listOf(
            AIModel(
                id = "deepseek-ai/DeepSeek-R1:fastest",
                name = "DeepSeek R1 (Fastest)",
                providerId = ProviderType.HUGGINGFACE,
                capabilities = ModelCapabilities(
                    supportsVision = false,
                    supportsStreaming = true,
                    maxTokens = 8192,
                    contextWindow = 128000
                )
            ),
            AIModel(
                id = "deepseek-ai/DeepSeek-R1",
                name = "DeepSeek R1",
                providerId = ProviderType.HUGGINGFACE,
                capabilities = ModelCapabilities(
                    supportsVision = false,
                    supportsStreaming = true,
                    maxTokens = 8192,
                    contextWindow = 128000
                )
            ),
            AIModel(
                id = "meta-llama/Llama-3.3-70B-Instruct",
                name = "Llama 3.3 70B Instruct",
                providerId = ProviderType.HUGGINGFACE,
                capabilities = ModelCapabilities(
                    supportsVision = false,
                    supportsStreaming = true,
                    maxTokens = 8192,
                    contextWindow = 128000
                )
            ),
            AIModel(
                id = "Qwen/Qwen2.5-72B-Instruct",
                name = "Qwen 2.5 72B Instruct",
                providerId = ProviderType.HUGGINGFACE,
                capabilities = ModelCapabilities(
                    supportsVision = false,
                    supportsStreaming = true,
                    maxTokens = 8192,
                    contextWindow = 32768
                )
            ),
            AIModel(
                id = "nvidia/Llama-3.1-Nemotron-70B-Instruct-HF",
                name = "Llama 3.1 Nemotron 70B Instruct",
                providerId = ProviderType.HUGGINGFACE,
                capabilities = ModelCapabilities(
                    supportsVision = false,
                    supportsStreaming = true,
                    maxTokens = 8192,
                    contextWindow = 128000
                )
            ),
            AIModel(
                id = "mistralai/Mistral-7B-Instruct-v0.3",
                name = "Mistral 7B Instruct v0.3",
                providerId = ProviderType.HUGGINGFACE,
                capabilities = ModelCapabilities(
                    supportsVision = false,
                    supportsStreaming = true,
                    maxTokens = 4096,
                    contextWindow = 32768
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
