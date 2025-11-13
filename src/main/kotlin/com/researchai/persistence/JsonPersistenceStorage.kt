package com.researchai.persistence

import com.researchai.models.ChatSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import com.researchai.domain.models.Message
import com.researchai.domain.models.CompressionConfig

/**
 * Реализация PersistenceStorage с сохранением в JSON файлы
 */
class JsonPersistenceStorage(
    private val storageDir: File = File("data/sessions")
) : PersistenceStorage {

    private val logger = LoggerFactory.getLogger(JsonPersistenceStorage::class.java)

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    init {
        // Создаем директорию для хранения сессий
        if (!storageDir.exists()) {
            storageDir.mkdirs()
            logger.info("Created sessions storage directory: ${storageDir.absolutePath}")
        }
    }

    override suspend fun saveSession(session: ChatSession): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val sessionFile = File(storageDir, "${session.id}.json")
            val tempFile = File(storageDir, "${session.id}.tmp")

            // Конвертируем ChatSession в сериализуемую модель
            val sessionData = SessionData(
                id = session.id,
                messages = session.messages,
                createdAt = session.createdAt,
                lastAccessedAt = session.lastAccessedAt,
                agentId = session.agentId,
                archivedMessages = session.archivedMessages,
                compressionConfig = session.compressionConfig,
                compressionCount = session.compressionCount
            )

            // Записываем во временный файл
            tempFile.writeText(json.encodeToString(sessionData))

            // Атомарно заменяем основной файл
            Files.move(tempFile.toPath(), sessionFile.toPath(), StandardCopyOption.REPLACE_EXISTING)

            logger.debug("Saved session ${session.id} (${session.messages.size} messages)")
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Failed to save session ${session.id}", e)
            Result.failure(e)
        }
    }

    override suspend fun loadSession(sessionId: String): Result<ChatSession?> = withContext(Dispatchers.IO) {
        try {
            val sessionFile = File(storageDir, "$sessionId.json")

            if (!sessionFile.exists()) {
                return@withContext Result.success(null)
            }

            val sessionData = json.decodeFromString<SessionData>(sessionFile.readText())

            // Создаем новую сессию и заполняем данными
            val session = ChatSession(
                id = sessionData.id,
                createdAt = sessionData.createdAt,
                agentId = sessionData.agentId,
                compressionConfig = sessionData.compressionConfig,
                compressionCount = sessionData.compressionCount
            )

            // Восстанавливаем сообщения через публичные методы
            sessionData.messages.forEach { message ->
                session.addMessage(message)
            }

            // Восстанавливаем архивированные сообщения
            if (sessionData.archivedMessages.isNotEmpty()) {
                session.archiveMessages(sessionData.archivedMessages)
            }

            // Восстанавливаем lastAccessedAt
            session.lastAccessedAt = sessionData.lastAccessedAt

            logger.debug("Loaded session $sessionId (${session.messages.size} messages)")
            Result.success(session)
        } catch (e: Exception) {
            logger.error("Failed to load session $sessionId", e)
            Result.failure(e)
        }
    }

    override suspend fun loadAllSessions(): Result<List<ChatSession>> = withContext(Dispatchers.IO) {
        try {
            val sessions = mutableListOf<ChatSession>()

            storageDir.listFiles { file -> file.extension == "json" }?.forEach { file ->
                try {
                    val sessionData = json.decodeFromString<SessionData>(file.readText())

                    // Создаем новую сессию и заполняем данными
                    val session = ChatSession(
                        id = sessionData.id,
                        createdAt = sessionData.createdAt,
                        agentId = sessionData.agentId,
                        compressionConfig = sessionData.compressionConfig,
                        compressionCount = sessionData.compressionCount
                    )

                    // Восстанавливаем сообщения через публичные методы
                    sessionData.messages.forEach { message ->
                        session.addMessage(message)
                    }

                    // Восстанавливаем архивированные сообщения
                    if (sessionData.archivedMessages.isNotEmpty()) {
                        session.archiveMessages(sessionData.archivedMessages)
                    }

                    // Восстанавливаем lastAccessedAt
                    session.lastAccessedAt = sessionData.lastAccessedAt

                    sessions.add(session)
                } catch (e: Exception) {
                    logger.warn("Failed to load session from file ${file.name}", e)
                }
            }

            logger.info("Loaded ${sessions.size} sessions from storage")
            Result.success(sessions)
        } catch (e: Exception) {
            logger.error("Failed to load sessions", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteSession(sessionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val sessionFile = File(storageDir, "$sessionId.json")

            if (sessionFile.exists()) {
                sessionFile.delete()
                logger.info("Deleted session $sessionId")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Failed to delete session $sessionId", e)
            Result.failure(e)
        }
    }

    override suspend fun sessionExists(sessionId: String): Boolean = withContext(Dispatchers.IO) {
        File(storageDir, "$sessionId.json").exists()
    }

    override suspend fun close() {
        logger.info("Closing JSON persistence storage")
    }
}

/**
 * Сериализуемая модель сессии для JSON
 */
@Serializable
private data class SessionData(
    val id: String,
    val messages: List<Message>,
    val createdAt: Long,
    val lastAccessedAt: Long,
    val agentId: String? = null,
    val archivedMessages: List<Message> = emptyList(),
    val compressionConfig: CompressionConfig = CompressionConfig(),
    val compressionCount: Int = 0
)
