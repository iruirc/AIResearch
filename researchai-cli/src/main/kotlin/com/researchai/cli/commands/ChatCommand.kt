package com.researchai.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.researchai.cli.api.ResearchAiClient
import com.researchai.cli.config.CliConfig
import kotlinx.coroutines.runBlocking

class ChatCommand : CliktCommand(
    name = "chat",
    help = "Start interactive chat session"
) {
    private val serverUrlOption by option("--server", "-s", help = "Server URL")

    private val sessionId by option("--session", help = "Session ID to continue")

    private val modelOption by option("--model", "-m", help = "Model to use (e.g., gpt-4-turbo, claude-sonnet-4-5-20241022, llama3:latest)")

    override fun run() = runBlocking {
        val config = CliConfig.load()

        val serverUrl = serverUrlOption ?: config.serverUrl
        val model = modelOption ?: config.defaultModel

        val client = ResearchAiClient(serverUrl)

        echo("ResearchAI CLI v0.1.0")
        model?.let { echo("Model: $it") } ?: echo("Model: server default")
        echo("Connecting to $serverUrl...")

        if (!client.checkHealth()) {
            echo("Error: Cannot connect to server at $serverUrl")
            echo("Make sure the server is running and try again.")
            return@runBlocking
        }

        echo("Connected!")
        echo("Type /exit to quit, /help for commands\n")

        var currentSessionId = sessionId
        var currentModel = model

        while (true) {
            print("You: ")
            val input = readlnOrNull() ?: break

            when {
                input == "/exit" -> {
                    echo("Goodbye!")
                    break
                }
                input == "/help" -> {
                    printHelp()
                }
                input == "/clear" -> {
                    currentSessionId?.let {
                        try {
                            client.clearSession(it)
                            echo("Session cleared")
                        } catch (e: Exception) {
                            echo("Error clearing session: ${e.message}")
                        }
                    } ?: echo("No active session to clear")
                }
                input == "/session" -> {
                    echo("Current session: ${currentSessionId ?: "none"}")
                }
                input == "/new" -> {
                    currentSessionId = null
                    echo("Started new session")
                }
                input == "/model" -> {
                    echo("Current model: ${currentModel ?: "server default"}")
                }
                input.startsWith("/model ") -> {
                    currentModel = input.removePrefix("/model ").trim()
                    echo("Model changed to: $currentModel")
                }
                input.startsWith("/") -> {
                    echo("Unknown command: $input")
                    echo("Type /help for available commands")
                }
                input.isNotBlank() -> {
                    try {
                        val response = client.sendMessage(input, currentSessionId, currentModel)
                        currentSessionId = response.sessionId
                        echo("\nAI: ${response.response}\n")
                    } catch (e: Exception) {
                        echo("Error: ${e.message}")
                    }
                }
            }
        }

        client.close()
    }

    private fun printHelp() {
        echo("""
            |Available commands:
            |  /exit          - Exit the chat
            |  /help          - Show this help message
            |  /clear         - Clear current session history
            |  /session       - Show current session ID
            |  /new           - Start a new session
            |  /model         - Show current model
            |  /model <name>  - Change model (e.g., /model gpt-4-turbo)
        """.trimMargin())
    }
}
