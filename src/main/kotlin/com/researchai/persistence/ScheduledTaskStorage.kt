package com.researchai.persistence

import com.researchai.scheduler.ScheduledChatTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.io.path.Path

/**
 * Хранилище для запланированных задач
 * Сохраняет задачи в JSON файлы
 */
class ScheduledTaskStorage(
    private val storagePath: String = "data/scheduled_tasks"
) {
    private val logger = LoggerFactory.getLogger(ScheduledTaskStorage::class.java)

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    init {
        // Создаем директорию если не существует
        File(storagePath).mkdirs()
        logger.info("Scheduled task storage initialized at: $storagePath")
    }

    /**
     * Сохраняет задачу в файл
     */
    suspend fun saveTask(task: ScheduledChatTask): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val filePath = getTaskFilePath(task.id)
            val jsonContent = json.encodeToString(task)

            // Atomic write - сначала во временный файл, затем переименовываем
            val tempFile = File("$filePath.tmp")
            tempFile.writeText(jsonContent)

            Files.move(
                tempFile.toPath(),
                Path(filePath),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )

            logger.debug("Saved task: ${task.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Failed to save task ${task.id}", e)
            Result.failure(e)
        }
    }

    /**
     * Загружает задачу из файла
     */
    suspend fun loadTask(taskId: String): Result<ScheduledChatTask> = withContext(Dispatchers.IO) {
        try {
            val filePath = getTaskFilePath(taskId)
            val file = File(filePath)

            if (!file.exists()) {
                return@withContext Result.failure(IllegalArgumentException("Task file not found: $taskId"))
            }

            val jsonContent = file.readText()
            val task = json.decodeFromString<ScheduledChatTask>(jsonContent)

            logger.debug("Loaded task: $taskId")
            Result.success(task)
        } catch (e: Exception) {
            logger.error("Failed to load task $taskId", e)
            Result.failure(e)
        }
    }

    /**
     * Загружает все задачи
     */
    suspend fun loadAllTasks(): Result<List<ScheduledChatTask>> = withContext(Dispatchers.IO) {
        try {
            val dir = File(storagePath)
            if (!dir.exists() || !dir.isDirectory) {
                return@withContext Result.success(emptyList())
            }

            val tasks = dir.listFiles { file ->
                file.isFile && file.name.endsWith(".json")
            }?.mapNotNull { file ->
                try {
                    val jsonContent = file.readText()
                    json.decodeFromString<ScheduledChatTask>(jsonContent)
                } catch (e: Exception) {
                    logger.error("Failed to load task from file ${file.name}", e)
                    null
                }
            } ?: emptyList()

            logger.info("Loaded ${tasks.size} tasks from storage")
            Result.success(tasks)
        } catch (e: Exception) {
            logger.error("Failed to load tasks", e)
            Result.failure(e)
        }
    }

    /**
     * Удаляет задачу
     */
    suspend fun deleteTask(taskId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val filePath = getTaskFilePath(taskId)
            val file = File(filePath)

            if (file.exists()) {
                file.delete()
                logger.info("Deleted task file: $taskId")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Failed to delete task $taskId", e)
            Result.failure(e)
        }
    }

    /**
     * Получает путь к файлу задачи
     */
    private fun getTaskFilePath(taskId: String): String {
        return "$storagePath/$taskId.json"
    }
}
