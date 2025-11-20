package com.researchai.persistence

import com.researchai.models.Assistant

/**
 * Интерфейс для хранилища ассистентов
 */
interface AssistantStorage {
    /**
     * Сохраняет ассистента
     */
    suspend fun saveAssistant(assistant: Assistant): Result<Unit>

    /**
     * Загружает ассистента по ID
     */
    suspend fun loadAssistant(assistantId: String): Result<Assistant?>

    /**
     * Загружает всех ассистентов
     */
    suspend fun loadAllAssistants(): Result<List<Assistant>>

    /**
     * Удаляет ассистента
     */
    suspend fun deleteAssistant(assistantId: String): Result<Unit>

    /**
     * Проверяет существование ассистента
     */
    suspend fun assistantExists(assistantId: String): Boolean

    /**
     * Закрывает ресурсы хранилища
     */
    suspend fun close()
}
