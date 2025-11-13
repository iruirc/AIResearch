package com.researchai.services

import com.researchai.models.ChatSession
import com.researchai.models.MessageRole
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Менеджер для управления сессиями чата.
 * Хранит все активные сессии в памяти до рестарта приложения.
 */
class ChatSessionManager {
    private val logger = LoggerFactory.getLogger(ChatSessionManager::class.java)
    private val sessions = ConcurrentHashMap<String, ChatSession>()

    /**
     * Создает новую сессию чата
     * @param agentId ID агента (опционально)
     * @return ID новой сессии
     */
    fun createSession(agentId: String? = null): String {
        val session = ChatSession(agentId = agentId)
        sessions[session.id] = session
        if (agentId != null) {
            logger.info("Created new session with agent: ${session.id}, agentId=$agentId")
        } else {
            logger.info("Created new session: ${session.id}")
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
            sessionId to session
        } else {
            val newSession = ChatSession()
            sessions[newSession.id] = newSession
            logger.info("Created new session: ${newSession.id}")
            newSession.id to newSession
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
            logger.debug("Added message to session $sessionId: role=$role")
            true
        } else {
            logger.warn("Session not found: $sessionId")
            false
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
                messageCount = session.messages.size,
                createdAt = session.createdAt,
                lastAccessedAt = session.lastAccessedAt,
                agentId = session.agentId
            )
        }
    }
}

/**
 * Информация о сессии для мониторинга
 */
data class SessionInfo(
    val id: String,
    val messageCount: Int,
    val createdAt: Long,
    val lastAccessedAt: Long,
    val agentId: String? = null
)
