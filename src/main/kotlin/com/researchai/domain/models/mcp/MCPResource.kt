package com.researchai.domain.models.mcp

import kotlinx.serialization.Serializable

/**
 * Represents an MCP resource (files, data, APIs)
 */
@Serializable
data class MCPResource(
    val uri: String,
    val name: String,
    val description: String? = null,
    val mimeType: String? = null,
    val serverId: String // ID of the MCP server providing this resource
)

/**
 * Request to read an MCP resource
 */
@Serializable
data class MCPResourceReadRequest(
    val uri: String,
    val serverId: String
)

/**
 * Result of reading an MCP resource
 */
@Serializable
data class MCPResourceReadResult(
    val success: Boolean,
    val contents: List<MCPContent>,
    val error: String? = null
)
