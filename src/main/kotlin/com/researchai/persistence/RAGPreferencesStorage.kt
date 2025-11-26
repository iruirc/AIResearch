package com.researchai.persistence

import com.researchai.models.RAGSearchPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Interface for RAG search preferences storage
 */
interface RAGPreferencesStorage {
    suspend fun load(): RAGSearchPreferences
    suspend fun save(preferences: RAGSearchPreferences)
    suspend fun reset(): RAGSearchPreferences
}

/**
 * JSON file-based implementation of RAG preferences storage
 */
class JsonRAGPreferencesStorage(
    private val storageDir: File = File("data"),
    private val fileName: String = "rag_search_preferences.json"
) : RAGPreferencesStorage {

    private val logger = LoggerFactory.getLogger(JsonRAGPreferencesStorage::class.java)
    private val mutex = Mutex()
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val file: File
        get() = File(storageDir, fileName)

    init {
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
    }

    override suspend fun load(): RAGSearchPreferences = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                if (file.exists()) {
                    val content = file.readText()
                    json.decodeFromString<RAGSearchPreferences>(content)
                } else {
                    logger.info("RAG preferences file not found, using defaults")
                    RAGSearchPreferences.default()
                }
            } catch (e: Exception) {
                logger.error("Failed to load RAG preferences, using defaults", e)
                RAGSearchPreferences.default()
            }
        }
    }

    override suspend fun save(preferences: RAGSearchPreferences) = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val content = json.encodeToString(RAGSearchPreferences.serializer(), preferences)
                file.writeText(content)
                logger.debug("RAG preferences saved successfully")
            } catch (e: Exception) {
                logger.error("Failed to save RAG preferences", e)
                throw e
            }
        }
    }

    override suspend fun reset(): RAGSearchPreferences = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                if (file.exists()) {
                    file.delete()
                }
                val defaults = RAGSearchPreferences.default()
                val content = json.encodeToString(RAGSearchPreferences.serializer(), defaults)
                file.writeText(content)
                logger.info("RAG preferences reset to defaults")
                defaults
            } catch (e: Exception) {
                logger.error("Failed to reset RAG preferences", e)
                throw e
            }
        }
    }
}
