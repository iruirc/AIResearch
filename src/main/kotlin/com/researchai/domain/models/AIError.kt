package com.researchai.domain.models

/**
 * Ошибки AI SDK
 */
sealed class AIError : Exception() {
    data class NetworkException(override val message: String, override val cause: Throwable? = null) : AIError()
    data class ConfigurationException(override val message: String) : AIError()
    data class UnsupportedProviderException(override val message: String) : AIError()
    data class ParseException(override val message: String) : AIError()
    data class DatabaseException(override val message: String, override val cause: Throwable? = null) : AIError()
    data class NotFoundException(override val message: String) : AIError()
    data class UnsupportedOperationException(override val message: String) : AIError()
    data class ValidationException(override val message: String, val errors: List<String>) : AIError()

    companion object {
        fun fromException(e: Exception): AIError {
            return when (e) {
                is AIError -> e
                else -> NetworkException("Unknown error: ${e.message}", e)
            }
        }
    }
}

/**
 * Результат валидации
 */
sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val errors: List<String>) : ValidationResult()
}
