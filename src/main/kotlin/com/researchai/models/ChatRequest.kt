package com.researchai.models

import kotlinx.serialization.Serializable

@Serializable
data class ChatRequest(
    val message: String,
    val format: ResponseFormat = ResponseFormat.PLAIN_TEXT,
    val sessionId: String? = null,
    val model: String? = null, // ID модели Claude для использования
    val temperature: Double? = null, // Температура для генерации ответа (0.0 - 1.0)
    val maxTokens: Int? = null // Максимальное количество токенов в ответе
)

@Serializable
data class ConfigResponse(
    val model: String,
    val temperature: Double,
    val maxTokens: Int,
    val format: String
)
