package com.researchai.domain.compression

import com.researchai.domain.models.CompressionConfig
import com.researchai.domain.models.Message
import com.researchai.domain.models.MessageContent
import com.researchai.domain.models.MessageRole

/**
 * Полная замена всех сообщений на одну суммаризацию.
 * Максимальное сжатие, но потеря детализации.
 *
 * Алгоритм:
 * 1. Берем все сообщения диалога
 * 2. Генерируем суммаризацию через AI
 * 3. Заменяем все сообщения на одно системное с суммаризацией
 * 4. Архивируем оригинальные сообщения
 */
class FullReplacementCompression : CompressionAlgorithm {

    override fun shouldCompress(
        messages: List<Message>,
        config: CompressionConfig,
        contextWindowSize: Int?
    ): Boolean {
        return messages.size >= config.fullReplacementMessageThreshold
    }

    override suspend fun compress(
        messages: List<Message>,
        config: CompressionConfig,
        summarize: suspend (List<Message>) -> String
    ): CompressionResult {
        if (messages.isEmpty()) {
            return CompressionResult(
                newMessages = emptyList(),
                archivedMessages = emptyList(),
                summaryGenerated = false,
                originalMessageCount = 0,
                newMessageCount = 0,
                compressionRatio = 0.0
            )
        }

        // Генерируем суммаризацию всех сообщений
        val summary = summarize(messages)

        // Создаем новое системное сообщение с суммаризацией
        val summaryMessage = Message(
            role = MessageRole.SYSTEM,
            content = MessageContent.Text(
                """
                |=== СЖАТАЯ ИСТОРИЯ ДИАЛОГА ===
                |
                |$summary
                |
                |=== КОНЕЦ СЖАТОЙ ИСТОРИИ ===
                """.trimMargin()
            )
        )

        val newMessages = listOf(summaryMessage)

        return CompressionResult(
            newMessages = newMessages,
            archivedMessages = messages,
            summaryGenerated = true,
            originalMessageCount = messages.size,
            newMessageCount = newMessages.size,
            compressionRatio = 1.0 - (newMessages.size.toDouble() / messages.size)
        )
    }
}
