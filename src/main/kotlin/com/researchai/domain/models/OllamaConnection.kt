package com.researchai.domain.models

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Конфигурация подключения к Ollama серверу
 */
@Serializable
data class OllamaConnection(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val baseUrl: String,
    val keepAlive: String = "5m",
    val isDefault: Boolean = false
) {
    fun validate(): List<String> {
        val errors = mutableListOf<String>()

        if (name.isBlank()) {
            errors.add("Connection name cannot be empty")
        }
        if (baseUrl.isBlank()) {
            errors.add("Base URL cannot be empty")
        }
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            errors.add("Base URL must start with http:// or https://")
        }

        return errors
    }
}
