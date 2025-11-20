package com.researchai.persistence

import com.researchai.models.Assistant
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
 * Хранилище для ассистентов
 * Сохраняет ассистентов в JSON файлы
 */
class JsonAssistantStorage(
    private val storagePath: String = "data/assistants"
) : AssistantStorage {
    private val logger = LoggerFactory.getLogger(JsonAssistantStorage::class.java)

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    init {
        // Создаем директорию если не существует
        File(storagePath).mkdirs()
        logger.info("Assistant storage initialized at: $storagePath")
    }

    /**
     * Сохраняет ассистента в файл
     */
    override suspend fun saveAssistant(assistant: Assistant): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val filePath = getAssistantFilePath(assistant.id)
            val jsonContent = json.encodeToString(assistant)

            // Atomic write - сначала во временный файл, затем переименовываем
            val tempFile = File("$filePath.tmp")
            tempFile.writeText(jsonContent)

            Files.move(
                tempFile.toPath(),
                Path(filePath),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )

            logger.debug("Saved assistant: ${assistant.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Failed to save assistant ${assistant.id}", e)
            Result.failure(e)
        }
    }

    /**
     * Загружает ассистента из файла
     */
    override suspend fun loadAssistant(assistantId: String): Result<Assistant?> = withContext(Dispatchers.IO) {
        try {
            val filePath = getAssistantFilePath(assistantId)
            val file = File(filePath)

            if (!file.exists()) {
                return@withContext Result.success(null)
            }

            val jsonContent = file.readText()
            val assistant = json.decodeFromString<Assistant>(jsonContent)

            logger.debug("Loaded assistant: $assistantId")
            Result.success(assistant)
        } catch (e: Exception) {
            logger.error("Failed to load assistant $assistantId", e)
            Result.failure(e)
        }
    }

    /**
     * Загружает всех ассистентов
     */
    override suspend fun loadAllAssistants(): Result<List<Assistant>> = withContext(Dispatchers.IO) {
        try {
            val dir = File(storagePath)
            if (!dir.exists() || !dir.isDirectory) {
                return@withContext Result.success(emptyList())
            }

            val assistants = dir.listFiles { file ->
                file.isFile && file.name.endsWith(".json")
            }?.mapNotNull { file ->
                try {
                    val jsonContent = file.readText()
                    json.decodeFromString<Assistant>(jsonContent)
                } catch (e: Exception) {
                    logger.error("Failed to load assistant from file ${file.name}", e)
                    null
                }
            } ?: emptyList()

            logger.info("Loaded ${assistants.size} assistants from storage")
            Result.success(assistants)
        } catch (e: Exception) {
            logger.error("Failed to load assistants", e)
            Result.failure(e)
        }
    }

    /**
     * Удаляет ассистента
     */
    override suspend fun deleteAssistant(assistantId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val filePath = getAssistantFilePath(assistantId)
            val file = File(filePath)

            if (file.exists()) {
                file.delete()
                logger.info("Deleted assistant file: $assistantId")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Failed to delete assistant $assistantId", e)
            Result.failure(e)
        }
    }

    /**
     * Проверяет существование ассистента
     */
    override suspend fun assistantExists(assistantId: String): Boolean = withContext(Dispatchers.IO) {
        val filePath = getAssistantFilePath(assistantId)
        File(filePath).exists()
    }

    /**
     * Закрывает ресурсы хранилища
     */
    override suspend fun close() {
        logger.info("Assistant storage closed")
    }

    /**
     * Получает путь к файлу ассистента
     */
    private fun getAssistantFilePath(assistantId: String): String {
        return "$storagePath/$assistantId.json"
    }
}
