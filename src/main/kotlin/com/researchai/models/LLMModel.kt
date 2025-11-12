package com.researchai.models

import kotlinx.serialization.Serializable

/**
 * Информация о модели LLM (Language Model)
 */
@Serializable
data class LLMModel(
    val id: String,
    val displayName: String,
    val createdAt: String
)

/**
 * Список доступных моделей Claude
 */
object AvailableClaudeModels {
    val models = listOf(
        LLMModel(
            id = "claude-haiku-4-5-20251001",
            displayName = "Claude Haiku 4.5",
            createdAt = "2025-10-15T00:00:00Z"
        ),
        LLMModel(
            id = "claude-sonnet-4-5-20250929",
            displayName = "Claude Sonnet 4.5",
            createdAt = "2025-09-29T00:00:00Z"
        ),
        LLMModel(
            id = "claude-opus-4-1-20250805",
            displayName = "Claude Opus 4.1",
            createdAt = "2025-08-05T00:00:00Z"
        ),
        LLMModel(
            id = "claude-opus-4-20250514",
            displayName = "Claude Opus 4",
            createdAt = "2025-05-22T00:00:00Z"
        ),
        LLMModel(
            id = "claude-sonnet-4-20250514",
            displayName = "Claude Sonnet 4",
            createdAt = "2025-05-22T00:00:00Z"
        ),
        LLMModel(
            id = "claude-3-7-sonnet-20250219",
            displayName = "Claude Sonnet 3.7",
            createdAt = "2025-02-24T00:00:00Z"
        ),
        LLMModel(
            id = "claude-3-5-haiku-20241022",
            displayName = "Claude Haiku 3.5",
            createdAt = "2024-10-22T00:00:00Z"
        ),
        LLMModel(
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
 * Список доступных моделей OpenAI
 */
object AvailableOpenAIModels {
    val models = listOf(
        LLMModel(
            id = "gpt-5-nano",
            displayName = "gpt-5-nano",
            createdAt = "2025-08-05T11:33:04Z"
        ),
        LLMModel(
            id = "gpt-5-mini",
            displayName = "gpt-5-mini",
            createdAt = "2025-08-05T11:25:28Z"
        ),
        LLMModel(
            id = "gpt-5",
            displayName = "gpt-5",
            createdAt = "2025-08-05T11:22:57Z"
        ),
        LLMModel(
            id = "gpt-5-pro",
            displayName = "gpt-5-pro",
            createdAt = "2025-10-03T07:03:42Z"
        )
    )

    /**
     * Модель по умолчанию (GPT-5)
     */
    const val DEFAULT_MODEL = "gpt-5"

    /**
     * Проверяет, доступна ли модель
     */
    fun isValidModel(modelId: String): Boolean {
        return models.any { it.id == modelId }
    }
}

/**
 * Список доступных моделей HuggingFace
 */
object AvailableHuggingFaceModels {
    val models = listOf(
        LLMModel(
            id = "deepseek-ai/DeepSeek-R1:fastest",
            displayName = "DeepSeek R1 (Fastest)",
            createdAt = "2025-01-20T00:00:00Z"
        ),
        LLMModel(
            id = "deepseek-ai/DeepSeek-R1",
            displayName = "DeepSeek R1",
            createdAt = "2025-01-20T00:00:00Z"
        ),
        LLMModel(
            id = "meta-llama/Llama-3.3-70B-Instruct",
            displayName = "Llama 3.3 70B Instruct",
            createdAt = "2024-12-06T00:00:00Z"
        ),
        LLMModel(
            id = "Qwen/Qwen2.5-72B-Instruct",
            displayName = "Qwen 2.5 72B Instruct",
            createdAt = "2024-09-19T00:00:00Z"
        ),
        LLMModel(
            id = "meta-llama/Llama-3.2-3B-Instruct",
            displayName = "Llama 3.2 3B Instruct",
            createdAt = "2024-09-25T00:00:00Z"
        )
    )

    /**
     * Модель по умолчанию (DeepSeek R1 Fastest)
     */
    const val DEFAULT_MODEL = "deepseek-ai/DeepSeek-R1:fastest"

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
    val models: List<LLMModel>
)
