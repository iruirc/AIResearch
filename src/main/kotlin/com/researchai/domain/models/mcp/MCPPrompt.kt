package com.researchai.domain.models.mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Represents an MCP prompt template
 */
@Serializable
data class MCPPrompt(
    val name: String,
    val description: String? = null,
    val arguments: List<MCPPromptArgument> = emptyList(),
    val serverId: String // ID of the MCP server providing this prompt
)

/**
 * Argument definition for an MCP prompt
 */
@Serializable
data class MCPPromptArgument(
    val name: String,
    val description: String? = null,
    val required: Boolean = false
)

/**
 * Request to get a prompt from MCP server
 */
@Serializable
data class MCPPromptGetRequest(
    val promptName: String,
    val arguments: Map<String, String> = emptyMap(),
    val serverId: String
)

/**
 * Result of getting an MCP prompt
 */
@Serializable
data class MCPPromptGetResult(
    val success: Boolean,
    val description: String? = null,
    val messages: List<MCPPromptMessage> = emptyList(),
    val error: String? = null
)

/**
 * Message in an MCP prompt
 */
@Serializable
data class MCPPromptMessage(
    val role: String, // "user", "assistant"
    val content: MCPContent
)
