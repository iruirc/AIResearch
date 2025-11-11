package com.researchai.domain.provider

import com.researchai.domain.models.*

/**
 * Базовый интерфейс для всех AI-провайдеров
 * Реализует Strategy Pattern для взаимозаменяемости провайдеров
 */
interface AIProvider {
    val providerId: ProviderType
    val config: ProviderConfig

    /**
     * Отправка сообщения
     */
    suspend fun sendMessage(request: AIRequest): Result<AIResponse>

    /**
     * Получение списка доступных моделей
     */
    suspend fun getModels(): Result<List<AIModel>>

    /**
     * Валидация конфигурации провайдера
     */
    fun validateConfig(): ValidationResult
}

/**
 * Информация о модели AI
 */
data class AIModel(
    val id: String,
    val name: String,
    val providerId: ProviderType,
    val capabilities: ModelCapabilities = ModelCapabilities()
)

/**
 * Возможности модели
 */
data class ModelCapabilities(
    val supportsVision: Boolean = false,
    val supportsStreaming: Boolean = true,
    val maxTokens: Int = 4096,
    val contextWindow: Int = 8192
)
