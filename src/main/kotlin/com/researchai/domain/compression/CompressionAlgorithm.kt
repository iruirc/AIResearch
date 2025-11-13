package com.researchai.domain.compression

import com.researchai.domain.models.CompressionConfig
import com.researchai.domain.models.Message

/**
 * Результат сжатия диалога
 */
data class CompressionResult(
    val newMessages: List<Message>, // Новый список сообщений (после сжатия)
    val archivedMessages: List<Message>, // Сообщения, которые были сжаты и должны быть архивированы
    val summaryGenerated: Boolean, // Была ли создана суммаризация
    val originalMessageCount: Int, // Исходное количество сообщений
    val newMessageCount: Int, // Новое количество сообщений
    val compressionRatio: Double // Коэффициент сжатия (например, 0.5 = сжато на 50%)
)

/**
 * Интерфейс для алгоритма сжатия диалога
 */
interface CompressionAlgorithm {
    /**
     * Проверяет, нужно ли сжатие для данного списка сообщений
     * @param messages Текущие сообщения диалога
     * @param config Конфигурация сжатия
     * @param contextWindowSize Размер контекстного окна модели (в токенах), опционально
     * @return true, если нужно сжатие
     */
    fun shouldCompress(
        messages: List<Message>,
        config: CompressionConfig,
        contextWindowSize: Int? = null
    ): Boolean

    /**
     * Выполняет сжатие диалога
     * @param messages Текущие сообщения диалога
     * @param config Конфигурация сжатия
     * @param summarize Функция для генерации суммаризации через AI
     * @return Результат сжатия
     */
    suspend fun compress(
        messages: List<Message>,
        config: CompressionConfig,
        summarize: suspend (List<Message>) -> String
    ): CompressionResult
}
