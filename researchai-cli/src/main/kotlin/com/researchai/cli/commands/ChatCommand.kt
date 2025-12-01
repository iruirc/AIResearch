package com.researchai.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.researchai.cli.api.ResearchAiClient
import kotlinx.coroutines.runBlocking

class ChatCommand : CliktCommand(
    name = "chat",
    help = "Start interactive chat session"
) {
    private val serverUrl by option("--server", "-s", help = "Server URL")
        .default("http://localhost:8080")

    private val sessionId by option("--session", help = "Session ID to continue")

    override fun run() = runBlocking {
        val client = ResearchAiClient(serverUrl)

        echo("ResearchAI CLI v0.1.0")
        echo("Connecting to $serverUrl...")

        if (!client.checkHealth()) {
            echo("Error: Cannot connect to server at $serverUrl")
            echo("Make sure the server is running and try again.")
            return@runBlocking
        }

        echo("Connected!")
        echo("Type /exit to quit, /help for commands\n")

        var currentSessionId = sessionId

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
                input.startsWith("/") -> {
                    echo("Unknown command: $input")
                    echo("Type /help for available commands")
                }
                input.isNotBlank() -> {
                    try {
                        val response = client.sendMessage(input, currentSessionId)
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
            |  /exit     - Exit the chat
            |  /help     - Show this help message
            |  /clear    - Clear current session history
            |  /session  - Show current session ID
            |  /new      - Start a new session
        """.trimMargin())
    }
}
