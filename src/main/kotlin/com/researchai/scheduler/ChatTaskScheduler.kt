package com.researchai.scheduler

import com.researchai.domain.models.MessageRole
import com.researchai.domain.models.RequestParameters
import com.researchai.domain.usecase.SendMessageUseCase
import com.researchai.services.ChatSessionManager
import org.slf4j.LoggerFactory

/**
 * Планировщик задач для чата
 * Отправляет повторяющиеся сообщения в AI чат
 */
class ChatTaskScheduler(
    task: ScheduledChatTask,
    private val sessionManager: ChatSessionManager,
    private val sendMessageUseCase: SendMessageUseCase
) : TaskScheduler<ScheduledChatTask>(task) {

    private val logger = LoggerFactory.getLogger(ChatTaskScheduler::class.java)

    /**
     * Инициализирует задачу:
     * - Создает новую сессию чата
     * - Добавляет первое информационное сообщение
     */
    suspend fun initialize() {
        logger.info("Initializing chat task scheduler for task ${task.id}")

        // Создаем новую сессию с привязкой к задаче
        val sessionId = sessionManager.createSession(scheduledTaskId = task.id)
        task.sessionId = sessionId

        // Создаем информационное сообщение
        val initialMessage = buildInitialMessage()

        // Добавляем первое сообщение от ассистента
        sessionManager.addMessageToSession(
            sessionId = sessionId,
            role = com.researchai.models.MessageRole.ASSISTANT,
            content = initialMessage
        )

        // Устанавливаем title сессии
        val title = task.title ?: "Задача: ${task.taskRequest.take(50)}..."
        sessionManager.updateSessionTitle(sessionId, title)

        logger.info("Chat task scheduler initialized: sessionId=$sessionId")
    }

    /**
     * Выполнение задачи - отправка сообщения в чат
     */
    override suspend fun onTaskExecution() {
        val sessionId = task.sessionId ?: run {
            logger.error("Session ID is null for task ${task.id}")
            return
        }

        logger.debug("Executing chat task ${task.id}: sending message to session $sessionId")

        // Определяем провайдера и модель
        val providerId = task.providerId ?: com.researchai.domain.models.ProviderType.CLAUDE
        val model = task.model

        // Отправляем сообщение
        val result = sendMessageUseCase(
            message = task.taskRequest,
            sessionId = sessionId,
            providerId = providerId,
            model = model,
            parameters = RequestParameters()
        )

        result.onFailure { error ->
            logger.error("Failed to execute chat task ${task.id}", error)
            // Ошибка будет обработана в onTaskError
            throw error
        }
    }

    /**
     * Обработка ошибки - добавляем сообщение об ошибке в чат
     */
    override suspend fun onTaskError(error: Exception) {
        val sessionId = task.sessionId ?: return

        val errorMessage = buildErrorMessage(error)

        try {
            sessionManager.addMessageToSession(
                sessionId = sessionId,
                role = com.researchai.models.MessageRole.ASSISTANT,
                content = errorMessage
            )
        } catch (e: Exception) {
            logger.error("Failed to add error message to session $sessionId", e)
        }
    }

    /**
     * Формирует начальное информационное сообщение
     */
    private fun buildInitialMessage(): String {
        val interval = formatInterval(task.intervalSeconds)
        return """
            Я планировщик задач.
            Каждые $interval я буду выполнять задачу
            Моя задача - ${task.taskRequest}
        """.trimIndent()
    }

    /**
     * Формирует сообщение об ошибке
     */
    private fun buildErrorMessage(error: Exception): String {
        val interval = formatInterval(task.intervalSeconds)
        return """
            ⚠️ Ошибка выполнения задачи:
            ${error.message ?: "Неизвестная ошибка"}

            Следующая попытка будет выполнена через $interval
        """.trimIndent()
    }

    /**
     * Форматирует интервал в читаемый вид
     */
    private fun formatInterval(seconds: Long): String {
        return when {
            seconds < 60 -> "$seconds секунд(ы)"
            seconds < 3600 -> "${seconds / 60} минут(ы)"
            seconds < 86400 -> "${seconds / 3600} час(ов)"
            else -> "${seconds / 86400} дней"
        }
    }
}
