package com.researchai.models

import kotlinx.serialization.Serializable

/**
 * Информация о модели Claude
 */
@Serializable
data class ClaudeModel(
    val id: String,
    val displayName: String,
    val createdAt: String
)

/**
 * Список доступных моделей Claude
 */
object AvailableModels {
    val models = listOf(
        ClaudeModel(
            id = "claude-haiku-4-5-20251001",
            displayName = "Claude Haiku 4.5",
            createdAt = "2025-10-15T00:00:00Z"
        ),
        ClaudeModel(
            id = "claude-sonnet-4-5-20250929",
            displayName = "Claude Sonnet 4.5",
            createdAt = "2025-09-29T00:00:00Z"
        ),
        ClaudeModel(
            id = "claude-opus-4-1-20250805",
            displayName = "Claude Opus 4.1",
            createdAt = "2025-08-05T00:00:00Z"
        ),
        ClaudeModel(
            id = "claude-opus-4-20250514",
            displayName = "Claude Opus 4",
            createdAt = "2025-05-22T00:00:00Z"
        ),
        ClaudeModel(
            id = "claude-sonnet-4-20250514",
            displayName = "Claude Sonnet 4",
            createdAt = "2025-05-22T00:00:00Z"
        ),
        ClaudeModel(
            id = "claude-3-7-sonnet-20250219",
            displayName = "Claude Sonnet 3.7",
            createdAt = "2025-02-24T00:00:00Z"
        ),
        ClaudeModel(
            id = "claude-3-5-haiku-20241022",
            displayName = "Claude Haiku 3.5",
            createdAt = "2024-10-22T00:00:00Z"
        ),
        ClaudeModel(
            id = "claude-3-haiku-20240307",
            displayName = "Claude Haiku 3",
            createdAt = "2024-03-07T00:00:00Z"
        )
    )

    /**
     * Модель по умолчанию (Claude Haiku 4.5)
     */
    const val DEFAULT_MODEL = "claude-haiku-4-5-20251001"

    /**
     * Проверяет, доступна ли модель
     */
    fun isValidModel(modelId: String): Boolean {
        return models.any { it.id == modelId }
    }
}

/**
 * Ответ со списком моделей
 */
@Serializable
data class ModelsListResponse(
    val models: List<ClaudeModel>
)
