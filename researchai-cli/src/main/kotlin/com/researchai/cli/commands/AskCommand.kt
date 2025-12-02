package com.researchai.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.researchai.cli.api.ResearchAiClient
import com.researchai.cli.config.CliConfig
import com.researchai.cli.config.ProjectConfig
import com.researchai.cli.handlers.AskHandler
import kotlinx.coroutines.runBlocking
import java.io.File

class AskCommand : CliktCommand(
    name = "ask",
    help = "Ask a question using project's RAG knowledge"
) {
    private val questionParts by argument("question", help = "Question to ask").multiple()
    private val serverUrlOption by option("--server", "-s", help = "Server URL")
    private val modelOption by option("--model", "-m", help = "Model to use")

    override fun run() = runBlocking {
        val currentDir = File(System.getProperty("user.dir"))
        val config = CliConfig.load()
        val serverUrl = serverUrlOption ?: config.serverUrl
        val model = modelOption ?: config.defaultModel

        val projectConfig = ProjectConfig.load(currentDir)

        // Build question from arguments
        val question = questionParts.joinToString(" ")
        if (question.isBlank()) {
            echo("Please provide a question.")
            echo("Usage: rai ask <question>")
            return@runBlocking
        }

        val client = ResearchAiClient(serverUrl)

        if (!client.checkHealth()) {
            echo("Error: Cannot connect to server at $serverUrl")
            client.close()
            return@runBlocking
        }

        try {
            val handler = AskHandler(client, projectConfig) { echo(it) }
            val response = handler.ask(question, model)

            response?.let {
                echo(it.response)
                echo("")
            }
        } catch (e: Exception) {
            echo("Error: ${e.message}")
        } finally {
            client.close()
        }
    }
}
