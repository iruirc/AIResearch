package com.researchai.domain.models

import kotlinx.serialization.Serializable

/**
 * Универсальная модель запроса к AI-провайдеру
 */
@Serializable
data class AIRequest(
    val messages: List<Message>,
    val model: String,
    val parameters: RequestParameters = RequestParameters(),
    val systemPrompt: String? = null,
    val sessionId: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class RequestParameters(
    val temperature: Double = 1.0,
    val maxTokens: Int = 4096,
    val topP: Double = 1.0,
    val topK: Int? = null,
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null,
    val stopSequences: List<String> = emptyList(),
    val responseFormat: com.researchai.models.ResponseFormat = com.researchai.models.ResponseFormat.PLAIN_TEXT,
    val streamingEnabled: Boolean = false
)
