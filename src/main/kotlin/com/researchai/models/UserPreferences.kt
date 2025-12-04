package com.researchai.models

import com.researchai.domain.models.OllamaConnection
import kotlinx.serialization.Serializable

/**
 * User preferences for default model settings
 * Persisted to disk and loaded on application startup
 */
@Serializable
data class UserPreferences(
    val providerId: String = "claude",
    val model: String = "claude-haiku-4-5-20251001",
    val temperature: Double = 1.0,
    val maxTokens: Int = 4096,
    val format: ResponseFormat = ResponseFormat.PLAIN_TEXT,
    val ollamaConnections: List<OllamaConnection> = emptyList(),
    val activeOllamaConnectionId: String? = null,
    val toolChoice: String = "auto", // "auto", "required", "none"
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * Default preferences matching application defaults
         */
        fun default() = UserPreferences()
    }
}
