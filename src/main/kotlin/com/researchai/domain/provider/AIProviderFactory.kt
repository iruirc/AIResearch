package com.researchai.domain.provider

import com.researchai.domain.models.ProviderConfig
import com.researchai.domain.models.ProviderType

/**
 * Фабрика для создания AI-провайдеров
 */
interface AIProviderFactory {
    /**
     * Создание провайдера по типу и конфигурации
     */
    fun create(type: ProviderType, config: ProviderConfig): AIProvider

    /**
     * Регистрация кастомного провайдера
     */
    fun register(type: ProviderType, creator: (ProviderConfig) -> AIProvider)
}
