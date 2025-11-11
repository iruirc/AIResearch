package com.researchai.domain.usecase

import com.researchai.domain.models.AIError
import com.researchai.domain.models.ProviderType
import com.researchai.domain.provider.AIModel
import com.researchai.domain.provider.AIProviderFactory
import com.researchai.domain.repository.ConfigRepository
import org.slf4j.LoggerFactory

/**
 * Use case для получения списка доступных моделей
 */
class GetModelsUseCase(
    private val providerFactory: AIProviderFactory,
    private val configRepository: ConfigRepository
) {
    private val logger = LoggerFactory.getLogger(GetModelsUseCase::class.java)

    suspend operator fun invoke(providerId: ProviderType): Result<List<AIModel>> {
        return try {
            logger.info("GetModelsUseCase: Getting models for provider $providerId")

            // Получаем конфигурацию провайдера
            val config = configRepository.getProviderConfig(providerId)
                .getOrNull() ?: return Result.failure(
                    AIError.ConfigurationException("Provider $providerId not configured")
                )

            // Создаем провайдера
            val provider = providerFactory.create(providerId, config)

            // Получаем модели
            val models = provider.getModels().getOrThrow()

            logger.info("Retrieved ${models.size} models for provider $providerId")

            Result.success(models)
        } catch (e: Exception) {
            logger.error("Error in GetModelsUseCase: ${e.message}", e)
            Result.failure(AIError.fromException(e as? Exception ?: Exception(e.message)))
        }
    }
}
