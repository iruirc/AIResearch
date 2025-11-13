package com.researchai.domain.compression

import com.researchai.domain.models.CompressionConfig
import com.researchai.domain.models.Message
import com.researchai.domain.models.MessageContent
import com.researchai.domain.models.MessageRole

/**
 * Адаптивное сжатие по токенам: сжимаем когда достигаем лимита токенов.
 * Более гибкий подход, учитывает размер сообщений.
 *
 * Алгоритм:
 * 1. Подсчитываем общее количество токенов в диалоге
 * 2. Если превышен порог (процент от context window), начинаем сжатие
 * 3. Определяем, сколько последних сообщений оставить (по проценту токенов)
 * 4. Сжимаем остальные сообщения в суммаризацию
 * 5. Формируем новый список сообщений
 */
class TokenBasedCompression : CompressionAlgorithm {

    override fun shouldCompress(
        messages: List<Message>,
        config: CompressionConfig,
        contextWindowSize: Int?
    ): Boolean {
        if (contextWindowSize == null || messages.isEmpty()) {
            return false
        }

        val totalTokens = calculateTotalTokens(messages)
        val threshold = (contextWindowSize * config.tokenBasedThresholdPercent).toInt()

        return totalTokens >= threshold
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

        val totalTokens = calculateTotalTokens(messages)

        // Определяем, сколько токенов оставить (процент от общего)
        val tokensToKeep = (totalTokens * config.tokenBasedKeepPercent).toInt()

        // Находим последние сообщения, которые в сумме дают нужное количество токенов
        val (messagesToCompress, messagesToKeep) = splitMessagesByTokens(messages, tokensToKeep)

        if (messagesToCompress.isEmpty()) {
            // Если нечего сжимать, возвращаем исходные сообщения
            return CompressionResult(
                newMessages = messages,
                archivedMessages = emptyList(),
                summaryGenerated = false,
                originalMessageCount = messages.size,
                newMessageCount = messages.size,
                compressionRatio = 0.0
            )
        }

        // Генерируем суммаризацию старых сообщений
        val summary = summarize(messagesToCompress)

        // Новый список сообщений: только последние сообщения
        // Суммаризация будет добавлена отдельно в ChatCompressionService
        val newMessages = messagesToKeep

        return CompressionResult(
            newMessages = newMessages,
            archivedMessages = messagesToCompress,
            summaryGenerated = true,
            originalMessageCount = messages.size,
            newMessageCount = messagesToKeep.size + 1, // +1 для сообщения суммаризации
            compressionRatio = 1.0 - ((messagesToKeep.size + 1).toDouble() / messages.size)
        )
    }

    /**
     * Подсчитывает общее количество токенов в списке сообщений
     */
    private fun calculateTotalTokens(messages: List<Message>): Int {
        return messages.sumOf { message ->
            message.metadata?.totalTokens ?: message.metadata?.estimatedTotalTokens ?: 0
        }
    }

    /**
     * Разделяет сообщения на две части: для сжатия и для сохранения
     * @param messages Список сообщений
     * @param tokensToKeep Количество токенов, которое нужно оставить
     * @return Pair(messagesToCompress, messagesToKeep)
     */
    private fun splitMessagesByTokens(
        messages: List<Message>,
        tokensToKeep: Int
    ): Pair<List<Message>, List<Message>> {
        var accumulatedTokens = 0
        var splitIndex = messages.size

        // Идем с конца, накапливая токены
        for (i in messages.indices.reversed()) {
            val messageTokens = messages[i].metadata?.totalTokens
                ?: messages[i].metadata?.estimatedTotalTokens
                ?: 0

            if (accumulatedTokens + messageTokens > tokensToKeep) {
                splitIndex = i + 1
                break
            }

            accumulatedTokens += messageTokens
        }

        // Убеждаемся, что хотя бы одно сообщение остается
        if (splitIndex >= messages.size) {
            splitIndex = messages.size - 1
        }

        val messagesToCompress = messages.subList(0, splitIndex)
        val messagesToKeep = messages.subList(splitIndex, messages.size)

        return Pair(messagesToCompress, messagesToKeep)
    }
}
