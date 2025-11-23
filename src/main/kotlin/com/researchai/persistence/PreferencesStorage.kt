package com.researchai.persistence

import com.researchai.models.UserPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Storage interface for user preferences
 */
interface PreferencesStorage {
    fun load(): UserPreferences?
    fun save(preferences: UserPreferences): Result<Unit>
    fun delete(): Result<Unit>
}

/**
 * JSON-based implementation of PreferencesStorage
 * Stores preferences in data/preferences/user_preferences.json
 */
class JsonPreferencesStorage(
    private val storageDir: String = "data/preferences"
) : PreferencesStorage {

    private val logger = LoggerFactory.getLogger(JsonPreferencesStorage::class.java)
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val preferencesFile = File("$storageDir/user_preferences.json")

    init {
        // Create storage directory if it doesn't exist
        File(storageDir).mkdirs()
        logger.info("PreferencesStorage initialized at: ${preferencesFile.absolutePath}")
    }

    override fun load(): UserPreferences? {
        return try {
            if (!preferencesFile.exists()) {
                logger.info("No saved preferences found, using defaults")
                return null
            }

            val content = preferencesFile.readText()
            val preferences = json.decodeFromString<UserPreferences>(content)
            logger.info("Loaded user preferences: provider=${preferences.providerId}, model=${preferences.model}")
            preferences
        } catch (e: Exception) {
            logger.error("Failed to load preferences: ${e.message}", e)
            null
        }
    }

    override fun save(preferences: UserPreferences): Result<Unit> {
        return try {
            // Create temporary file for atomic write
            val tempFile = File("$storageDir/user_preferences.tmp")
            val jsonContent = json.encodeToString(preferences)

            tempFile.writeText(jsonContent)

            // Atomic move to final location
            Files.move(
                tempFile.toPath(),
                preferencesFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )

            logger.info("Saved user preferences: provider=${preferences.providerId}, model=${preferences.model}")
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Failed to save preferences: ${e.message}", e)
            Result.failure(e)
        }
    }

    override fun delete(): Result<Unit> {
        return try {
            if (preferencesFile.exists()) {
                preferencesFile.delete()
                logger.info("Deleted user preferences")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Failed to delete preferences: ${e.message}", e)
            Result.failure(e)
        }
    }
}
