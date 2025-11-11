package com.researchai.domain.repository

import com.researchai.domain.models.ProviderConfig
import com.researchai.domain.models.ProviderType

/**
 * Репозиторий для управления конфигурациями провайдеров
 */
interface ConfigRepository {
    /**
     * Сохранение конфигурации провайдера
     */
    suspend fun saveProviderConfig(providerType: ProviderType, config: ProviderConfig): Result<Unit>

    /**
     * Получение конфигурации провайдера
     */
    suspend fun getProviderConfig(providerType: ProviderType): Result<ProviderConfig?>

    /**
     * Удаление конфигурации провайдера
     */
    suspend fun deleteProviderConfig(providerType: ProviderType): Result<Unit>

    /**
     * Получение всех конфигураций
     */
    suspend fun getAllConfigs(): Result<Map<ProviderType, ProviderConfig>>
}
