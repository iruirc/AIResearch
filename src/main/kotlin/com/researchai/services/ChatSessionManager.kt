package com.researchai.services

import com.researchai.models.ChatSession
import com.researchai.models.MessageRole
import com.researchai.persistence.PersistenceManager
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Менеджер для управления сессиями чата.
 * Хранит все активные сессии в памяти с автоматическим сохранением на диск.
 */
class ChatSessionManager(
    private val persistenceManager: PersistenceManager? = null
) {
    private val logger = LoggerFactory.getLogger(ChatSessionManager::class.java)
    private val sessions = ConcurrentHashMap<String, ChatSession>()

    /**
     * Загружает сессии из хранилища при инициализации
     */
    init {
        if (persistenceManager != null) {
            runBlocking {
                persistenceManager.loadAllSessions().onSuccess { loadedSessions ->
                    loadedSessions.forEach { session ->
                        sessions[session.id] = session
                    }
                    logger.info("Loaded ${loadedSessions.size} sessions from persistence storage")
                }.onFailure { e ->
                    logger.error("Failed to load sessions from storage", e)
                }
            }
        }
    }

    /**
     * Создает новую сессию чата
     * @param agentId ID агента (опционально)
     * @param scheduledTaskId ID задачи планировщика (опционально)
     * @return ID новой сессии
     */
    fun createSession(agentId: String? = null, scheduledTaskId: String? = null): String {
        val session = ChatSession(
            agentId = agentId,
            scheduledTaskId = scheduledTaskId
        )
        sessions[session.id] = session

        // Помечаем для сохранения
        persistenceManager?.markDirty(session)

        when {
            agentId != null -> logger.info("Created new session with agent: ${session.id}, agentId=$agentId")
            scheduledTaskId != null -> logger.info("Created new session with scheduled task: ${session.id}, scheduledTaskId=$scheduledTaskId")
            else -> logger.info("Created new session: ${session.id}")
        }
        return session.id
    }

    /**
     * Получает существующую сессию по ID
     * @param sessionId ID сессии
     * @return сессия или null если не найдена
     */
    fun getSession(sessionId: String): ChatSession? {
        val session = sessions[sessionId]
        if (session != null) {
            session.lastAccessedAt = System.currentTimeMillis()
            // Помечаем для сохранения (обновление lastAccessedAt)
            persistenceManager?.markDirty(session)
        }
        return session
    }

    /**
     * Получает или создает новую сессию
     * @param sessionId ID сессии (может быть null)
     * @return существующая сессия или новая
     */
    fun getOrCreateSession(sessionId: String?): Pair<String, ChatSession> {
        return if (sessionId != null && sessions.containsKey(sessionId)) {
            val session = sessions[sessionId]!!
            session.lastAccessedAt = System.currentTimeMillis()
            persistenceManager?.markDirty(session)
            sessionId to session
        } else {
            val newSession = ChatSession()
            sessions[newSession.id] = newSession
            persistenceManager?.markDirty(newSession)
            logger.info("Created new session: ${newSession.id}")
            newSession.id to newSession
        }
    }

    /**
     * Помечает сессию как требующую сохранения
     * @param sessionId ID сессии
     */
    fun markSessionDirty(sessionId: String) {
        val session = sessions[sessionId]
        if (session != null) {
            persistenceManager?.markDirty(session)
        }
    }

    /**
     * Обновляет название сессии
     * @param sessionId ID сессии
     * @param newTitle новое название
     * @return true если название обновлено, false если сессия не найдена
     */
    fun updateSessionTitle(sessionId: String, newTitle: String): Boolean {
        val session = sessions[sessionId]
        return if (session != null) {
            session.title = newTitle.trim().ifEmpty { null }
            persistenceManager?.markDirty(session)
            logger.info("Updated title for session $sessionId: $newTitle")
            true
        } else {
            logger.warn("Session not found: $sessionId")
            false
        }
    }

    /**
     * Добавляет сообщение в сессию
     * @param sessionId ID сессии
     * @param role роль отправителя (USER или ASSISTANT)
     * @param content содержимое сообщения
     * @return true если сообщение добавлено, false если сессия не найдена
     */
    fun addMessageToSession(sessionId: String, role: MessageRole, content: String): Boolean {
        val session = sessions[sessionId]
        return if (session != null) {
            session.addMessage(role, content)

            // Генерируем title из первого сообщения пользователя
            if (session.title == null && role == MessageRole.USER && session.messages.size == 1) {
                session.title = generateTitle(content)
                logger.debug("Generated title for session $sessionId: ${session.title}")
            }

            persistenceManager?.markDirty(session)
            logger.debug("Added message to session $sessionId: role=$role")
            true
        } else {
            logger.warn("Session not found: $sessionId")
            false
        }
    }

    /**
     * Генерирует название чата из содержимого первого сообщения
     * @param content текст сообщения
     * @return название чата (максимум 50 символов)
     */
    private fun generateTitle(content: String): String {
        val maxLength = 50
        val cleaned = content.trim().replace("\n", " ").replace(Regex("\\s+"), " ")
        return if (cleaned.length <= maxLength) {
            cleaned
        } else {
            cleaned.take(maxLength - 3) + "..."
        }
    }

    /**
     * Очищает историю сообщений в сессии
     * @param sessionId ID сессии
     * @return true если история очищена, false если сессия не найдена
     */
    fun clearSession(sessionId: String): Boolean {
        val session = sessions[sessionId]
        return if (session != null) {
            session.clear()
            persistenceManager?.markDirty(session)
            logger.info("Cleared session: $sessionId")
            true
        } else {
            logger.warn("Session not found: $sessionId")
            false
        }
    }

    /**
     * Удаляет сессию полностью
     * @param sessionId ID сессии
     * @return true если сессия удалена, false если не найдена
     */
    fun deleteSession(sessionId: String): Boolean {
        return if (sessions.remove(sessionId) != null) {
            // Удаляем из хранилища
            persistenceManager?.let {
                runBlocking {
                    it.deleteSession(sessionId)
                }
            }
            logger.info("Deleted session: $sessionId")
            true
        } else {
            logger.warn("Session not found: $sessionId")
            false
        }
    }

    /**
     * Копирует существующую сессию со всеми сообщениями
     * @param sessionId ID исходной сессии
     * @return ID новой скопированной сессии или null если исходная сессия не найдена
     */
    fun copySession(sessionId: String): String? {
        val originalSession = sessions[sessionId]
        return if (originalSession != null) {
            // Создаем новую сессию с копией всех данных
            val copiedSession = ChatSession(
                title = originalSession.title?.let { "$it (копия)" },
                agentId = originalSession.agentId
            ).apply {
                // Копируем все сообщения
                originalSession.messages.forEach { message ->
                    addMessage(message)
                }

                // Копируем архивированные сообщения (если были сжатия)
                originalSession.archivedMessages.forEach { message ->
                    archiveMessages(listOf(message))
                }

                // Копируем конфигурацию сжатия
                compressionConfig = originalSession.compressionConfig.copy()
                compressionCount = originalSession.compressionCount
            }

            sessions[copiedSession.id] = copiedSession
            persistenceManager?.markDirty(copiedSession)
            logger.info("Copied session $sessionId to ${copiedSession.id} (${copiedSession.messages.size} messages, ${copiedSession.archivedMessages.size} archived)")
            copiedSession.id
        } else {
            logger.warn("Session not found for copying: $sessionId")
            null
        }
    }

    /**
     * Возвращает количество активных сессий
     */
    fun getActiveSessionsCount(): Int = sessions.size

    /**
     * Возвращает информацию о всех активных сессиях
     */
    fun getSessionsInfo(): Map<String, SessionInfo> {
        return sessions.mapValues { (_, session) ->
            SessionInfo(
                id = session.id,
                title = session.title,
                messageCount = session.messages.size,
                createdAt = session.createdAt,
                lastAccessedAt = session.lastAccessedAt,
                agentId = session.agentId,
                scheduledTaskId = session.scheduledTaskId
            )
        }
    }

    /**
     * Завершает работу менеджера с сохранением всех сессий
     */
    suspend fun shutdown() {
        logger.info("Shutting down ChatSessionManager...")
        persistenceManager?.shutdown()
        logger.info("ChatSessionManager shutdown complete")
    }
}

/**
 * Информация о сессии для мониторинга
 */
data class SessionInfo(
    val id: String,
    val title: String? = null,
    val messageCount: Int,
    val createdAt: Long,
    val lastAccessedAt: Long,
    val agentId: String? = null,
    val scheduledTaskId: String? = null
)
