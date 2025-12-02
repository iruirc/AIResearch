package com.researchai.cli.handlers

import com.researchai.cli.api.CompleteData
import com.researchai.cli.api.ProgressData
import com.researchai.cli.api.ResearchAiClient
import com.researchai.cli.config.ProjectConfig
import com.researchai.cli.strategy.DiscoveryStrategyFactory
import java.io.File

/**
 * Handler for project initialization and RAG document creation.
 * Used by both top-level InitCommand and /init slash command in chat.
 */
class InitHandler(
    private val client: ResearchAiClient,
    private val output: (String) -> Unit,
    private val outputProgress: ((String) -> Unit)? = null
) {
    /**
     * Result of initialization
     */
    data class InitResult(
        val success: Boolean,
        val documentId: String? = null,
        val chunksCount: Int = 0,
        val error: String? = null
    )

    /**
     * Initialize or reinitialize project RAG.
     *
     * @param currentDir Project directory
     * @param force If true, reinitialize even if already initialized
     * @param skipConfirm If true, skip user confirmation
     * @return InitResult with status and document info
     */
    suspend fun init(
        currentDir: File,
        force: Boolean = false,
        skipConfirm: Boolean = false
    ): InitResult {
        // 1. Check for existing initialization
        if (ProjectConfig.isInitialized(currentDir) && !force) {
            val existingConfig = ProjectConfig.load(currentDir)
            output("Project already initialized.")
            output("RAG Document ID: ${existingConfig?.ragDocumentId}")
            output("Indexed files: ${existingConfig?.indexedFiles?.size ?: 0}")
            output("Use --force or /init to reinitialize.")
            return InitResult(success = false, error = "Already initialized")
        }

        // 2. Discover files
        output("Scanning for files...")
        val strategy = DiscoveryStrategyFactory.default()
        val discoveredFiles = strategy.discover(currentDir)

        if (discoveredFiles.isEmpty()) {
            output("No files found to index.")
            output("Expected: README.md in root or Documents/ folder with .md, .txt, .json, .xml, .log files")
            return InitResult(success = false, error = "No files found")
        }

        output("Found ${discoveredFiles.size} file(s):")
        discoveredFiles.forEach { output("  - ${it.relativePath}") }

        // 3. Create RAG document with progress
        output("\nCreating RAG knowledge base...")

        return try {
            // Combine all file contents
            val combinedContent = discoveredFiles.joinToString("\n\n---\n\n") { file ->
                "# ${file.relativePath}\n\n${file.file.readText()}"
            }

            // Create source files for API
            val sourceFiles = discoveredFiles.map {
                it.relativePath.replace("/", "_").replace("\\", "_") to it.file.readText()
            }

            var completedDocument: CompleteData? = null
            var errorMessage: String? = null

            client.createRagDocumentWithProgress(
                name = currentDir.name,
                content = combinedContent,
                sourceFiles = sourceFiles,
                onProgress = { progress ->
                    outputProgress?.invoke(formatProgress(progress))
                        ?: output(formatProgressLine(progress))
                },
                onComplete = { complete ->
                    completedDocument = complete
                },
                onError = { error ->
                    errorMessage = error
                }
            )

            if (errorMessage != null) {
                output("Error: $errorMessage")
                return InitResult(success = false, error = errorMessage)
            }

            val document = completedDocument
            if (document == null) {
                output("Error: No response from server")
                return InitResult(success = false, error = "No response from server")
            }

            // 4. Save project configuration
            val projectConfig = ProjectConfig.create(
                ragDocumentId = document.documentId,
                projectName = currentDir.name,
                indexedFiles = discoveredFiles.map { it.relativePath }
            )
            ProjectConfig.save(currentDir, projectConfig)

            output("\nProject initialized successfully!")
            output("RAG Document ID: ${document.documentId}")
            output("Chunks created: ${document.chunksCount}")
            output("Config saved to: ${ProjectConfig.getConfigDir(currentDir).path}/config.json")

            InitResult(
                success = true,
                documentId = document.documentId,
                chunksCount = document.chunksCount
            )

        } catch (e: Exception) {
            output("\nError creating RAG document: ${e.message}")
            InitResult(success = false, error = e.message)
        }
    }

    /**
     * Format progress for inline update (with \r for terminal)
     */
    private fun formatProgress(progress: ProgressData): String {
        val percent = progress.percent ?: 0
        val bar = buildProgressBar(percent)
        val phaseText = formatPhase(progress)
        return "\r$bar $percent% - $phaseText${" ".repeat(20)}"
    }

    /**
     * Format progress as a simple line (for chat output)
     */
    private fun formatProgressLine(progress: ProgressData): String {
        val percent = progress.percent ?: 0
        val phaseText = formatPhase(progress)
        return "[$percent%] $phaseText"
    }

    private fun formatPhase(progress: ProgressData): String {
        return when (progress.phase) {
            "saving_files" -> "Saving source files"
            "chunking" -> "Splitting into chunks"
            "embedding" -> {
                val current = progress.current ?: 0
                val total = progress.total ?: 0
                "Generating embeddings ($current/$total)"
            }
            "indexing" -> "Indexing document"
            "saving" -> "Saving to storage"
            else -> progress.message ?: progress.phase
        }
    }

    private fun buildProgressBar(percent: Int, width: Int = 30): String {
        val filled = (width * percent) / 100
        val empty = width - filled
        return "[" + "█".repeat(filled) + "░".repeat(empty) + "]"
    }
}
