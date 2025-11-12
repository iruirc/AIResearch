package com.researchai.models

import kotlinx.serialization.Serializable

@Serializable
data class ChatResponse(
    val response: String,
    val sessionId: String,
    val tokensUsed: Int? = null,
    // Детальная информация о токенах
    val tokenDetails: TokenDetails? = null
)

@Serializable
data class TokenDetails(
    // API токены (реальные)
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,
    // Локальные токены (оценочные)
    val estimatedInputTokens: Int = 0,
    val estimatedOutputTokens: Int = 0,
    val estimatedTotalTokens: Int = 0
)
