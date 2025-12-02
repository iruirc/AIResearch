package com.researchai.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.researchai.cli.api.ResearchAiClient
import com.researchai.cli.commands.git.GitOutputFormatterRegistry
import com.researchai.cli.config.CliConfig
import com.researchai.cli.util.GitRepositoryDetector
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

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

    companion object {
        private const val GITHUB_SERVER_ID = "github"
    }

    override fun run() = runBlocking {
        val config = CliConfig.load()
        val serverUrl = serverUrlOption ?: config.serverUrl
        val client = ResearchAiClient(serverUrl)

        try {
            if (!client.checkHealth()) {
                echo("Error: Cannot connect to server at $serverUrl")
                return@runBlocking
            }

            when (tool) {
                null, "tools" -> listTools(client)
                else -> executeTool(client, tool!!)
            }
        } catch (e: Exception) {
            echo("Error: ${e.message}")
        } finally {
            client.close()
        }
    }

    private suspend fun listTools(client: ResearchAiClient) {
        echo("Available GitHub MCP tools:\n")

        val tools = client.getMcpTools(GITHUB_SERVER_ID)

        tools.sortedBy { it.name }.forEach { tool ->
            echo("  ${tool.name}")
            echo("    ${tool.description}\n")
        }

        echo("Total: ${tools.size} tools")
        echo("\nUsage: rai git <tool_name> [options]")
    }

    private suspend fun executeTool(client: ResearchAiClient, toolName: String) {
        // Auto-detect repository if needed
        val detectedRepo = GitRepositoryDetector.detect()
        val owner = ownerOption ?: detectedRepo?.owner
        val repo = repoOption ?: detectedRepo?.repo

        // Build arguments JSON
        val arguments = buildJsonObject {
            // Add owner/repo if available
            if (owner != null) put("owner", owner)
            if (repo != null) put("repo", repo)

            // Add named options
            title?.let { put("title", it) }
            body?.let { put("body", it) }
            query?.let { put("query", it) }
            branch?.let { put("branch", it) }
            base?.let { put("base", it) }
            head?.let { put("head", it) }
            path?.let { put("path", it) }
            message?.let { put("message", it) }
            ref?.let { put("ref", it) }
            sha?.let { put("sha", it) }
            content?.let { put("content", it) }

            issueNumber?.let {
                put("issue_number", it.toIntOrNull() ?: return@let)
            }
            prNumber?.let {
                put("pull_number", it.toIntOrNull() ?: return@let)
            }
            state?.let { put("state", it) }

            // Handle comma-separated lists
            labels?.let {
                put("labels", JsonArray(it.split(",").map { label -> JsonPrimitive(label.trim()) }))
            }
            assignees?.let {
                put("assignees", JsonArray(it.split(",").map { assignee -> JsonPrimitive(assignee.trim()) }))
            }

            // Add generic --arg key=value pairs
            args.forEach { arg ->
                val parts = arg.split("=", limit = 2)
                if (parts.size == 2) {
                    val (key, value) = parts
                    // Try to parse as number or boolean
                    when {
                        value.toIntOrNull() != null -> put(key, value.toInt())
                        value.toDoubleOrNull() != null -> put(key, value.toDouble())
                        value == "true" -> put(key, true)
                        value == "false" -> put(key, false)
                        else -> put(key, value)
                    }
                }
            }
        }

        // Show what we're doing
        if (owner != null && repo != null) {
            echo("Repository: $owner/$repo")
        }
        echo("Executing: $toolName\n")

        // Call the tool
        val result = client.callMcpTool(GITHUB_SERVER_ID, toolName, arguments)

        if (!result.success) {
            echo("Error: ${result.error ?: "Unknown error"}")
            return
        }

        // Display result with tool-specific formatting
        val formatter = GitOutputFormatterRegistry.getFormatter(toolName)

        result.content.forEach { content ->
            when (content.type) {
                "text" -> echo(formatter.format(content.text ?: ""))
                else -> echo("[${content.type}]: ${content.text ?: content.uri ?: "..."}")
            }
        }
    }
}
