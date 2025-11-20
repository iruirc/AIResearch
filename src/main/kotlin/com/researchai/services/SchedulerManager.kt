package com.researchai.services

import com.researchai.domain.usecase.SendMessageUseCase
import com.researchai.persistence.ScheduledTaskStorage
import com.researchai.scheduler.ChatTaskScheduler
import com.researchai.scheduler.ScheduledChatTask
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Менеджер для управления всеми планировщиками задач
 */
class SchedulerManager(
    private val sessionManager: ChatSessionManager,
    private val sendMessageUseCase: SendMessageUseCase,
    private val storage: ScheduledTaskStorage
) {
    private val logger = LoggerFactory.getLogger(SchedulerManager::class.java)

    /**
     * Храним все активные планировщики
     * Key: taskId, Value: scheduler
     */
    private val schedulers = ConcurrentHashMap<String, ChatTaskScheduler>()

    /**
     * Инициализация - загружаем все сохраненные задачи
     */
    init {
        runBlocking {
            loadAllTasks()
        }
    }

    /**
     * Создает и запускает новую задачу
     */
    suspend fun createTask(task: ScheduledChatTask): Result<String> {
        return try {
            logger.info("Creating new scheduled task: ${task.id}")

            // Валидация интервала
            if (task.intervalSeconds < 10) {
                return Result.failure(IllegalArgumentException("Interval must be at least 10 seconds"))
            }

            // Создаем планировщик
            val scheduler = ChatTaskScheduler(task, sessionManager, sendMessageUseCase)

            // Инициализируем (создаем сессию и первое сообщение)
            scheduler.initialize()

            // Запускаем планировщик
            scheduler.start()

            // Сохраняем в памяти
            schedulers[task.id] = scheduler

            // Сохраняем на диск
            storage.saveTask(task)

            logger.info("Scheduled task created and started: ${task.id}, sessionId=${task.sessionId}")
            Result.success(task.id)
        } catch (e: Exception) {
            logger.error("Failed to create scheduled task", e)
            Result.failure(e)
        }
    }

    /**
     * Останавливает задачу
     */
    fun stopTask(taskId: String): Result<Unit> {
        return try {
            val scheduler = schedulers[taskId]
                ?: return Result.failure(IllegalArgumentException("Task not found: $taskId"))

            logger.info("Stopping task: $taskId")
            scheduler.stop()

            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Failed to stop task $taskId", e)
            Result.failure(e)
        }
    }

    /**
     * Возобновляет задачу
     */
    fun startTask(taskId: String): Result<Unit> {
        return try {
            val scheduler = schedulers[taskId]
                ?: return Result.failure(IllegalArgumentException("Task not found: $taskId"))

            logger.info("Starting task: $taskId")
            scheduler.start()

            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Failed to start task $taskId", e)
            Result.failure(e)
        }
    }

    /**
     * Удаляет задачу полностью
     */
    suspend fun deleteTask(taskId: String): Result<Unit> {
        return try {
            val scheduler = schedulers[taskId]
                ?: return Result.failure(IllegalArgumentException("Task not found: $taskId"))

            logger.info("Deleting task: $taskId")

            // Останавливаем планировщик
            scheduler.stop()
            scheduler.shutdown()

            // Удаляем из памяти
            schedulers.remove(taskId)

            // Удаляем из хранилища
            storage.deleteTask(taskId)

            // Удаляем связанную сессию
            scheduler.task.sessionId?.let { sessionId ->
                sessionManager.deleteSession(sessionId)
            }

            logger.info("Task deleted: $taskId")
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Failed to delete task $taskId", e)
            Result.failure(e)
        }
    }

    /**
     * Получает задачу по ID
     */
    fun getTask(taskId: String): ScheduledChatTask? {
        return schedulers[taskId]?.task
    }

    /**
     * Получает планировщик по ID
     */
    fun getScheduler(taskId: String): ChatTaskScheduler? {
        return schedulers[taskId]
    }

    /**
     * Получает все задачи
     */
    fun getAllTasks(): List<ScheduledChatTask> {
        return schedulers.values.map { it.task }
    }

    /**
     * Получает задачу по sessionId
     */
    fun getTaskBySessionId(sessionId: String): ScheduledChatTask? {
        return schedulers.values
            .map { it.task }
            .find { it.sessionId == sessionId }
    }

    /**
     * Загружает все сохраненные задачи
     */
    private suspend fun loadAllTasks() {
        logger.info("Loading all scheduled tasks...")

        storage.loadAllTasks().onSuccess { tasks ->
            tasks.forEach { task ->
                try {
                    val scheduler = ChatTaskScheduler(task, sessionManager, sendMessageUseCase)
                    // Не вызываем initialize() - сессия уже существует
                    scheduler.start()
                    schedulers[task.id] = scheduler
                    logger.debug("Restored task: ${task.id}")
                } catch (e: Exception) {
                    logger.error("Failed to restore task ${task.id}", e)
                }
            }
            logger.info("Loaded ${tasks.size} scheduled tasks")
        }.onFailure { e ->
            logger.error("Failed to load scheduled tasks", e)
        }
    }

    /**
     * Graceful shutdown - останавливаем все планировщики
     */
    suspend fun shutdown() {
        logger.info("Shutting down SchedulerManager...")

        schedulers.values.forEach { scheduler ->
            try {
                scheduler.stop()
                scheduler.shutdown()

                // Сохраняем задачу
                storage.saveTask(scheduler.task)
            } catch (e: Exception) {
                logger.error("Failed to shutdown scheduler ${scheduler.task.id}", e)
            }
        }

        schedulers.clear()
        logger.info("SchedulerManager shutdown complete")
    }
}
