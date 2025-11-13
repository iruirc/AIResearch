package com.researchai.persistence

import com.researchai.models.ChatSession
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Менеджер для асинхронного сохранения сессий
 * Использует фоновую корутину для batch-записи
 */
class PersistenceManager(
    private val storage: PersistenceStorage,
    private val saveDelayMs: Long = 1000, // Задержка перед сохранением
    private val batchSize: Int = 10 // Максимальный размер batch
) {
    private val logger = LoggerFactory.getLogger(PersistenceManager::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Очередь сессий на сохранение
    private val saveQueue = Channel<String>(Channel.UNLIMITED)

    // Маркеры "грязных" сессий (требующих сохранения)
    private val dirtySessions = ConcurrentHashMap<String, ChatSession>()

    // Флаг активности
    private val isActive = AtomicBoolean(true)

    // Фоновая корутина для сохранения
    private val saveJob: Job

    init {
        saveJob = scope.launch {
            processSaveQueue()
        }
        logger.info("PersistenceManager started (delay=${saveDelayMs}ms, batch=$batchSize)")
    }

    /**
     * Помечает сессию как требующую сохранения
     */
    fun markDirty(session: ChatSession) {
        if (!isActive.get()) return

        dirtySessions[session.id] = session
        saveQueue.trySend(session.id)
    }

    /**
     * Синхронное сохранение сессии (для критических операций)
     */
    suspend fun saveNow(session: ChatSession): Result<Unit> {
        return storage.saveSession(session)
    }

    /**
     * Загружает все сессии из хранилища
     */
    suspend fun loadAllSessions(): Result<List<ChatSession>> {
        return storage.loadAllSessions()
    }

    /**
     * Удаляет сессию из хранилища
     */
    suspend fun deleteSession(sessionId: String): Result<Unit> {
        dirtySessions.remove(sessionId)
        return storage.deleteSession(sessionId)
    }

    /**
     * Сохраняет все "грязные" сессии и завершает работу
     */
    suspend fun shutdown() {
        if (!isActive.compareAndSet(true, false)) {
            return // Уже выключен
        }

        logger.info("Shutting down PersistenceManager, saving ${dirtySessions.size} sessions...")

        // Сохраняем все "грязные" сессии
        val sessionsToSave = dirtySessions.values.toList()
        dirtySessions.clear()

        sessionsToSave.forEach { session ->
            storage.saveSession(session).onFailure { e ->
                logger.error("Failed to save session ${session.id} on shutdown", e)
            }
        }

        // Останавливаем фоновую корутину
        saveJob.cancelAndJoin()

        // Закрываем хранилище
        storage.close()

        logger.info("PersistenceManager shutdown complete")
    }

    /**
     * Обработка очереди сохранения
     */
    private suspend fun processSaveQueue() {
        val sessionIds = mutableSetOf<String>()

        while (isActive.get()) {
            try {
                // Собираем batch сессий для сохранения
                sessionIds.clear()

                // Ждем первую сессию
                val firstId = withTimeoutOrNull(saveDelayMs) {
                    saveQueue.receive()
                }

                if (firstId != null) {
                    sessionIds.add(firstId)

                    // Собираем остальные сессии из очереди (до batchSize)
                    while (sessionIds.size < batchSize) {
                        val id = saveQueue.tryReceive().getOrNull() ?: break
                        sessionIds.add(id)
                    }

                    // Сохраняем batch
                    saveBatch(sessionIds)
                }
            } catch (e: Exception) {
                logger.error("Error in save queue processing", e)
            }
        }
    }

    /**
     * Сохраняет batch сессий
     */
    private suspend fun saveBatch(sessionIds: Set<String>) {
        val startTime = System.currentTimeMillis()
        var successCount = 0
        var failureCount = 0

        sessionIds.forEach { sessionId ->
            val session = dirtySessions.remove(sessionId)
            if (session != null) {
                storage.saveSession(session).fold(
                    onSuccess = { successCount++ },
                    onFailure = { e ->
                        logger.error("Failed to save session $sessionId", e)
                        failureCount++
                        // Возвращаем в очередь для повторной попытки
                        dirtySessions[sessionId] = session
                    }
                )
            }
        }

        val duration = System.currentTimeMillis() - startTime
        if (successCount > 0) {
            logger.debug("Saved $successCount sessions in ${duration}ms (failed: $failureCount)")
        }
    }
}
