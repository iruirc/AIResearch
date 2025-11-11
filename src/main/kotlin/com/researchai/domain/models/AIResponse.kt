package com.researchai.domain.models

import kotlinx.serialization.Serializable

/**
 * Универсальная модель ответа от AI-провайдера
 */
@Serializable
data class AIResponse(
    val id: String,
    val content: String,
    val role: MessageRole = MessageRole.ASSISTANT,
    val model: String,
    val usage: TokenUsage,
    val finishReason: FinishReason,
    val metadata: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class TokenUsage(
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int = inputTokens + outputTokens
)

@Serializable
enum class FinishReason {
    STOP, MAX_TOKENS, CONTENT_FILTER, ERROR, CANCELLED
}
