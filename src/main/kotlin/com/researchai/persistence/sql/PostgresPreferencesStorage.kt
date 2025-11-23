package com.researchai.persistence.sql

import com.researchai.persistence.sql.DatabaseFactory.dbQuery
import com.researchai.persistence.sql.tables.UserPreferencesTable
import org.jetbrains.exposed.sql.*
import org.slf4j.LoggerFactory

/**
 * User preferences model
 */
data class UserPreferences(
    val id: Int? = null,
    val userId: String,
    val providerId: String, // AI provider (CLAUDE, OPENAI, etc.)
    val model: String,
    val temperature: Double = 1.0,
    val maxTokens: Int = 4096,
    val format: String = "PLAIN_TEXT", // Response format (PLAIN_TEXT, MARKDOWN, JSON)
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * PostgreSQL implementation for UserPreferences storage
 * Stores user-specific AI provider and model preferences
 */
class PostgresPreferencesStorage {
    private val logger = LoggerFactory.getLogger(PostgresPreferencesStorage::class.java)

    /**
     * Saves or updates user preferences
     */
    suspend fun savePreferences(preferences: UserPreferences): Result<Unit> = dbQuery {
        try {
            UserPreferencesTable.upsert {
                it[userId] = preferences.userId
                it[providerId] = preferences.providerId
                it[model] = preferences.model
                it[temperature] = preferences.temperature
                it[maxTokens] = preferences.maxTokens
                it[format] = preferences.format
                it[updatedAt] = java.time.Instant.ofEpochMilli(preferences.updatedAt)
            }

            logger.debug("Saved preferences for user: ${preferences.userId}")
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Failed to save preferences for user ${preferences.userId}", e)
            Result.failure(e)
        }
    }

    /**
     * Loads preferences for a user
     */
    suspend fun loadPreferences(userId: String): Result<UserPreferences?> = dbQuery {
        try {
            val row = UserPreferencesTable.selectAll()
                .where { UserPreferencesTable.userId eq userId }
                .singleOrNull()

            val preferences = row?.let {
                UserPreferences(
                    id = it[UserPreferencesTable.id],
                    userId = it[UserPreferencesTable.userId],
                    providerId = it[UserPreferencesTable.providerId],
                    model = it[UserPreferencesTable.model],
                    temperature = it[UserPreferencesTable.temperature],
                    maxTokens = it[UserPreferencesTable.maxTokens],
                    format = it[UserPreferencesTable.format],
                    updatedAt = it[UserPreferencesTable.updatedAt].toEpochMilli()
                )
            }

            logger.debug("Loaded preferences for user: $userId, found: ${preferences != null}")
            Result.success(preferences)
        } catch (e: Exception) {
            logger.error("Failed to load preferences for user $userId", e)
            Result.failure(e)
        }
    }

    /**
     * Deletes preferences for a user
     */
    suspend fun deletePreferences(userId: String): Result<Unit> = dbQuery {
        try {
            UserPreferencesTable.deleteWhere { UserPreferencesTable.userId eq userId }
            logger.info("Deleted preferences for user: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Failed to delete preferences for user $userId", e)
            Result.failure(e)
        }
    }

    /**
     * Updates specific preference fields
     */
    suspend fun updateProvider(userId: String, providerId: String, model: String): Result<Unit> = dbQuery {
        try {
            UserPreferencesTable.update({ UserPreferencesTable.userId eq userId }) {
                it[UserPreferencesTable.providerId] = providerId
                it[UserPreferencesTable.model] = model
                it[updatedAt] = java.time.Instant.now()
            }

            logger.debug("Updated provider preferences for user: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Failed to update provider for user $userId", e)
            Result.failure(e)
        }
    }

    /**
     * Updates model parameters (temperature, maxTokens)
     */
    suspend fun updateParameters(userId: String, temperature: Double, maxTokens: Int): Result<Unit> = dbQuery {
        try {
            UserPreferencesTable.update({ UserPreferencesTable.userId eq userId }) {
                it[UserPreferencesTable.temperature] = temperature
                it[UserPreferencesTable.maxTokens] = maxTokens
                it[updatedAt] = java.time.Instant.now()
            }

            logger.debug("Updated parameters for user: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Failed to update parameters for user $userId", e)
            Result.failure(e)
        }
    }
}
