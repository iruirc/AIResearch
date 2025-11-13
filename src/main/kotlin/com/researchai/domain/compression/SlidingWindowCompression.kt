package com.researchai.domain.compression

import com.researchai.domain.models.CompressionConfig
import com.researchai.domain.models.Message
import com.researchai.domain.models.MessageContent
import com.researchai.domain.models.MessageRole

/**
 * Скользящее окно: суммаризация старых сообщений, сохранение последних N.
 * Простая реализация, подходит для большинства случаев.
 *
 * Алгоритм:
 * 1. Проверяем количество сообщений
 * 2. Если превышен порог, берем старые сообщения (все кроме последних N)
 * 3. Генерируем суммаризацию старых сообщений
 * 4. Создаем новый список: [системное сообщение с суммаризацией] + [последние N сообщений]
 * 5. Архивируем старые сообщения
 */
class SlidingWindowCompression : CompressionAlgorithm {

    override fun shouldCompress(
        messages: List<Message>,
        config: CompressionConfig,
        contextWindowSize: Int?
    ): Boolean {
        return messages.size >= config.slidingWindowMessageThreshold
    }

    override suspend fun compress(
        messages: List<Message>,
        config: CompressionConfig,
        summarize: suspend (List<Message>) -> String
    ): CompressionResult {
        if (messages.isEmpty() || messages.size <= config.slidingWindowKeepLast) {
            return CompressionResult(
                newMessages = messages,
                archivedMessages = emptyList(),
                summaryGenerated = false,
                originalMessageCount = messages.size,
                newMessageCount = messages.size,
                compressionRatio = 0.0
            )
        }

        // Разделяем на старые и последние сообщения
        val messagesToCompress = messages.dropLast(config.slidingWindowKeepLast)
        val messagesToKeep = messages.takeLast(config.slidingWindowKeepLast)

        // Генерируем суммаризацию старых сообщений
        val summary = summarize(messagesToCompress)

        // Создаем системное сообщение с контекстом
        val contextMessage = Message(
            role = MessageRole.SYSTEM,
            content = MessageContent.Text(
                """
                |=== КОНТЕКСТ ПРЕДЫДУЩЕЙ БЕСЕДЫ ===
                |
                |Ниже представлена краткая суммаризация предыдущих ${messagesToCompress.size} сообщений диалога.
                |Используйте этот контекст для понимания текущей беседы.
                |
                |$summary
                |
                |=== КОНЕЦ КОНТЕКСТА ===
                |
                |Далее следуют последние ${messagesToKeep.size} сообщений диалога в полном виде.
                """.trimMargin()
            )
        )

        // Новый список сообщений: контекст + последние сообщения
        val newMessages = listOf(contextMessage) + messagesToKeep

        return CompressionResult(
            newMessages = newMessages,
            archivedMessages = messagesToCompress,
            summaryGenerated = true,
            originalMessageCount = messages.size,
            newMessageCount = newMessages.size,
            compressionRatio = 1.0 - (newMessages.size.toDouble() / messages.size)
        )
    }
}
