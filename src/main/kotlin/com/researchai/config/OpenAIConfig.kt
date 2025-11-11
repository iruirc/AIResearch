package com.researchai.config

/**
 * Конфигурация для OpenAI API
 */
data class OpenAIConfig(
    val apiKey: String,
    val baseUrl: String = "https://api.openai.com/v1/chat/completions",
    val organizationId: String? = null,
    val projectId: String? = null,
    val model: String = "gpt-4-turbo",
    val maxTokens: Int = 4096,
    val temperature: Double = 1.0
)

/**
 * Получение конфигурации OpenAI из переменных окружения
 *
 * Переменные окружения:
 * - OPENAI_API_KEY (обязательно)
 * - OPENAI_ORGANIZATION_ID (опционально)
 * - OPENAI_PROJECT_ID (опционально)
 * - OPENAI_MODEL (опционально, по умолчанию: gpt-4-turbo)
 * - OPENAI_MAX_TOKENS (опционально, по умолчанию: 4096)
 * - OPENAI_TEMPERATURE (опционально, по умолчанию: 1.0)
 *
 * @return OpenAIConfig или null если OPENAI_API_KEY не задан
 */
fun getOpenAIConfig(): OpenAIConfig? {
    val apiKey = System.getenv("OPENAI_API_KEY") ?: System.getProperty("OPENAI_API_KEY")
        ?: return null // OpenAI - опциональный провайдер

    return OpenAIConfig(
        apiKey = apiKey,
        organizationId = System.getenv("OPENAI_ORGANIZATION_ID") ?: System.getProperty("OPENAI_ORGANIZATION_ID"),
        projectId = System.getenv("OPENAI_PROJECT_ID") ?: System.getProperty("OPENAI_PROJECT_ID"),
        model = System.getenv("OPENAI_MODEL") ?: System.getProperty("OPENAI_MODEL") ?: "gpt-4-turbo",
        maxTokens = (System.getenv("OPENAI_MAX_TOKENS") ?: System.getProperty("OPENAI_MAX_TOKENS"))?.toIntOrNull() ?: 4096,
        temperature = (System.getenv("OPENAI_TEMPERATURE") ?: System.getProperty("OPENAI_TEMPERATURE"))?.toDoubleOrNull() ?: 1.0
    )
}
