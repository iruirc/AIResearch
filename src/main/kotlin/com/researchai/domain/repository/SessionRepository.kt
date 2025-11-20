package com.researchai.domain.repository

import com.researchai.domain.models.Message
import com.researchai.domain.models.ProviderType

/**
 * Репозиторий для управления сессиями чата
 */
interface SessionRepository {
    /**
     * Создание новой сессии
     */
    suspend fun createSession(
        providerId: ProviderType,
        assistantId: String? = null,
        metadata: Map<String, String> = emptyMap()
    ): Result<ChatSession>

    /**
     * Получение сессии по ID
     */
    suspend fun getSession(sessionId: String): Result<ChatSession>

    /**
     * Получение всех сессий
     */
    suspend fun getAllSessions(): Result<List<ChatSession>>

    /**
     * Обновление сессии
     */
    suspend fun updateSession(session: ChatSession): Result<Unit>

    /**
     * Удаление сессии
     */
    suspend fun deleteSession(sessionId: String): Result<Unit>

    /**
     * Добавление сообщения в сессию
     */
    suspend fun addMessage(sessionId: String, message: Message): Result<Unit>

    /**
     * Получение истории сообщений сессии
     */
    suspend fun getMessages(sessionId: String): Result<List<Message>>

    /**
     * Очистка истории сообщений
     */
    suspend fun clearMessages(sessionId: String): Result<Unit>
}

/**
 * Модель сессии чата
 */
data class ChatSession(
    val id: String,
    val providerId: ProviderType,
    val assistantId: String? = null,
    val messages: List<Message> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap(),
    val pipelineExecutionId: String? = null, // ID выполнения pipeline
    val currentPipelineStep: Int? = null // Текущий шаг в pipeline
)
