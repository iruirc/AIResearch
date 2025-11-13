package com.researchai.domain.models

import kotlinx.serialization.Serializable

/**
 * Стратегия сжатия диалога
 */
@Serializable
enum class CompressionStrategy {
    /**
     * Полная замена всех сообщений на одну суммаризацию.
     * Максимальное сжатие, но потеря детализации.
     * Триггер: по количеству сообщений (по умолчанию >= 10)
     */
    FULL_REPLACEMENT,

    /**
     * Скользящее окно: суммаризация старых сообщений, сохранение последних N.
     * Простая реализация, подходит для большинства случаев.
     * Триггер: по количеству сообщений (по умолчанию >= 12, оставляем последние 6)
     */
    SLIDING_WINDOW,

    /**
     * Адаптивное сжатие по токенам: сжимаем когда достигаем лимита токенов.
     * Более гибкий подход, учитывает размер сообщений.
     * Триггер: по количеству токенов (по умолчанию >= 80% от context window)
     */
    TOKEN_BASED
}

/**
 * Настройки сжатия для каждой стратегии
 */
@Serializable
data class CompressionConfig(
    val strategy: CompressionStrategy = CompressionStrategy.FULL_REPLACEMENT,

    // Настройки для FULL_REPLACEMENT
    val fullReplacementMessageThreshold: Int = 10,

    // Настройки для SLIDING_WINDOW
    val slidingWindowMessageThreshold: Int = 12,
    val slidingWindowKeepLast: Int = 6,

    // Настройки для TOKEN_BASED
    val tokenBasedThresholdPercent: Double = 0.8, // 80% от context window
    val tokenBasedKeepPercent: Double = 0.4 // Оставляем 40% последних сообщений
)
