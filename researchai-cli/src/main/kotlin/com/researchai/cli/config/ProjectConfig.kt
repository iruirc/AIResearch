package com.researchai.cli.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant

/**
 * Project configuration stored in .researchCLI/config.json
 * Contains information about the linked RAG document on the server.
 */
@Serializable
data class ProjectConfig(
    val version: String = "1.0",
    val ragDocumentId: String,
    val projectName: String,
    val indexedAt: String,
    val indexedFiles: List<String>
) {
    companion object {
        private const val CONFIG_DIR_NAME = ".researchCLI"
        private const val CONFIG_FILE_NAME = "config.json"

        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }

        /**
         * Get the configuration directory for a project
         */
        fun getConfigDir(projectDir: File): File {
            return File(projectDir, CONFIG_DIR_NAME)
        }

        /**
         * Check if project is initialized (has .researchCLI/config.json)
         */
        fun isInitialized(projectDir: File): Boolean {
            val configFile = File(getConfigDir(projectDir), CONFIG_FILE_NAME)
            return configFile.exists()
        }

        /**
         * Load project configuration
         * @return ProjectConfig or null if not initialized or invalid
         */
        fun load(projectDir: File): ProjectConfig? {
            val configFile = File(getConfigDir(projectDir), CONFIG_FILE_NAME)
            if (!configFile.exists()) return null

            return try {
                json.decodeFromString<ProjectConfig>(configFile.readText())
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Save project configuration
         */
        fun save(projectDir: File, config: ProjectConfig) {
            val configDir = getConfigDir(projectDir)
            configDir.mkdirs()

            val configFile = File(configDir, CONFIG_FILE_NAME)
            configFile.writeText(json.encodeToString(config))
        }

        /**
         * Delete project configuration
         */
        fun delete(projectDir: File): Boolean {
            val configDir = getConfigDir(projectDir)
            return if (configDir.exists()) {
                configDir.deleteRecursively()
            } else {
                false
            }
        }

        /**
         * Create a new project configuration
         */
        fun create(
            ragDocumentId: String,
            projectName: String,
            indexedFiles: List<String>
        ): ProjectConfig {
            return ProjectConfig(
                version = "1.0",
                ragDocumentId = ragDocumentId,
                projectName = projectName,
                indexedAt = Instant.now().toString(),
                indexedFiles = indexedFiles
            )
        }
    }
}
