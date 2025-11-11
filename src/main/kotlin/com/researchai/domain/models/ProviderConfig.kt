package com.researchai.domain.models

import kotlinx.serialization.Serializable

/**
 * Конфигурация провайдеров
 */
sealed class ProviderConfig {
    abstract val apiKey: String
    abstract val baseUrl: String
    abstract val timeout: TimeoutConfig

    data class ClaudeConfig(
        override val apiKey: String,
        override val baseUrl: String = "https://api.anthropic.com/v1/messages",
        val apiVersion: String = "2023-06-01",
        override val timeout: TimeoutConfig = TimeoutConfig(),
        val defaultModel: String = "claude-sonnet-4-5-20250929"
    ) : ProviderConfig()

    data class OpenAIConfig(
        override val apiKey: String,
        override val baseUrl: String = "https://api.openai.com/v1/chat/completions",
        val organization: String? = null,
        val projectId: String? = null,
        override val timeout: TimeoutConfig = TimeoutConfig(),
        val defaultModel: String = "gpt-4-turbo"
    ) : ProviderConfig()

    data class GeminiConfig(
        override val apiKey: String,
        override val baseUrl: String = "https://generativelanguage.googleapis.com/v1",
        override val timeout: TimeoutConfig = TimeoutConfig(),
        val defaultModel: String = "gemini-pro"
    ) : ProviderConfig()

    data class HuggingFaceConfig(
        override val apiKey: String,
        override val baseUrl: String = "https://router.huggingface.co/v1/chat/completions",
        override val timeout: TimeoutConfig = TimeoutConfig(),
        val defaultModel: String = "deepseek-ai/DeepSeek-R1:fastest"
    ) : ProviderConfig()

    data class CustomConfig(
        override val apiKey: String,
        override val baseUrl: String,
        val headers: Map<String, String> = emptyMap(),
        override val timeout: TimeoutConfig = TimeoutConfig()
    ) : ProviderConfig()
}

@Serializable
data class TimeoutConfig(
    val connectTimeoutMs: Long = 10_000,
    val readTimeoutMs: Long = 300_000,
    val writeTimeoutMs: Long = 300_000
)
