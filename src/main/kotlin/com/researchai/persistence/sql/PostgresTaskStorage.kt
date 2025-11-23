package com.researchai.persistence.sql

import com.researchai.domain.models.ProviderType
import com.researchai.scheduler.ScheduledChatTask
import com.researchai.persistence.sql.DatabaseFactory.dbQuery
import com.researchai.persistence.sql.tables.ScheduledTasksTable
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.slf4j.LoggerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PostgreSQL implementation for ScheduledChatTask storage
 * Uses Exposed ORM for type-safe database queries
 */
class PostgresTaskStorage {
    private val logger = LoggerFactory.getLogger(PostgresTaskStorage::class.java)

    /**
     * Saves a scheduled task to the database
     */
    suspend fun saveTask(task: ScheduledChatTask): Result<Unit> = dbQuery {
        try {
            ScheduledTasksTable.upsert {
                it[id] = task.id
                it[title] = task.title
                it[taskRequest] = task.taskRequest
                it[intervalSeconds] = task.intervalSeconds
                it[executeImmediately] = task.executeImmediately
                it[providerId] = task.providerId?.toString()
                it[model] = task.model
                it[createdAt] = Instant.fromEpochMilliseconds(task.createdAt)
                it[updatedAt] = Clock.System.now()
            }

            logger.debug("Saved task: ${task.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Failed to save task ${task.id}", e)
            Result.failure(e)
        }
    }

    /**
     * Loads a task from the database by ID
     */
    suspend fun loadTask(taskId: String): Result<ScheduledChatTask> = dbQuery {
        try {
            val row = ScheduledTasksTable.selectAll()
                .where { ScheduledTasksTable.id eq taskId }
                .singleOrNull()

            if (row == null) {
                return@dbQuery Result.failure(IllegalArgumentException("Task not found: $taskId"))
            }

            val task = ScheduledChatTask(
                id = row[ScheduledTasksTable.id],
                title = row[ScheduledTasksTable.title],
                taskRequest = row[ScheduledTasksTable.taskRequest],
                intervalSeconds = row[ScheduledTasksTable.intervalSeconds],
                executeImmediately = row[ScheduledTasksTable.executeImmediately],
                providerId = row[ScheduledTasksTable.providerId]?.let { ProviderType.valueOf(it) },
                model = row[ScheduledTasksTable.model],
                createdAt = row[ScheduledTasksTable.createdAt].toEpochMilliseconds(),
                sessionId = "" // Will be set by scheduler
            )

            logger.debug("Loaded task: $taskId")
            Result.success(task)
        } catch (e: Exception) {
            logger.error("Failed to load task $taskId", e)
            Result.failure(e)
        }
    }

    /**
     * Loads all tasks from the database
     */
    suspend fun loadAllTasks(): Result<List<ScheduledChatTask>> = dbQuery {
        try {
            val tasks = ScheduledTasksTable.selectAll()
                .map { row ->
                    ScheduledChatTask(
                        id = row[ScheduledTasksTable.id],
                        title = row[ScheduledTasksTable.title],
                        taskRequest = row[ScheduledTasksTable.taskRequest],
                        intervalSeconds = row[ScheduledTasksTable.intervalSeconds],
                        executeImmediately = row[ScheduledTasksTable.executeImmediately],
                        providerId = row[ScheduledTasksTable.providerId]?.let { ProviderType.valueOf(it) },
                        model = row[ScheduledTasksTable.model],
                        createdAt = row[ScheduledTasksTable.createdAt].toEpochMilliseconds(),
                        sessionId = "" // Will be set by scheduler
                    )
                }

            logger.info("Loaded ${tasks.size} tasks from storage")
            Result.success(tasks)
        } catch (e: Exception) {
            logger.error("Failed to load tasks", e)
            Result.failure(e)
        }
    }

    /**
     * Deletes a task from the database
     */
    suspend fun deleteTask(taskId: String): Result<Unit> = dbQuery {
        try {
            ScheduledTasksTable.deleteWhere { id eq taskId }
            logger.info("Deleted task: $taskId")
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Failed to delete task $taskId", e)
            Result.failure(e)
        }
    }
}
