package com.researchai.auth.service

/**
 * Сервис для управления whitelist разрешенных email адресов
 */
class WhitelistService(
    private val allowedEmails: Set<String>
) {
    /**
     * Проверить, разрешен ли email адрес
     */
    fun isEmailAllowed(email: String): Boolean {
        // Если whitelist пуст, разрешаем всех (backward compatibility)
        if (allowedEmails.isEmpty()) {
            return true
        }

        // Проверяем точное совпадение (case-insensitive)
        return allowedEmails.any { it.equals(email, ignoreCase = true) }
    }

    /**
     * Получить список разрешенных email адресов
     */
    fun getAllowedEmails(): Set<String> = allowedEmails

    /**
     * Проверить, включен ли whitelist (есть ли ограничения)
     */
    fun isWhitelistEnabled(): Boolean = allowedEmails.isNotEmpty()

    companion object {
        /**
         * Создать WhitelistService из строки с email адресами, разделенными запятыми
         */
        fun fromCommaSeparatedString(emailsString: String?): WhitelistService {
            if (emailsString.isNullOrBlank()) {
                return WhitelistService(emptySet())
            }

            val emails = emailsString
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()

            return WhitelistService(emails)
        }
    }
}
