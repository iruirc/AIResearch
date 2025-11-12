package com.researchai.domain.tokenizer

import com.researchai.domain.models.Message

/**
 * Интерфейс для подсчёта токенов в тексте
 */
interface TokenCounter {
    /**
     * Подсчитывает количество токенов в тексте
     * @param text Текст для подсчёта
     * @return Количество токенов
     */
    fun countTokens(text: String): Int

    /**
     * Подсчитывает количество токенов в списке сообщений
     * @param messages Список сообщений
     * @return Количество токенов
     */
    fun countTokens(messages: List<Message>): Int

    /**
     * Подсчитывает количество токенов в сообщении с учётом форматирования
     * @param messages Список сообщений
     * @param systemPrompt Системный промпт (если есть)
     * @return Приблизительное количество токенов
     */
    fun countTokensWithFormatting(messages: List<Message>, systemPrompt: String? = null): Int
}
