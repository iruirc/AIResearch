package com.researchai.services

import com.researchai.models.UserPreferences
import com.researchai.persistence.PreferencesStorage
import org.slf4j.LoggerFactory

/**
 * Manager for user preferences
 * Handles loading, saving, and resetting user preferences
 */
class PreferencesManager(
    private val storage: PreferencesStorage
) {
    private val logger = LoggerFactory.getLogger(PreferencesManager::class.java)

    @Volatile
    private var currentPreferences: UserPreferences? = null

    init {
        // Load preferences on initialization
        loadPreferences()
    }

    /**
     * Load preferences from storage
     * @return UserPreferences or default if not found
     */
    fun loadPreferences(): UserPreferences {
        if (currentPreferences != null) {
            return currentPreferences!!
        }

        val loaded = storage.load()
        currentPreferences = loaded ?: UserPreferences.default()

        if (loaded == null) {
            logger.info("No saved preferences found, using defaults")
        } else {
            logger.info("Loaded preferences: provider=${loaded.providerId}, model=${loaded.model}")
        }

        return currentPreferences!!
    }

    /**
     * Get current preferences (from cache)
     * @return Current UserPreferences
     */
    fun getPreferences(): UserPreferences {
        return currentPreferences ?: loadPreferences()
    }

    /**
     * Save preferences to storage
     * @param preferences UserPreferences to save
     * @return Result of save operation
     */
    fun savePreferences(preferences: UserPreferences): Result<Unit> {
        val updatedPreferences = preferences.copy(updatedAt = System.currentTimeMillis())

        return storage.save(updatedPreferences).also { result ->
            result.onSuccess {
                currentPreferences = updatedPreferences
                logger.info("Preferences saved: provider=${updatedPreferences.providerId}, model=${updatedPreferences.model}")
            }.onFailure { error ->
                logger.error("Failed to save preferences", error)
            }
        }
    }

    /**
     * Reset preferences to defaults
     * @return Result of reset operation
     */
    fun resetToDefaults(): Result<Unit> {
        return storage.delete().also { result ->
            result.onSuccess {
                currentPreferences = UserPreferences.default()
                logger.info("Preferences reset to defaults")
            }.onFailure { error ->
                logger.error("Failed to reset preferences", error)
            }
        }
    }

    /**
     * Get default preferences without loading from storage
     * @return Default UserPreferences
     */
    fun getDefaults(): UserPreferences {
        return UserPreferences.default()
    }
}
