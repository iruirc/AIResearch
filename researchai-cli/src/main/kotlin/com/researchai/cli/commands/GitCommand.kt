package com.researchai.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.researchai.cli.api.ResearchAiClient
import com.researchai.cli.config.CliConfig
import com.researchai.cli.handlers.GitHandler
import kotlinx.coroutines.runBlocking

class GitCommand : CliktCommand(
    name = "git",
    help = """
        Execute GitHub MCP tools.

        Usage:
          rai git tools                    - List available tools
          rai git <tool_name> [options]    - Execute a tool

        Examples:
          rai git get_me
          rai git list_issues
          rai git list_issues --owner user --repo myrepo
          rai git create_issue --title "Bug" --body "Description"
          rai git search_repositories --query "language:kotlin"
    """.trimIndent()
) {
    private val tool by argument(
        help = "Tool name or 'tools' to list available tools"
    ).optional()

    private val serverUrlOption by option("--server", "-s", help = "Server URL")

    // Owner and repo can be auto-detected or explicitly specified
    private val ownerOption by option("--owner", help = "Repository owner")
    private val repoOption by option("--repo", help = "Repository name")

    // Generic arguments passed as key=value pairs
    private val args by option("--arg", "-a", help = "Tool argument (key=value)").multiple()

    // Named options for common arguments
    private val title by option("--title", help = "Title (for issues/PRs)")
    private val body by option("--body", help = "Body/description")
    private val query by option("--query", "-q", help = "Search query")
    private val branch by option("--branch", help = "Branch name")
    private val base by option("--base", help = "Base branch")
    private val head by option("--head", help = "Head branch")
    private val path by option("--path", help = "File path")
    private val message by option("--message", "-m", help = "Commit message")
    private val issueNumber by option("--issue", help = "Issue number")
    private val prNumber by option("--pr", help = "Pull request number")
    private val state by option("--state", help = "State filter (open/closed/all)")
    private val ref by option("--ref", help = "Git reference (branch/tag/commit)")
    private val sha by option("--sha", help = "Commit SHA")
    private val content by option("--content", help = "File content")
    private val labels by option("--labels", help = "Labels (comma-separated)")
    private val assignees by option("--assignees", help = "Assignees (comma-separated)")

    override fun run() = runBlocking {
        val config = CliConfig.load()
        val serverUrl = serverUrlOption ?: config.serverUrl
        val client = ResearchAiClient(serverUrl)

        try {
            if (!client.checkHealth()) {
                echo("Error: Cannot connect to server at $serverUrl")
                return@runBlocking
            }

            val handler = GitHandler(client) { echo(it) }

            when (tool) {
                null, "tools" -> handler.listTools()
                else -> {
                    // Build arguments from named options
                    val arguments = buildArgsMap()
                    handler.executeTool(
                        toolName = tool!!,
                        args = arguments,
                        owner = ownerOption,
                        repo = repoOption
                    )
                }
            }
        } catch (e: Exception) {
            echo("Error: ${e.message}")
        } finally {
            client.close()
        }
    }

    private fun buildArgsMap(): Map<String, String> {
        val argsMap = mutableMapOf<String, String>()

        // Add named options
        title?.let { argsMap["title"] = it }
        body?.let { argsMap["body"] = it }
        query?.let { argsMap["query"] = it }
        branch?.let { argsMap["branch"] = it }
        base?.let { argsMap["base"] = it }
        head?.let { argsMap["head"] = it }
        path?.let { argsMap["path"] = it }
        message?.let { argsMap["message"] = it }
        ref?.let { argsMap["ref"] = it }
        sha?.let { argsMap["sha"] = it }
        content?.let { argsMap["content"] = it }
        issueNumber?.let { argsMap["issue_number"] = it }
        prNumber?.let { argsMap["pull_number"] = it }
        state?.let { argsMap["state"] = it }
        labels?.let { argsMap["labels"] = it }
        assignees?.let { argsMap["assignees"] = it }

        // Add generic --arg key=value pairs
        args.forEach { arg ->
            val parts = arg.split("=", limit = 2)
            if (parts.size == 2) {
                argsMap[parts[0]] = parts[1]
            }
        }

        return argsMap
    }
}
