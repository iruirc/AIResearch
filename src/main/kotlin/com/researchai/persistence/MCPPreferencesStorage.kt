package com.researchai.persistence

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Storage for MCP server user preferences
 * Stores which servers are enabled/disabled by the user
 */
class MCPPreferencesStorage(
    private val storageDir: File = File("data"),
    private val preferencesFileName: String = "mcp-preferences.json"
) {
    private val logger = LoggerFactory.getLogger(MCPPreferencesStorage::class.java)
    private val mutex = Mutex()

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val preferencesFile: File
        get() = File(storageDir, preferencesFileName)

    init {
        // Create storage directory if it doesn't exist
        if (!storageDir.exists()) {
            storageDir.mkdirs()
            logger.info("Created MCP preferences storage directory: ${storageDir.absolutePath}")
        }
    }

    /**
     * Load MCP preferences from file
     * Returns default preferences if file doesn't exist
     */
    suspend fun loadPreferences(): Result<MCPPreferences> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                if (!preferencesFile.exists()) {
                    logger.info("MCP preferences file not found, returning defaults")
                    return@withContext Result.success(MCPPreferences())
                }

                val preferencesData = json.decodeFromString<MCPPreferences>(preferencesFile.readText())
                logger.info("Loaded MCP preferences: ${preferencesData.enabledServers.size} enabled servers")
                Result.success(preferencesData)
            } catch (e: Exception) {
                logger.error("Failed to load MCP preferences", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Save MCP preferences to file
     */
    suspend fun savePreferences(preferences: MCPPreferences): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val tempFile = File(storageDir, "$preferencesFileName.tmp")

                // Write to temporary file
                tempFile.writeText(json.encodeToString(preferences))

                // Atomically replace the main file
                Files.move(tempFile.toPath(), preferencesFile.toPath(), StandardCopyOption.REPLACE_EXISTING)

                logger.info("Saved MCP preferences: ${preferences.enabledServers.size} enabled servers")
                Result.success(Unit)
            } catch (e: Exception) {
                logger.error("Failed to save MCP preferences", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Enable a specific MCP server
     */
    suspend fun enableServer(serverId: String): Result<Unit> {
        return try {
            val preferences = loadPreferences().getOrThrow()
            val updatedServers = preferences.enabledServers.toMutableList().apply {
                if (!contains(serverId)) {
                    add(serverId)
                }
            }
            savePreferences(preferences.copy(enabledServers = updatedServers))
        } catch (e: Exception) {
            logger.error("Failed to enable server $serverId", e)
            Result.failure(e)
        }
    }

    /**
     * Disable a specific MCP server
     */
    suspend fun disableServer(serverId: String): Result<Unit> {
        return try {
            val preferences = loadPreferences().getOrThrow()
            val updatedServers = preferences.enabledServers.toMutableList().apply {
                remove(serverId)
            }
            savePreferences(preferences.copy(enabledServers = updatedServers))
        } catch (e: Exception) {
            logger.error("Failed to disable server $serverId", e)
            Result.failure(e)
        }
    }

    /**
     * Check if a server is enabled in preferences
     */
    suspend fun isServerEnabled(serverId: String): Boolean {
        return try {
            val preferences = loadPreferences().getOrThrow()
            preferences.enabledServers.contains(serverId)
        } catch (e: Exception) {
            logger.error("Failed to check if server $serverId is enabled", e)
            false
        }
    }

    /**
     * Get all enabled server IDs
     */
    suspend fun getEnabledServers(): List<String> {
        return try {
            val preferences = loadPreferences().getOrThrow()
            preferences.enabledServers
        } catch (e: Exception) {
            logger.error("Failed to get enabled servers", e)
            emptyList()
        }
    }
}

/**
 * MCP user preferences data model
 */
@Serializable
data class MCPPreferences(
    val enabledServers: List<String> = emptyList()
)
