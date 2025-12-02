package com.researchai.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.researchai.cli.api.ResearchAiClient
import com.researchai.cli.config.CliConfig
import com.researchai.cli.config.ProjectConfig
import com.researchai.cli.handlers.InitHandler
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

        // User confirmation (unless --yes)
        if (!yesOption && !ProjectConfig.isInitialized(currentDir)) {
            echo("Project directory: ${currentDir.absolutePath}")
            print("Index this project? [y/N]: ")
            val confirm = readlnOrNull()?.trim()?.lowercase()
            if (confirm != "y" && confirm != "yes") {
                echo("Cancelled.")
                return@runBlocking
            }
        }

        val client = ResearchAiClient(serverUrl)

        if (!client.checkHealth()) {
            echo("Error: Cannot connect to server at $serverUrl")
            client.close()
            return@runBlocking
        }

        try {
            val handler = InitHandler(
                client = client,
                output = { echo(it) },
                outputProgress = { progress ->
                    // Use \r for inline progress update in terminal
                    print(progress)
                    System.out.flush()
                }
            )

            val result = handler.init(
                currentDir = currentDir,
                force = forceOption,
                skipConfirm = true // Already confirmed above
            )

            // Clear the progress line after completion
            if (result.success) {
                print("\r${" ".repeat(80)}\r")
            }

        } finally {
            client.close()
        }
    }
}
