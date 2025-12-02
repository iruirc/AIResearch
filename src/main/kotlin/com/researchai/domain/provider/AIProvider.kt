package com.researchai.domain.provider

import com.researchai.domain.models.*
import kotlinx.serialization.Serializable

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
@Serializable
data class AIModel(
    val id: String,
    val name: String,
    val providerId: ProviderType,
    val capabilities: ModelCapabilities = ModelCapabilities()
)

/**
 * Возможности модели
 */
@Serializable
data class ModelCapabilities(
    val supportsVision: Boolean = false,
    val supportsStreaming: Boolean = true,
    val maxTokens: Int = 4096,
    val contextWindow: Int = 8192,

    // Temperature constraints
    val temperatureMin: Double = 0.0,
    val temperatureMax: Double = 1.0,
    val defaultTemperature: Double = 1.0
)

/**
 * Ответ со списком моделей для API
 */
@Serializable
data class ModelsResponse(
    val models: List<AIModel>
)
