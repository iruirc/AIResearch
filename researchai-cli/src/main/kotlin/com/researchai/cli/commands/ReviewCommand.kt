package com.researchai.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.researchai.cli.api.ResearchAiClient
import com.researchai.cli.config.CliConfig
import com.researchai.cli.handlers.ReviewHandler
import kotlinx.coroutines.runBlocking

/**
 * CLI command for reviewing pull requests
 */
class ReviewCommand : CliktCommand(
    name = "review",
    help = "Review a pull request using AI"
) {
    private val prUrl by argument(
        name = "pr-url",
        help = "GitHub PR URL (e.g., https://github.com/owner/repo/pull/123)"
    )

    private val mode by option(
        "--mode", "-m",
        help = "Review mode: quick, standard, thorough"
    ).default("standard")

    private val focus by option(
        "--focus", "-f",
        help = "Focus areas (comma-separated): security,performance,style,architecture,testing,documentation,error_handling,kotlin_idioms"
    )

    private val output by option(
        "--output", "-o",
        help = "Output format: text, json, github"
    ).default("text")

    private val postComment by option(
        "--post-comment",
        help = "Post review as GitHub PR comment (requires GITHUB_TOKEN)"
    ).flag(default = false)

    private val serverUrlOption by option(
        "--server", "-s",
        help = "ResearchAI server URL"
    )

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
            val handler = ReviewHandler(client) { echo(it, trailingNewline = false) }
            handler.handle(
                prUrl = prUrl,
                mode = mode,
                focusAreas = focus,
                outputFormat = output,
                postComment = postComment
            )
        } catch (e: Exception) {
            echo("Error: ${e.message}")
            System.exit(1)
        } finally {
            client.close()
        }
    }
}
