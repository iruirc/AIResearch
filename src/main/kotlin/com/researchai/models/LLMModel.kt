package com.researchai.models

import kotlinx.serialization.Serializable

/**
 * Информация о модели LLM (Language Model) для legacy API
 * Это упрощенное представление модели, используемое только в /models endpoint
 */
@Serializable
data class LLMModel(
    val id: String,
    val displayName: String,
    val createdAt: String
)

/**
 * Ответ со списком моделей для legacy API
 */
@Serializable
data class ModelsListResponse(
    val models: List<LLMModel>
)
