package com.researchai.domain.models.mcp

import kotlinx.serialization.Serializable

/**
 * Transport type for MCP server connection
 */
enum class MCPTransportType {
    STDIO,  // Standard input/output (subprocess)
    SSE,    // Server-Sent Events (HTTP)
    WEBSOCKET  // WebSocket connection
}

/**
 * Configuration for an MCP server
 */
@Serializable
data class MCPServerConfig(
    val id: String,
    val name: String,
    val description: String? = null,
    val transport: String, // "stdio", "sse", "websocket"
    val command: String? = null, // For STDIO: command to run (e.g., "npx", "node")
    val args: List<String>? = null, // For STDIO: command arguments
    val env: Map<String, String>? = null, // Environment variables for STDIO
    val url: String? = null, // For SSE/WebSocket: connection URL
    val headers: Map<String, String>? = null, // For SSE/WebSocket: HTTP headers
    val enabled: Boolean = true
) {
    fun getTransportType(): MCPTransportType {
        return when (transport.lowercase()) {
            "stdio" -> MCPTransportType.STDIO
            "sse" -> MCPTransportType.SSE
            "websocket" -> MCPTransportType.WEBSOCKET
            else -> throw IllegalArgumentException("Unknown transport type: $transport")
        }
    }
}
