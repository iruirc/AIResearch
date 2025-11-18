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
    val usage: TokenUsage, // Токены от API провайдера (реальные)
    val finishReason: FinishReason,
    val metadata: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
    val estimatedInputTokens: Int = 0, // Локально подсчитанные входные токены
    val estimatedOutputTokens: Int = 0, // Локально подсчитанные выходные токены
    val toolUses: List<ToolUse> = emptyList() // Tool uses requested by the AI (if any)
)

@Serializable
data class TokenUsage(
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int = inputTokens + outputTokens
)

@Serializable
enum class FinishReason {
    STOP, MAX_TOKENS, CONTENT_FILTER, ERROR, CANCELLED, TOOL_USE
}

/**
 * Represents a tool use request from the AI model
 */
@Serializable
data class ToolUse(
    val id: String,
    val name: String,
    val input: kotlinx.serialization.json.JsonElement
)
