package com.researchai.domain.models

import kotlinx.serialization.Serializable

/**
 * Универсальная модель сообщения
 */
@Serializable
data class Message(
    val role: MessageRole,
    val content: MessageContent,
    val timestamp: Long = System.currentTimeMillis()
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
