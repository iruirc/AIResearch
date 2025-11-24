package com.researchai.domain.models

import kotlinx.serialization.Serializable

/**
 * Типы AI-провайдеров
 */
@Serializable
enum class ProviderType(val id: String, val displayName: String) {
    CLAUDE("claude", "Anthropic Claude"),
    OPENAI("openai", "OpenAI"),
    HUGGINGFACE("huggingface", "HuggingFace"),
    OLLAMA("ollama", "Ollama (Local)");

    companion object {
        fun fromId(id: String): ProviderType? {
            return values().find { it.id == id }
        }
    }
}
