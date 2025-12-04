package com.researchai.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.researchai.cli.api.ResearchAiClient
import com.researchai.cli.config.CliConfig
import com.researchai.cli.handlers.SupportHandler
import kotlinx.coroutines.runBlocking

/**
 * CLI command for tech support queries
 *
 * Usage:
 *   rai support "Why isn't authentication working?"
 *   rai support --session my-session "Check status of my ticket"
 *   rai support --no-rag "Create a ticket for login issue"
 *
 * Project Management:
 *   rai support --priority high            # Show high priority tasks
 *   rai support --status                   # Show project status
 *   rai support --recommend                # Get AI recommendations on what to do first
 */
class SupportCommand : CliktCommand(
    name = "support",
    help = "Get tech support assistance using RAG and Trello integration"
) {
    private val query by argument(
        name = "query",
        help = "Your support question or issue description"
    ).optional()

    private val sessionId by option(
        "--session", "-s",
        help = "Session ID for conversation continuity"
    )

    private val customerId by option(
        "--customer", "-c",
        help = "Customer ID for context"
    )

    private val boardId by option(
        "--board", "-b",
        help = "Trello board ID (overrides default)"
    )

    private val noRag by option(
        "--no-rag",
        help = "Disable RAG knowledge base search"
    ).flag(default = false)

    private val noTrello by option(
        "--no-trello",
        help = "Disable Trello ticket search"
    ).flag(default = false)

    private val output by option(
        "--output", "-o",
        help = "Output format: text, json"
    ).default("text")

    private val serverUrlOption by option(
        "--server",
        help = "ResearchAI server URL"
    )

    private val model by option(
        "--model", "-m",
        help = "AI model to use"
    )

    // Project Management options
    private val priority by option(
        "--priority", "-p",
        help = "Filter tasks by priority: high, medium, low"
    )

    private val status by option(
        "--status",
        help = "Show project status summary"
    ).flag(default = false)

    private val recommend by option(
        "--recommend", "-r",
        help = "Get AI recommendations for task prioritization"
    ).flag(default = false)

    override fun run() = runBlocking {
        val config = CliConfig.load()
        val serverUrl = serverUrlOption ?: config.serverUrl

        val client = ResearchAiClient(serverUrl)

        if (!client.checkHealth()) {
            echo("Error: Cannot connect to server at $serverUrl")
            client.close()
            return@runBlocking
        }

        try {
            val handler = SupportHandler(client) { msg, newline ->
                echo(msg, trailingNewline = newline)
            }

            // Build query based on project management shortcuts
            val actualQuery: String = when {
                status -> "Show project status summary"
                recommend -> "What tasks should I do first and why?"
                priority != null -> "Show tasks with $priority priority"
                query != null -> query!!
                else -> {
                    // Interactive mode
                    echo("Tech Support Assistant")
                    echo("=" .repeat(50))
                    echo("Enter your question (Ctrl+D to exit):\n")
                    print("> ")
                    readLine() ?: run {
                        echo("\nGoodbye!")
                        return@runBlocking
                    }
                }
            }

            handler.handle(
                query = actualQuery,
                sessionId = sessionId,
                customerId = customerId,
                boardId = boardId,
                includeRag = !noRag,
                includeTrello = !noTrello,
                outputFormat = output,
                model = model
            )
        } catch (e: Exception) {
            echo("Error: ${e.message}")
            System.exit(1)
        } finally {
            client.close()
        }
    }
}
