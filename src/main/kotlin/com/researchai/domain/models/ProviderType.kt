package com.researchai.domain.models

/**
 * Типы AI-провайдеров
 */
enum class ProviderType(val id: String, val displayName: String) {
    CLAUDE("claude", "Anthropic Claude"),
    OPENAI("openai", "OpenAI"),
    GEMINI("gemini", "Google Gemini"),
    CUSTOM("custom", "Custom Provider");

    companion object {
        fun fromId(id: String): ProviderType? {
            return values().find { it.id == id }
        }
    }
}
