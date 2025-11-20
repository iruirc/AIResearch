package com.researchai.routes

import com.researchai.domain.models.ProviderType
import com.researchai.models.*
import com.researchai.scheduler.ScheduledChatTask
import com.researchai.services.SchedulerManager
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("SchedulerRoutes")

/**
 * Регистрирует маршруты для работы с планировщиком задач
 */
fun Route.schedulerRoutes(schedulerManager: SchedulerManager) {
    route("/scheduler") {

        // POST /scheduler/tasks - Создать новую задачу
        post("/tasks") {
            try {
                val request = call.receive<CreateScheduledTaskRequest>()

                // Валидация
                if (request.taskRequest.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        SchedulerOperationResponse(
                            success = false,
                            message = "Task request cannot be empty"
                        )
                    )
                    return@post
                }

                if (request.intervalSeconds < 10) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        SchedulerOperationResponse(
                            success = false,
                            message = "Interval must be at least 10 seconds"
                        )
                    )
                    return@post
                }

                // Парсим providerId если указан
                val providerType = request.providerId?.let { providerId ->
                    try {
                        ProviderType.valueOf(providerId)
                    } catch (e: IllegalArgumentException) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            SchedulerOperationResponse(
                                success = false,
                                message = "Invalid provider: $providerId. Valid providers: ${ProviderType.entries.joinToString(", ")}"
                            )
                        )
                        return@post
                    }
                }

                // Создаем задачу
                val task = ScheduledChatTask(
                    title = request.title,
                    taskRequest = request.taskRequest,
                    intervalSeconds = request.intervalSeconds,
                    executeImmediately = request.executeImmediately,
                    providerId = providerType,
                    model = request.model
                )

                schedulerManager.createTask(task).onSuccess { taskId ->
                    val createdTask = schedulerManager.getTask(taskId)
                    call.respond(
                        HttpStatusCode.Created,
                        CreateScheduledTaskResponse(
                            taskId = taskId,
                            sessionId = createdTask?.sessionId ?: "",
                            message = "Task created and started successfully"
                        )
                    )
                    logger.info("Created scheduled task: $taskId")
                }.onFailure { error ->
                    logger.error("Failed to create scheduled task", error)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        SchedulerOperationResponse(
                            success = false,
                            message = "Failed to create task: ${error.message}"
                        )
                    )
                }

            } catch (e: Exception) {
                logger.error("Error in POST /scheduler/tasks", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    SchedulerOperationResponse(
                        success = false,
                        message = "Internal server error: ${e.message}"
                    )
                )
            }
        }

        // GET /scheduler/tasks - Получить список всех задач
        get("/tasks") {
            try {
                val tasks = schedulerManager.getAllTasks()
                val taskInfoList = tasks.map { task ->
                    val scheduler = schedulerManager.getScheduler(task.id)
                    ScheduledTaskInfo(
                        id = task.id,
                        title = task.title,
                        taskRequest = task.taskRequest,
                        intervalSeconds = task.intervalSeconds,
                        executeImmediately = task.executeImmediately,
                        sessionId = task.sessionId,
                        createdAt = task.createdAt,
                        isRunning = scheduler?.isRunning() ?: false,
                        secondsUntilNext = scheduler?.getSecondsUntilNextExecution() ?: 0,
                        providerId = task.providerId?.name,
                        model = task.model
                    )
                }

                call.respond(
                    HttpStatusCode.OK,
                    ScheduledTasksListResponse(tasks = taskInfoList)
                )
                logger.debug("Listed ${taskInfoList.size} scheduled tasks")

            } catch (e: Exception) {
                logger.error("Error in GET /scheduler/tasks", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    SchedulerOperationResponse(
                        success = false,
                        message = "Failed to list tasks: ${e.message}"
                    )
                )
            }
        }

        // GET /scheduler/tasks/{id} - Получить детали задачи
        get("/tasks/{id}") {
            try {
                val taskId = call.parameters["id"]
                if (taskId == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        SchedulerOperationResponse(
                            success = false,
                            message = "Task ID is required"
                        )
                    )
                    return@get
                }

                val task = schedulerManager.getTask(taskId)
                if (task == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        SchedulerOperationResponse(
                            success = false,
                            message = "Task not found: $taskId"
                        )
                    )
                    return@get
                }

                val scheduler = schedulerManager.getScheduler(taskId)
                val taskDetail = ScheduledTaskDetailResponse(
                    id = task.id,
                    title = task.title,
                    taskRequest = task.taskRequest,
                    intervalSeconds = task.intervalSeconds,
                    executeImmediately = task.executeImmediately,
                    sessionId = task.sessionId,
                    createdAt = task.createdAt,
                    isRunning = scheduler?.isRunning() ?: false,
                    secondsUntilNext = scheduler?.getSecondsUntilNextExecution() ?: 0,
                    providerId = task.providerId?.name,
                    model = task.model
                )

                call.respond(HttpStatusCode.OK, taskDetail)
                logger.debug("Retrieved task details: $taskId")

            } catch (e: Exception) {
                logger.error("Error in GET /scheduler/tasks/{id}", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    SchedulerOperationResponse(
                        success = false,
                        message = "Failed to get task: ${e.message}"
                    )
                )
            }
        }

        // POST /scheduler/tasks/{id}/stop - Остановить задачу
        post("/tasks/{id}/stop") {
            try {
                val taskId = call.parameters["id"]
                if (taskId == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        SchedulerOperationResponse(
                            success = false,
                            message = "Task ID is required"
                        )
                    )
                    return@post
                }

                schedulerManager.stopTask(taskId).onSuccess {
                    call.respond(
                        HttpStatusCode.OK,
                        SchedulerOperationResponse(
                            success = true,
                            message = "Task stopped successfully"
                        )
                    )
                    logger.info("Stopped task: $taskId")
                }.onFailure { error ->
                    logger.error("Failed to stop task $taskId", error)
                    call.respond(
                        HttpStatusCode.NotFound,
                        SchedulerOperationResponse(
                            success = false,
                            message = error.message ?: "Failed to stop task"
                        )
                    )
                }

            } catch (e: Exception) {
                logger.error("Error in POST /scheduler/tasks/{id}/stop", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    SchedulerOperationResponse(
                        success = false,
                        message = "Internal server error: ${e.message}"
                    )
                )
            }
        }

        // POST /scheduler/tasks/{id}/start - Запустить задачу
        post("/tasks/{id}/start") {
            try {
                val taskId = call.parameters["id"]
                if (taskId == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        SchedulerOperationResponse(
                            success = false,
                            message = "Task ID is required"
                        )
                    )
                    return@post
                }

                schedulerManager.startTask(taskId).onSuccess {
                    call.respond(
                        HttpStatusCode.OK,
                        SchedulerOperationResponse(
                            success = true,
                            message = "Task started successfully"
                        )
                    )
                    logger.info("Started task: $taskId")
                }.onFailure { error ->
                    logger.error("Failed to start task $taskId", error)
                    call.respond(
                        HttpStatusCode.NotFound,
                        SchedulerOperationResponse(
                            success = false,
                            message = error.message ?: "Failed to start task"
                        )
                    )
                }

            } catch (e: Exception) {
                logger.error("Error in POST /scheduler/tasks/{id}/start", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    SchedulerOperationResponse(
                        success = false,
                        message = "Internal server error: ${e.message}"
                    )
                )
            }
        }

        // DELETE /scheduler/tasks/{id} - Удалить задачу
        delete("/tasks/{id}") {
            try {
                val taskId = call.parameters["id"]
                if (taskId == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        SchedulerOperationResponse(
                            success = false,
                            message = "Task ID is required"
                        )
                    )
                    return@delete
                }

                schedulerManager.deleteTask(taskId).onSuccess {
                    call.respond(
                        HttpStatusCode.OK,
                        SchedulerOperationResponse(
                            success = true,
                            message = "Task deleted successfully"
                        )
                    )
                    logger.info("Deleted task: $taskId")
                }.onFailure { error ->
                    logger.error("Failed to delete task $taskId", error)
                    call.respond(
                        HttpStatusCode.NotFound,
                        SchedulerOperationResponse(
                            success = false,
                            message = error.message ?: "Failed to delete task"
                        )
                    )
                }

            } catch (e: Exception) {
                logger.error("Error in DELETE /scheduler/tasks/{id}", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    SchedulerOperationResponse(
                        success = false,
                        message = "Internal server error: ${e.message}"
                    )
                )
            }
        }
    }
}
