package com.researchai.persistence.sql

import com.researchai.persistence.*
import org.slf4j.LoggerFactory

/**
 * Factory for creating storage implementations based on configuration
 * Supports both PostgreSQL and JSON-based storage with automatic fallback
 */
object StorageFactory {
    private val logger = LoggerFactory.getLogger(StorageFactory::class.java)

    /**
     * Creates PersistenceStorage based on ENABLE_POSTGRES flag
     * Falls back to JSON storage if PostgreSQL is unavailable
     */
    fun createPersistenceStorage(enablePostgres: Boolean): PersistenceStorage {
        return if (enablePostgres) {
            try {
                logger.info("Attempting to use PostgreSQL for persistence storage...")
                PostgresPersistenceStorage()
            } catch (e: Exception) {
                logger.error("Failed to initialize PostgreSQL persistence storage, falling back to JSON", e)
                JsonPersistenceStorage()
            }
        } else {
            logger.info("Using JSON-based persistence storage (PostgreSQL disabled)")
            JsonPersistenceStorage()
        }
    }

    /**
     * Creates AssistantStorage based on ENABLE_POSTGRES flag
     * Falls back to JSON storage if PostgreSQL is unavailable
     */
    fun createAssistantStorage(enablePostgres: Boolean): AssistantStorage {
        return if (enablePostgres) {
            try {
                logger.info("Attempting to use PostgreSQL for assistant storage...")
                PostgresAssistantStorage()
            } catch (e: Exception) {
                logger.error("Failed to initialize PostgreSQL assistant storage, falling back to JSON", e)
                JsonAssistantStorage()
            }
        } else {
            logger.info("Using JSON-based assistant storage (PostgreSQL disabled)")
            JsonAssistantStorage()
        }
    }
}
