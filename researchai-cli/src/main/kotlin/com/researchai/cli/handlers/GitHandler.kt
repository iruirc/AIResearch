package com.researchai.cli.handlers

import com.researchai.cli.api.ResearchAiClient
import com.researchai.cli.commands.git.GitOutputFormatterRegistry
import com.researchai.cli.util.GitRepositoryDetector
import kotlinx.serialization.json.*

/**
 * Handler for GitHub MCP tool operations.
 * Used by both top-level GitCommand and /git slash command in chat.
 */
class GitHandler(
    private val client: ResearchAiClient,
    private val output: (String) -> Unit
) {
    companion object {
        private const val GITHUB_SERVER_ID = "github"
    }

    /**
     * List all available GitHub MCP tools
     */
    suspend fun listTools() {
        output("Available GitHub MCP tools:\n")

        val tools = client.getMcpTools(GITHUB_SERVER_ID)

        tools.sortedBy { it.name }.forEach { tool ->
            output("  ${tool.name}")
            output("    ${tool.description}\n")
        }

        output("Total: ${tools.size} tools")
        output("\nUsage: /git <tool_name> [key=value ...]")
    }

    /**
     * Execute a GitHub MCP tool with arguments.
     *
     * @param toolName Name of the tool to execute
     * @param args Map of argument key-value pairs
     * @param owner Repository owner (optional, auto-detected if null)
     * @param repo Repository name (optional, auto-detected if null)
     */
    suspend fun executeTool(
        toolName: String,
        args: Map<String, String> = emptyMap(),
        owner: String? = null,
        repo: String? = null
    ) {
        // Auto-detect repository if needed
        val detectedRepo = GitRepositoryDetector.detect()
        val effectiveOwner = owner ?: detectedRepo?.owner
        val effectiveRepo = repo ?: detectedRepo?.repo

        // Build arguments JSON
        val arguments = buildJsonObject {
            // Add owner/repo if available
            if (effectiveOwner != null) put("owner", effectiveOwner)
            if (effectiveRepo != null) put("repo", effectiveRepo)

            // Add all provided arguments with type inference
            args.forEach { (key, value) ->
                when {
                    value.toIntOrNull() != null -> put(key, value.toInt())
                    value.toDoubleOrNull() != null -> put(key, value.toDouble())
                    value == "true" -> put(key, true)
                    value == "false" -> put(key, false)
                    value.contains(",") -> {
                        // Comma-separated values become arrays
                        put(key, JsonArray(value.split(",").map { JsonPrimitive(it.trim()) }))
                    }
                    else -> put(key, value)
                }
            }
        }

        // Show what we're doing
        if (effectiveOwner != null && effectiveRepo != null) {
            output("Repository: $effectiveOwner/$effectiveRepo")
        }
        output("Executing: $toolName\n")

        // Call the tool
        val result = client.callMcpTool(GITHUB_SERVER_ID, toolName, arguments)

        if (!result.success) {
            output("Error: ${result.error ?: "Unknown error"}")
            return
        }

        // Display result with tool-specific formatting
        val formatter = GitOutputFormatterRegistry.getFormatter(toolName)

        result.content.forEach { content ->
            when (content.type) {
                "text" -> output(formatter.format(content.text ?: ""))
                else -> output("[${content.type}]: ${content.text ?: content.uri ?: "..."}")
            }
        }
    }

    /**
     * Parse a slash command input string into tool name and arguments.
     * Format: "tool_name key1=value1 key2=value2"
     *
     * @param input The input string after "/git "
     * @return Pair of tool name and arguments map
     */
    fun parseSlashCommand(input: String): Pair<String, Map<String, String>> {
        val parts = input.trim().split(Regex("\\s+"))
        if (parts.isEmpty()) {
            return "tools" to emptyMap()
        }

        val toolName = parts[0]
        val args = mutableMapOf<String, String>()

        parts.drop(1).forEach { part ->
            val eqIndex = part.indexOf('=')
            if (eqIndex > 0) {
                val key = part.substring(0, eqIndex)
                val value = part.substring(eqIndex + 1)
                args[key] = value
            }
        }

        return toolName to args
    }
}
