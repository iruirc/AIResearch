package com.researchai.config

/**
 * Конфигурация для Ollama (Local LLM Server)
 *
 * Ollama - локальный сервер для запуска LLM моделей без API ключей.
 * Поддерживает множественные подключения через UserPreferences.
 */
data class OllamaConfig(
    val defaultBaseUrl: String = "http://localhost:11434",
    val defaultKeepAlive: String = "5m",
    val defaultModel: String = "llama3.2"
)

/**
 * Получение конфигурации Ollama из переменных окружения
 *
 * Переменные окружения:
 * - OLLAMA_BASE_URL (опционально, по умолчанию: http://localhost:11434)
 * - OLLAMA_KEEP_ALIVE (опционально, по умолчанию: 5m)
 * - OLLAMA_MODEL (опционально, по умолчанию: llama3.2)
 *
 * Примечание: Ollama всегда считается настроенным (не требует API ключа).
 * Реальные подключения управляются через OllamaConnectionManager и UserPreferences.
 *
 * @return OllamaConfig с дефолтными значениями из env или константами
 */
fun getOllamaConfig(): OllamaConfig {
    return OllamaConfig(
        defaultBaseUrl = System.getenv("OLLAMA_BASE_URL") ?: System.getProperty("OLLAMA_BASE_URL") ?: "http://localhost:11434",
        defaultKeepAlive = System.getenv("OLLAMA_KEEP_ALIVE") ?: System.getProperty("OLLAMA_KEEP_ALIVE") ?: "5m",
        defaultModel = System.getenv("OLLAMA_MODEL") ?: System.getProperty("OLLAMA_MODEL") ?: "llama3.2"
    )
}
