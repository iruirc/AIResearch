package com.researchai.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.researchai.cli.api.ResearchAiClient
import com.researchai.cli.config.CliConfig
import com.researchai.cli.config.ProjectConfig
import com.researchai.cli.handlers.AskHandler
import com.researchai.cli.handlers.GitHandler
import com.researchai.cli.handlers.InitHandler
import kotlinx.coroutines.runBlocking
import java.io.File

class ChatCommand : CliktCommand(
    name = "chat",
    help = "Start interactive chat session"
) {
    private val serverUrlOption by option("--server", "-s", help = "Server URL")

    private val sessionId by option("--session", help = "Session ID to continue")

    private val modelOption by option("--model", "-m", help = "Model to use (e.g., gpt-4-turbo, claude-sonnet-4-5-20241022, llama3:latest)")

    override fun run() = runBlocking {
        val config = CliConfig.load()
        val currentDir = File(System.getProperty("user.dir"))

        val serverUrl = serverUrlOption ?: config.serverUrl
        val model = modelOption ?: config.defaultModel

        // Load project config if initialized (mutable for /init command)
        var projectConfig = ProjectConfig.load(currentDir)

        val client = ResearchAiClient(serverUrl)

        echo("ResearchAI CLI v0.1.0")
        model?.let { echo("Model: $it") } ?: echo("Model: server default")
        if (projectConfig != null) {
            echo("RAG context: ${projectConfig.projectName} (${projectConfig.indexedFiles.size} files)")
        }
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
                // /git command
                input == "/git" || input.startsWith("/git ") -> {
                    val gitInput = input.removePrefix("/git").trim()
                    val gitHandler = GitHandler(client) { echo(it) }

                    try {
                        if (gitInput.isEmpty() || gitInput == "tools") {
                            gitHandler.listTools()
                        } else {
                            val (toolName, args) = gitHandler.parseSlashCommand(gitInput)
                            gitHandler.executeTool(toolName, args)
                        }
                    } catch (e: Exception) {
                        echo("Error: ${e.message}")
                    }
                }
                // /ask command
                input.startsWith("/ask ") -> {
                    val question = input.removePrefix("/ask ").trim()
                    val askHandler = AskHandler(client, projectConfig) { echo(it) }

                    try {
                        val response = askHandler.ask(question, currentModel, currentSessionId)
                        response?.let {
                            currentSessionId = it.sessionId
                            echo("\nAI: ${it.response}\n")
                        }
                    } catch (e: Exception) {
                        echo("Error: ${e.message}")
                    }
                }
                // /init command
                input == "/init" -> {
                    // Add newline to separate progress from prompt
                    echo("")

                    val initHandler = InitHandler(
                        client = client,
                        output = { echo(it) },
                        outputProgress = { progress ->
                            print(progress)
                            System.out.flush()
                        },
                        clearProgressOnComplete = true
                    )

                    try {
                        val result = initHandler.init(
                            currentDir = currentDir,
                            force = true,
                            skipConfirm = true,
                            strategy = "auto"
                        )

                        if (result.success) {
                            // Reload project config after successful init
                            projectConfig = ProjectConfig.load(currentDir)
                            echo("RAG context updated: ${projectConfig?.projectName}")
                        }
                    } catch (e: Exception) {
                        echo("Error: ${e.message}")
                    }
                }
                input.startsWith("/") -> {
                    echo("Unknown command: $input")
                    echo("Type /help for available commands")
                }
                input.isNotBlank() -> {
                    try {
                        // If project is initialized, search RAG for context
                        val messageToSend = if (projectConfig != null) {
                            val searchResults = try {
                                client.searchRag(
                                    query = input,
                                    documentIds = listOf(projectConfig.ragDocumentId),
                                    topK = 3
                                )
                            } catch (e: Exception) {
                                emptyList()
                            }

                            if (searchResults.isNotEmpty()) {
                                val context = searchResults.joinToString("\n\n") { it.text }
                                """
                                |Context from project documentation:
                                |$context
                                |
                                |User question: $input
                                """.trimMargin()
                            } else {
                                input
                            }
                        } else {
                            input
                        }

                        val response = client.sendMessage(messageToSend, currentSessionId, currentModel)
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
            |  /exit              - Exit the chat
            |  /help              - Show this help message
            |  /clear             - Clear current session history
            |  /session           - Show current session ID
            |  /new               - Start a new session
            |  /model             - Show current model
            |  /model <name>      - Change model (e.g., /model gpt-4-turbo)
            |  /git               - List available GitHub tools
            |  /git <tool> [args] - Execute GitHub tool (e.g., /git get_me)
            |  /ask <question>    - Ask question using RAG knowledge
            |  /init              - Initialize/reinitialize project RAG
        """.trimMargin())
    }
}
