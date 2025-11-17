package com.researchai.routes

import com.researchai.config.getMCPServers
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

/**
 * MCP Server information for frontend display
 */
@Serializable
data class MCPServerInfo(
    val name: String,
    val description: String? = null,
    val connected: Boolean,
    val version: String? = null,
    val capabilities: List<String>? = null
)

/**
 * Response containing list of MCP servers
 */
@Serializable
data class MCPServersResponse(
    val servers: List<MCPServerInfo>
)

/**
 * Configure MCP-related routes
 */
fun Route.mcpRoutes() {
    route("/mcp") {
        // Get list of configured MCP servers
        get("/servers") {
            try {
                val mcpServers = getMCPServers()

                // Convert MCP server configs to display format
                val serverInfoList = mcpServers
                    .filter { it.enabled } // Only show enabled servers
                    .map { config ->
                        MCPServerInfo(
                            name = config.name,
                            description = config.description,
                            connected = config.enabled, // Show as connected if enabled in config
                            version = null, // TODO: Get version from actual MCP server
                            capabilities = emptyList() // TODO: Get capabilities from actual MCP server
                        )
                    }

                call.respond(HttpStatusCode.OK, MCPServersResponse(servers = serverInfoList))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to load MCP servers: ${e.message}")
                )
            }
        }
    }
}
