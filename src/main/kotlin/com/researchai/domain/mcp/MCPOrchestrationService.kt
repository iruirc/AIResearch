package com.researchai.domain.mcp

import com.researchai.data.mcp.MCPServerManager
import com.researchai.domain.models.mcp.MCPTool
import com.researchai.domain.models.mcp.MCPToolCallResult
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * Service for orchestrating MCP tool calls with AI models
 *
 * This service handles:
 * - Converting MCP tools to AI provider tool format
 * - Executing MCP tool calls when requested by AI
 * - Managing the tool call loop
 */
class MCPOrchestrationService(
    private val mcpServerManager: MCPServerManager
) {
    private val logger = LoggerFactory.getLogger(MCPOrchestrationService::class.java)

    /**
     * Get all available MCP tools from connected servers
     * Returns tools in a format that can be used by AI providers
     */
    suspend fun getAvailableTools(): List<MCPTool> {
        return try {
            val tools = mcpServerManager.listAllTools()
            logger.info("Retrieved ${tools.size} MCP tools from connected servers")
            tools
        } catch (e: Exception) {
            logger.error("Failed to retrieve MCP tools: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Execute a tool call requested by the AI model
     *
     * @param toolName Name of the tool to call
     * @param arguments JSON arguments for the tool
     * @return Result of the tool call
     */
    suspend fun executeToolCall(
        toolName: String,
        arguments: JsonElement
    ): MCPToolCallResult {
        logger.info("Executing MCP tool: $toolName")

        // Find the tool among all available tools
        val tools = getAvailableTools()
        val tool = tools.find { it.name == toolName }

        if (tool == null) {
            logger.warn("Tool not found: $toolName")
            return MCPToolCallResult(
                success = false,
                content = emptyList(),
                error = "Tool '$toolName' not found among available MCP tools"
            )
        }

        // Call the tool on the appropriate server
        return try {
            val result = mcpServerManager.callTool(
                serverId = tool.serverId,
                toolName = toolName,
                arguments = arguments
            )

            logger.info("Tool call completed: $toolName, success: ${result.success}")
            result
        } catch (e: Exception) {
            logger.error("Error executing tool $toolName: ${e.message}", e)
            MCPToolCallResult(
                success = false,
                content = emptyList(),
                error = "Error executing tool: ${e.message}"
            )
        }
    }

    /**
     * Convert MCP tools to Anthropic Claude tool format
     * This is used to send tools to Claude API
     */
    fun convertToClaudeTools(mcpTools: List<MCPTool>): List<ClaudeTool> {
        return mcpTools.map { tool ->
            ClaudeTool(
                name = tool.name,
                description = tool.description,
                input_schema = tool.inputSchema
            )
        }
    }

    /**
     * Check if there are any MCP tools available
     */
    suspend fun hasAvailableTools(): Boolean {
        return getAvailableTools().isNotEmpty()
    }
}

/**
 * Tool format for Claude API
 */
@kotlinx.serialization.Serializable
data class ClaudeTool(
    val name: String,
    val description: String,
    val input_schema: JsonElement
)
