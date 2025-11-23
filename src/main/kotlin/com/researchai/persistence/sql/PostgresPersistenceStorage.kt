package com.researchai.persistence.sql

import com.researchai.domain.models.CompressionConfig
import com.researchai.domain.models.Message
import com.researchai.models.ChatSession
import com.researchai.persistence.PersistenceStorage
import com.researchai.persistence.sql.DatabaseFactory.dbQuery
import com.researchai.persistence.sql.tables.ChatSessionsTable
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.slf4j.LoggerFactory

/**
 * PostgreSQL implementation of PersistenceStorage for ChatSession
 * This is the most complex storage as it handles JSONB columns for messages and compression
 */
class PostgresPersistenceStorage : PersistenceStorage {
    private val logger = LoggerFactory.getLogger(PostgresPersistenceStorage::class.java)

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    override suspend fun saveSession(session: ChatSession): Result<Unit> = dbQuery {
        try {
            // Convert messages and archivedMessages to Map<String, Any> for JSONB storage
            val messagesJson = session.messages.map { message ->
                json.encodeToString(message)
            }
            val archivedMessagesJson = session.archivedMessages.map { message ->
                json.encodeToString(message)
            }
            val compressionConfigJson = json.encodeToString(session.compressionConfig)

            ChatSessionsTable.upsert {
                it[id] = session.id
                it[title] = session.title
                it[messages] = messagesJson.map { msgJson ->
                    @Suppress("UNCHECKED_CAST")
                    json.decodeFromString<Map<String, Any>>(msgJson)
                }
                it[createdAt] = Instant.fromEpochMilliseconds(session.createdAt)
                it[lastAccessedAt] = Instant.fromEpochMilliseconds(session.lastAccessedAt)
                it[assistantId] = session.assistantId
                it[scheduledTaskId] = session.scheduledTaskId
                it[pipelineId] = session.pipelineId
                it[archivedMessages] = archivedMessagesJson.map { msgJson ->
                    @Suppress("UNCHECKED_CAST")
                    json.decodeFromString<Map<String, Any>>(msgJson)
                }
                it[compressionConfig] = json.decodeFromString<Map<String, Any>>(compressionConfigJson)
                it[compressionCount] = session.compressionCount
            }

            logger.debug("Saved session: ${session.id} (${session.messages.size} messages)")
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Failed to save session: ${session.id}", e)
            Result.failure(e)
        }
    }

    override suspend fun loadSession(sessionId: String): Result<ChatSession?> = dbQuery {
        try {
            val row = ChatSessionsTable.selectAll()
                .where { ChatSessionsTable.id eq sessionId }
                .singleOrNull()

            val session = row?.let {
                // Reconstruct messages from JSONB
                val messagesData = it[ChatSessionsTable.messages]
                val messages = messagesData.map { messageMap ->
                    val messageJson = json.encodeToString(messageMap)
                    json.decodeFromString<Message>(messageJson)
                }.toMutableList()

                // Reconstruct archived messages
                val archivedData = it[ChatSessionsTable.archivedMessages]
                val archivedMessages = archivedData.map { messageMap ->
                    val messageJson = json.encodeToString(messageMap)
                    json.decodeFromString<Message>(messageJson)
                }.toMutableList()

                // Reconstruct compression config
                val compressionConfigData = it[ChatSessionsTable.compressionConfig]
                val compressionConfigJson = json.encodeToString(compressionConfigData)
                val compressionConfig = json.decodeFromString<CompressionConfig>(compressionConfigJson)

                ChatSession(
                    id = it[ChatSessionsTable.id],
                    title = it[ChatSessionsTable.title],
                    _messages = messages,
                    createdAt = it[ChatSessionsTable.createdAt].toEpochMilliseconds(),
                    lastAccessedAt = it[ChatSessionsTable.lastAccessedAt].toEpochMilliseconds(),
                    assistantId = it[ChatSessionsTable.assistantId],
                    scheduledTaskId = it[ChatSessionsTable.scheduledTaskId],
                    pipelineId = it[ChatSessionsTable.pipelineId],
                    _archivedMessages = archivedMessages,
                    compressionConfig = compressionConfig,
                    compressionCount = it[ChatSessionsTable.compressionCount]
                )
            }

            logger.debug("Loaded session: $sessionId, found: ${session != null}")
            Result.success(session)
        } catch (e: Exception) {
            logger.error("Failed to load session: $sessionId", e)
            Result.failure(e)
        }
    }

    override suspend fun loadAllSessions(): Result<List<ChatSession>> = dbQuery {
        try {
            val sessions = ChatSessionsTable.selectAll()
                .orderBy(ChatSessionsTable.lastAccessedAt, SortOrder.DESC)
                .map { row ->
                    // Reconstruct messages from JSONB
                    val messagesData = row[ChatSessionsTable.messages]
                    val messages = messagesData.map { messageMap ->
                        val messageJson = json.encodeToString(messageMap)
                        json.decodeFromString<Message>(messageJson)
                    }.toMutableList()

                    // Reconstruct archived messages
                    val archivedData = row[ChatSessionsTable.archivedMessages]
                    val archivedMessages = archivedData.map { messageMap ->
                        val messageJson = json.encodeToString(messageMap)
                        json.decodeFromString<Message>(messageJson)
                    }.toMutableList()

                    // Reconstruct compression config
                    val compressionConfigData = row[ChatSessionsTable.compressionConfig]
                    val compressionConfigJson = json.encodeToString(compressionConfigData)
                    val compressionConfig = json.decodeFromString<CompressionConfig>(compressionConfigJson)

                    ChatSession(
                        id = row[ChatSessionsTable.id],
                        title = row[ChatSessionsTable.title],
                        _messages = messages,
                        createdAt = row[ChatSessionsTable.createdAt].toEpochMilliseconds(),
                        lastAccessedAt = row[ChatSessionsTable.lastAccessedAt].toEpochMilliseconds(),
                        assistantId = row[ChatSessionsTable.assistantId],
                        scheduledTaskId = row[ChatSessionsTable.scheduledTaskId],
                        pipelineId = row[ChatSessionsTable.pipelineId],
                        _archivedMessages = archivedMessages,
                        compressionConfig = compressionConfig,
                        compressionCount = row[ChatSessionsTable.compressionCount]
                    )
                }

            logger.debug("Loaded ${sessions.size} sessions")
            Result.success(sessions)
        } catch (e: Exception) {
            logger.error("Failed to load all sessions", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteSession(sessionId: String): Result<Unit> = dbQuery {
        try {
            ChatSessionsTable.deleteWhere { id eq sessionId }
            logger.info("Deleted session: $sessionId")
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Failed to delete session: $sessionId", e)
            Result.failure(e)
        }
    }

    override suspend fun sessionExists(sessionId: String): Boolean = dbQuery {
        ChatSessionsTable.selectAll()
            .where { ChatSessionsTable.id eq sessionId }
            .count() > 0
    }

    override suspend fun close() {
        logger.info("Closing PostgresPersistenceStorage")
    }
}
