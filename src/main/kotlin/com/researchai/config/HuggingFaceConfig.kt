package com.researchai.config

/**
 * Конфигурация для HuggingFace API
 */
data class HuggingFaceConfig(
    val apiKey: String,
    val baseUrl: String = "https://router.huggingface.co/v1/chat/completions",
    val model: String = "deepseek-ai/DeepSeek-R1:fastest",
    val maxTokens: Int = 8192,
    val temperature: Double = 1.0
)

/**
 * Получение конфигурации HuggingFace из переменных окружения
 *
 * Переменные окружения:
 * - HUGGINGFACE_API_KEY (обязательно для использования HuggingFace)
 * - HUGGINGFACE_MODEL (опционально, по умолчанию: deepseek-ai/DeepSeek-R1:fastest)
 * - HUGGINGFACE_MAX_TOKENS (опционально, по умолчанию: 8192)
 * - HUGGINGFACE_TEMPERATURE (опционально, по умолчанию: 1.0)
 *
 * @return HuggingFaceConfig или null если HUGGINGFACE_API_KEY не задан
 */
fun getHuggingFaceConfig(): HuggingFaceConfig? {
    val apiKey = System.getenv("HUGGINGFACE_API_KEY") ?: System.getProperty("HUGGINGFACE_API_KEY")
        ?: return null // HuggingFace - опциональный провайдер

    return HuggingFaceConfig(
        apiKey = apiKey,
        model = System.getenv("HUGGINGFACE_MODEL") ?: System.getProperty("HUGGINGFACE_MODEL") ?: "deepseek-ai/DeepSeek-R1:fastest",
        maxTokens = (System.getenv("HUGGINGFACE_MAX_TOKENS") ?: System.getProperty("HUGGINGFACE_MAX_TOKENS"))?.toIntOrNull() ?: 8192,
        temperature = (System.getenv("HUGGINGFACE_TEMPERATURE") ?: System.getProperty("HUGGINGFACE_TEMPERATURE"))?.toDoubleOrNull() ?: 1.0
    )
}
