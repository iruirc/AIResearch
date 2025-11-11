package com.researchai.data.repository

import com.researchai.config.ClaudeConfig
import com.researchai.domain.models.AIError
import com.researchai.domain.models.ProviderConfig
import com.researchai.domain.models.ProviderType
import com.researchai.domain.repository.ConfigRepository

/**
 * Реализация репозитория конфигураций
 * Использует существующую ClaudeConfig и in-memory хранилище для других провайдеров
 */
class ConfigRepositoryImpl(
    private val claudeConfig: ClaudeConfig
) : ConfigRepository {

    // In-memory хранилище для других провайдеров
    private val configs = mutableMapOf<ProviderType, ProviderConfig>()

    init {
        // Инициализируем Claude конфигурацию из существующей
        configs[ProviderType.CLAUDE] = ProviderConfig.ClaudeConfig(
            apiKey = claudeConfig.apiKey,
            baseUrl = claudeConfig.apiUrl,
            apiVersion = claudeConfig.apiVersion,
            defaultModel = claudeConfig.model
        )
    }

    override suspend fun saveProviderConfig(
        providerType: ProviderType,
        config: ProviderConfig
    ): Result<Unit> {
        return try {
            configs[providerType] = config
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(AIError.DatabaseException("Failed to save config", e))
        }
    }

    override suspend fun getProviderConfig(providerType: ProviderType): Result<ProviderConfig?> {
        return try {
            val config = configs[providerType]
            Result.success(config)
        } catch (e: Exception) {
            Result.failure(AIError.DatabaseException("Failed to get config", e))
        }
    }

    override suspend fun deleteProviderConfig(providerType: ProviderType): Result<Unit> {
        return try {
            if (providerType == ProviderType.CLAUDE) {
                return Result.failure(
                    AIError.ValidationException(
                        "Cannot delete Claude config",
                        listOf("Claude is the default provider")
                    )
                )
            }
            configs.remove(providerType)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(AIError.DatabaseException("Failed to delete config", e))
        }
    }

    override suspend fun getAllConfigs(): Result<Map<ProviderType, ProviderConfig>> {
        return try {
            Result.success(configs.toMap())
        } catch (e: Exception) {
            Result.failure(AIError.DatabaseException("Failed to get all configs", e))
        }
    }
}
