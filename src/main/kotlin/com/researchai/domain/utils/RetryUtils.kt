package com.researchai.domain.utils

import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import kotlin.math.pow

/**
 * Утилита для выполнения операций с автоматическими повторными попытками
 */
object RetryUtils {
    private val logger = LoggerFactory.getLogger(RetryUtils::class.java)

    /**
     * Конфигурация retry логики
     */
    data class RetryConfig(
        val maxRetries: Int = 3,
        val baseDelayMs: Long = 1000L,
        val maxDelayMs: Long = 10000L,
        val shouldRetry: (Exception) -> Boolean = { true }
    )

    /**
     * Выполняет операцию с автоматическими повторными попытками при ошибках
     *
     * @param config конфигурация retry логики
     * @param operation операция для выполнения
     * @return результат операции
     * @throws Exception если все попытки исчерпаны
     */
    suspend fun <T> withRetry(
        config: RetryConfig = RetryConfig(),
        operation: suspend () -> T
    ): T {
        var lastException: Exception? = null

        repeat(config.maxRetries) { attempt ->
            try {
                return operation()
            } catch (e: Exception) {
                lastException = e

                // Проверяем, нужно ли повторять для этого типа ошибки
                if (!config.shouldRetry(e)) {
                    logger.debug("Exception not retryable: ${e::class.simpleName}")
                    throw e
                }

                if (attempt < config.maxRetries - 1) {
                    // Exponential backoff with max limit
                    val delayMs = minOf(
                        config.baseDelayMs * (2.0.pow(attempt.toDouble())).toLong(),
                        config.maxDelayMs
                    )

                    logger.warn(
                        "Operation failed (attempt ${attempt + 1}/${config.maxRetries}): ${e.message}. " +
                        "Retrying in ${delayMs}ms..."
                    )
                    delay(delayMs)
                } else {
                    logger.error("Operation failed after ${config.maxRetries} attempts: ${e.message}")
                }
            }
        }

        // Все попытки исчерпаны
        throw lastException ?: IllegalStateException("Operation failed without exception")
    }
}
