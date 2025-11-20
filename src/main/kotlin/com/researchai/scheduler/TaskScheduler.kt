package com.researchai.scheduler

import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Абстрактный планировщик задач
 * Управляет выполнением задачи с заданным интервалом
 */
abstract class TaskScheduler<T : ScheduledTask>(
    val task: T
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val _isRunning = AtomicBoolean(false)

    /**
     * Временная метка следующего выполнения (в миллисекундах)
     */
    @Volatile
    var nextExecutionTime: Long = 0L
        private set

    /**
     * Запускает планировщик
     */
    fun start() {
        if (_isRunning.getAndSet(true)) {
            logger.warn("Scheduler for task ${task.id} is already running")
            return
        }

        logger.info("Starting scheduler for task ${task.id}")

        job = scope.launch {
            try {
                // Выполнить сразу если требуется
                if (task.executeImmediately) {
                    logger.debug("Executing task ${task.id} immediately")
                    executeTask()
                }

                // Основной цикл планировщика
                while (isActive && _isRunning.get()) {
                    nextExecutionTime = System.currentTimeMillis() + (task.intervalSeconds * 1000)
                    delay(task.intervalSeconds * 1000)

                    if (_isRunning.get()) {
                        executeTask()
                    }
                }
            } catch (e: CancellationException) {
                logger.info("Scheduler for task ${task.id} was cancelled")
            } catch (e: Exception) {
                logger.error("Scheduler for task ${task.id} failed", e)
            }
        }
    }

    /**
     * Останавливает планировщик
     */
    fun stop() {
        if (!_isRunning.getAndSet(false)) {
            logger.warn("Scheduler for task ${task.id} is not running")
            return
        }

        logger.info("Stopping scheduler for task ${task.id}")
        job?.cancel()
        job = null
        nextExecutionTime = 0L
    }

    /**
     * Проверяет, запущен ли планировщик
     */
    fun isRunning(): Boolean = _isRunning.get()

    /**
     * Получить количество секунд до следующего выполнения
     */
    fun getSecondsUntilNextExecution(): Long {
        if (!isRunning() || nextExecutionTime == 0L) {
            return 0L
        }
        val remaining = (nextExecutionTime - System.currentTimeMillis()) / 1000
        return maxOf(0L, remaining)
    }

    /**
     * Выполняет задачу с обработкой ошибок
     */
    private suspend fun executeTask() {
        try {
            logger.debug("Executing task ${task.id}")
            onTaskExecution()
            logger.debug("Task ${task.id} executed successfully")
        } catch (e: Exception) {
            logger.error("Task ${task.id} execution failed", e)
            onTaskError(e)
        }
    }

    /**
     * Абстрактный метод для выполнения конкретной задачи
     * Реализуется в подклассах
     */
    protected abstract suspend fun onTaskExecution()

    /**
     * Обработка ошибок выполнения задачи
     * По умолчанию только логирует, но может быть переопределен
     */
    protected open suspend fun onTaskError(error: Exception) {
        // По умолчанию только логируем
        logger.error("Error in task ${task.id}: ${error.message}")
    }

    /**
     * Graceful shutdown планировщика
     */
    fun shutdown() {
        stop()
        scope.cancel()
    }
}
