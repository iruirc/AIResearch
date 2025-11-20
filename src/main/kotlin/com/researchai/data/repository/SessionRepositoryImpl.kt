package com.researchai.data.repository

import com.researchai.domain.models.AIError
import com.researchai.domain.models.Message
import com.researchai.domain.models.ProviderType
import com.researchai.domain.repository.ChatSession
import com.researchai.domain.repository.SessionRepository
import com.researchai.services.ChatSessionManager
import java.util.*

/**
 * Реализация репозитория сессий на основе существующего ChatSessionManager
 * Временное решение для обратной совместимости
 */
class SessionRepositoryImpl(
    private val sessionManager: ChatSessionManager
) : SessionRepository {

    override suspend fun createSession(
        providerId: ProviderType,
        assistantId: String?,
        metadata: Map<String, String>
    ): Result<ChatSession> {
        return try {
            val sessionId = sessionManager.createSession(assistantId)

            val session = ChatSession(
                id = sessionId,
                providerId = providerId,
                assistantId = assistantId,
                messages = emptyList(),
                metadata = metadata
            )

            Result.success(session)
        } catch (e: Exception) {
            Result.failure(AIError.DatabaseException("Failed to create session", e))
        }
    }

    override suspend fun getSession(sessionId: String): Result<ChatSession> {
        return try {
            val legacySession = sessionManager.getSession(sessionId)
                ?: return Result.failure(AIError.NotFoundException("Session not found: $sessionId"))

            // ChatSession теперь использует domain.Message напрямую
            val messages = legacySession.messages

            val session = ChatSession(
                id = legacySession.id,
                providerId = ProviderType.CLAUDE, // Default для legacy сессий
                assistantId = legacySession.assistantId,
                messages = messages,
                createdAt = legacySession.createdAt,
                lastAccessedAt = legacySession.lastAccessedAt
            )

            Result.success(session)
        } catch (e: Exception) {
            Result.failure(AIError.DatabaseException("Failed to get session", e))
        }
    }

    override suspend fun getAllSessions(): Result<List<ChatSession>> {
        return try {
            val sessionsInfo = sessionManager.getSessionsInfo()

            val sessions = sessionsInfo.map { (sessionId, info) ->
                val legacySession = sessionManager.getSession(sessionId)!!
                ChatSession(
                    id = info.id,
                    providerId = ProviderType.CLAUDE,
                    assistantId = info.assistantId,
                    messages = legacySession.messages, // ChatSession теперь использует domain.Message напрямую
                    createdAt = info.createdAt,
                    lastAccessedAt = info.lastAccessedAt
                )
            }

            Result.success(sessions)
        } catch (e: Exception) {
            Result.failure(AIError.DatabaseException("Failed to get all sessions", e))
        }
    }

    override suspend fun updateSession(session: ChatSession): Result<Unit> {
        return try {
            val legacySession = sessionManager.getSession(session.id)
                ?: return Result.failure(AIError.NotFoundException("Session not found: ${session.id}"))

            // Обновляем lastAccessedAt через повторное получение сессии
            sessionManager.getSession(session.id)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(AIError.DatabaseException("Failed to update session", e))
        }
    }

    override suspend fun deleteSession(sessionId: String): Result<Unit> {
        return try {
            sessionManager.deleteSession(sessionId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(AIError.DatabaseException("Failed to delete session", e))
        }
    }

    override suspend fun addMessage(sessionId: String, message: Message): Result<Unit> {
        return try {
            val legacySession = sessionManager.getSession(sessionId)
                ?: return Result.failure(AIError.NotFoundException("Session not found: $sessionId"))

            // ChatSession теперь принимает domain.Message напрямую
            legacySession.addMessage(message)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(AIError.DatabaseException("Failed to add message", e))
        }
    }

    override suspend fun getMessages(sessionId: String): Result<List<Message>> {
        return try {
            val legacySession = sessionManager.getSession(sessionId)
                ?: return Result.failure(AIError.NotFoundException("Session not found: $sessionId"))

            // ChatSession теперь использует domain.Message напрямую
            val messages = legacySession.messages

            Result.success(messages)
        } catch (e: Exception) {
            Result.failure(AIError.DatabaseException("Failed to get messages", e))
        }
    }

    override suspend fun clearMessages(sessionId: String): Result<Unit> {
        return try {
            sessionManager.clearSession(sessionId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(AIError.DatabaseException("Failed to clear messages", e))
        }
    }
}
