package com.researchai.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.researchai.cli.api.ResearchAiClient
import com.researchai.cli.config.CliConfig
import com.researchai.cli.config.ProjectConfig
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

        // 1. Check initialization
        if (!ProjectConfig.isInitialized(currentDir)) {
            echo("Project not initialized. Run 'rai init' first.")
            return@runBlocking
        }

        val projectConfig = ProjectConfig.load(currentDir)
        if (projectConfig == null) {
            echo("Error reading project configuration.")
            return@runBlocking
        }

        // 2. Build question from arguments
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
            // 3. Search in RAG
            echo("Searching knowledge base...")
            val searchResults = client.searchRag(
                query = question,
                documentIds = listOf(projectConfig.ragDocumentId),
                topK = 5
            )

            if (searchResults.isEmpty()) {
                echo("No relevant context found in knowledge base.")
                echo("Asking without context...\n")
            }

            // 4. Build context
            val context = if (searchResults.isNotEmpty()) {
                searchResults.joinToString("\n\n") { result ->
                    "---\n${result.text}\n---"
                }
            } else null

            // 5. Send question with context
            val messageWithContext = if (context != null) {
                """
                |Based on the following context from project documentation:
                |
                |$context
                |
                |Please answer: $question
                """.trimMargin()
            } else {
                question
            }

            echo("Asking AI...\n")
            val response = client.sendMessage(messageWithContext, sessionId = null, model = model)

            echo(response.response)
            echo("")

        } catch (e: Exception) {
            echo("Error: ${e.message}")
        } finally {
            client.close()
        }
    }
}
