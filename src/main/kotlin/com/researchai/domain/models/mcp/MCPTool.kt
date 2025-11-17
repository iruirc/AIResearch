package com.researchai.domain.models.mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Represents an MCP tool that can be called by AI models
 */
@Serializable
data class MCPTool(
    val name: String,
    val description: String,
    val inputSchema: JsonElement, // JSON Schema for tool parameters
    val serverId: String // ID of the MCP server providing this tool
)

/**
 * Request to call an MCP tool
 */
@Serializable
data class MCPToolCallRequest(
    val toolName: String,
    val arguments: JsonElement,
    val serverId: String
)

/**
 * Result of an MCP tool call
 */
@Serializable
data class MCPToolCallResult(
    val success: Boolean,
    val content: List<MCPContent>,
    val error: String? = null
)

/**
 * MCP content types (text, image, resource)
 */
@Serializable
data class MCPContent(
    val type: String, // "text", "image", "resource"
    val text: String? = null,
    val data: String? = null, // Base64 for images
    val mimeType: String? = null,
    val uri: String? = null // For resources
)
