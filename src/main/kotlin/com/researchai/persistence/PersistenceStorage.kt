package com.researchai.persistence

import com.researchai.models.ChatSession

/**
 * Интерфейс для хранилища сессий
 */
interface PersistenceStorage {
    /**
     * Сохраняет сессию
     */
    suspend fun saveSession(session: ChatSession): Result<Unit>

    /**
     * Загружает сессию по ID
     */
    suspend fun loadSession(sessionId: String): Result<ChatSession?>

    /**
     * Загружает все сессии
     */
    suspend fun loadAllSessions(): Result<List<ChatSession>>

    /**
     * Удаляет сессию
     */
    suspend fun deleteSession(sessionId: String): Result<Unit>

    /**
     * Проверяет существование сессии
     */
    suspend fun sessionExists(sessionId: String): Boolean

    /**
     * Закрывает ресурсы хранилища
     */
    suspend fun close()
}
