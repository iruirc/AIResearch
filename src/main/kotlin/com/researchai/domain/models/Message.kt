package com.researchai.domain.models

import kotlinx.serialization.Serializable

/**
 * Универсальная модель сообщения
 */
@Serializable
data class Message(
    val role: MessageRole,
    val content: MessageContent,
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: MessageMetadata? = null
)

/**
 * Метаданные сообщения (для ответов AI)
 */
@Serializable
data class MessageMetadata(
    val model: String,
    val tokensUsed: Int, // Deprecated: используйте totalTokens
    val responseTime: Double, // в секундах

    // Токены от API провайдера (реальные)
    val inputTokens: Int = 0, // Входные токены от API
    val outputTokens: Int = 0, // Выходные токены от API
    val totalTokens: Int = inputTokens + outputTokens, // Общее от API

    // Токены, подсчитанные локально (оценочные)
    val estimatedInputTokens: Int = 0, // Локальная оценка входных токенов
    val estimatedOutputTokens: Int = 0, // Локальная оценка выходных токенов
    val estimatedTotalTokens: Int = estimatedInputTokens + estimatedOutputTokens // Локальная оценка общих
)

@Serializable
enum class MessageRole {
    USER, ASSISTANT, SYSTEM
}

@Serializable
sealed class MessageContent {
    @Serializable
    data class Text(val text: String) : MessageContent()

    @Serializable
    data class MultiModal(
        val text: String? = null,
        val images: List<ImageContent> = emptyList()
    ) : MessageContent()
}

@Serializable
data class ImageContent(
    val data: String, // Base64 encoded
    val mimeType: String,
    val source: ImageSource
)

@Serializable
enum class ImageSource {
    BASE64, URL, LOCAL_FILE
}
