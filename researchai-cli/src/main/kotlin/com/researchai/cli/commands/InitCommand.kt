package com.researchai.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.researchai.cli.api.ResearchAiClient
import com.researchai.cli.config.CliConfig
import com.researchai.cli.config.ProjectConfig
import com.researchai.cli.strategy.DiscoveryStrategyFactory
import kotlinx.coroutines.runBlocking
import java.io.File

class InitCommand : CliktCommand(
    name = "init",
    help = "Initialize project and create RAG knowledge base"
) {
    private val serverUrlOption by option("--server", "-s", help = "Server URL")
    private val forceOption by option("--force", "-f", help = "Reinitialize existing project").flag()
    private val yesOption by option("--yes", "-y", help = "Skip confirmation prompt").flag()

    override fun run() = runBlocking {
        val currentDir = File(System.getProperty("user.dir"))
        val config = CliConfig.load()
        val serverUrl = serverUrlOption ?: config.serverUrl

        // 1. Check for existing initialization
        if (ProjectConfig.isInitialized(currentDir) && !forceOption) {
            val existingConfig = ProjectConfig.load(currentDir)
            echo("Project already initialized.")
            echo("RAG Document ID: ${existingConfig?.ragDocumentId}")
            echo("Indexed files: ${existingConfig?.indexedFiles?.size ?: 0}")
            echo("Use --force to reinitialize.")
            return@runBlocking
        }

        // 2. User confirmation
        if (!yesOption) {
            echo("Project directory: ${currentDir.absolutePath}")
            print("Index this project? [y/N]: ")
            val confirm = readlnOrNull()?.trim()?.lowercase()
            if (confirm != "y" && confirm != "yes") {
                echo("Cancelled.")
                return@runBlocking
            }
        }

        // 3. Discover files
        echo("Scanning for files...")
        val strategy = DiscoveryStrategyFactory.default()
        val discoveredFiles = strategy.discover(currentDir)

        if (discoveredFiles.isEmpty()) {
            echo("No files found to index.")
            echo("Expected: README.md in root or Documents/ folder with .md, .txt, .json, .xml, .log files")
            return@runBlocking
        }

        echo("Found ${discoveredFiles.size} file(s):")
        discoveredFiles.forEach { echo("  - ${it.relativePath}") }

        // 4. Connect to server
        val client = ResearchAiClient(serverUrl)

        if (!client.checkHealth()) {
            echo("Error: Cannot connect to server at $serverUrl")
            client.close()
            return@runBlocking
        }

        // 5. Create RAG document
        echo("\nCreating RAG knowledge base...")

        try {
            // Combine all file contents
            val combinedContent = discoveredFiles.joinToString("\n\n---\n\n") { file ->
                "# ${file.relativePath}\n\n${file.file.readText()}"
            }

            // Create source files for API (replace / with _ in fileName to avoid path issues on server)
            val sourceFiles = discoveredFiles.map {
                it.relativePath.replace("/", "_").replace("\\", "_") to it.file.readText()
            }

            val document = client.createRagDocument(
                name = currentDir.name,
                content = combinedContent,
                sourceFiles = sourceFiles
            )

            // 6. Save project configuration
            val projectConfig = ProjectConfig.create(
                ragDocumentId = document.id,
                projectName = currentDir.name,
                indexedFiles = discoveredFiles.map { it.relativePath }
            )
            ProjectConfig.save(currentDir, projectConfig)

            echo("\nProject initialized successfully!")
            echo("RAG Document ID: ${document.id}")
            echo("Config saved to: ${ProjectConfig.getConfigDir(currentDir).path}/config.json")

        } catch (e: Exception) {
            echo("Error creating RAG document: ${e.message}")
        } finally {
            client.close()
        }
    }
}
