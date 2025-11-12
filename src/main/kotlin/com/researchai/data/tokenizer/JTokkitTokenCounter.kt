package com.researchai.data.tokenizer

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.EncodingRegistry
import com.knuddels.jtokkit.api.EncodingType
import com.researchai.domain.models.Message
import com.researchai.domain.models.MessageContent
import com.researchai.domain.models.MessageRole
import com.researchai.domain.tokenizer.TokenCounter

/**
 * Реализация TokenCounter с использованием JTokkit
 * Использует cl100k_base encoding, который подходит для:
 * - GPT-4, GPT-3.5-turbo (OpenAI)
 * - Claude моделей (приблизительная оценка)
 * - HuggingFace моделей (приблизительная оценка)
 */
class JTokkitTokenCounter(
    private val modelName: String = "gpt-4"
) : TokenCounter {

    private val registry: EncodingRegistry = Encodings.newDefaultEncodingRegistry()
    private val encoding: Encoding = getEncodingForModel(modelName)

    /**
     * Определяет encoding на основе модели
     */
    private fun getEncodingForModel(model: String): Encoding {
        return when {
            // GPT-4 и GPT-3.5-turbo используют cl100k_base
            model.startsWith("gpt-4") || model.startsWith("gpt-3.5") ->
                registry.getEncoding(EncodingType.CL100K_BASE)

            // GPT-5 использует o200k_base
            model.startsWith("gpt-5") || model.startsWith("o1") ->
                try {
                    registry.getEncoding(EncodingType.O200K_BASE)
                } catch (e: Exception) {
                    // Fallback если O200K_BASE недоступен
                    registry.getEncoding(EncodingType.CL100K_BASE)
                }

            // Для Claude и других моделей используем cl100k_base как приблизительную оценку
            model.contains("claude", ignoreCase = true) ->
                registry.getEncoding(EncodingType.CL100K_BASE)

            // Для HuggingFace и других используем cl100k_base
            else -> registry.getEncoding(EncodingType.CL100K_BASE)
        }
    }

    override fun countTokens(text: String): Int {
        if (text.isEmpty()) return 0
        return encoding.countTokens(text)
    }

    override fun countTokens(messages: List<Message>): Int {
        return messages.sumOf { message ->
            val text = when (val content = message.content) {
                is MessageContent.Text -> content.text
                is MessageContent.MultiModal -> content.text ?: ""
            }
            countTokens(text)
        }
    }

    override fun countTokensWithFormatting(messages: List<Message>, systemPrompt: String?): Int {
        // Базовая оценка для форматирования сообщений в API запросе
        // Каждое сообщение добавляет ~4 токена для метаданных (role, content структура)
        val formattingOverhead = messages.size * 4

        // Считаем токены в системном промпте
        val systemTokens = systemPrompt?.let { countTokens(it) + 4 } ?: 0

        // Считаем токены в сообщениях
        val messageTokens = countTokens(messages)

        // Добавляем ~3 токена для обёртки всего запроса
        return systemTokens + messageTokens + formattingOverhead + 3
    }

    /**
     * Подсчитывает токены для конкретного текста с указанием модели
     */
    fun countTokensForModel(text: String, model: String): Int {
        if (text.isEmpty()) return 0
        val modelEncoding = getEncodingForModel(model)
        return modelEncoding.countTokens(text)
    }

    companion object {
        /**
         * Создаёт TokenCounter для указанной модели
         */
        fun forModel(modelName: String): JTokkitTokenCounter {
            return JTokkitTokenCounter(modelName)
        }
    }
}
