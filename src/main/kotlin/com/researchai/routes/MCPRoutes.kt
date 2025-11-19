package com.researchai.routes

import com.researchai.data.mcp.MCPServerManager
import com.researchai.domain.models.mcp.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

/**
 * MCP Server information for frontend display
 */
@Serializable
data class MCPServerInfo(
    val id: String,
    val name: String,
    val description: String? = null,
    val connected: Boolean,
    val enabled: Boolean,
    val transport: String,
    val toolsCount: Int = 0,
    val resourcesCount: Int = 0,
    val promptsCount: Int = 0,
    val tools: List<MCPTool> = emptyList()
)

/**
 * Response containing list of MCP servers
 */
@Serializable
data class MCPServersResponse(
    val servers: List<MCPServerInfo>
)

/**
 * Response for server toggle operations
 */
@Serializable
data class MCPServerToggleResponse(
    val success: Boolean,
    val message: String
)

/**
 * Configure MCP-related routes
 */
fun Route.mcpRoutes(mcpServerManager: MCPServerManager) {
    route("/mcp") {
        // Get list of all MCP servers (both enabled and disabled)
        get("/servers") {
            try {
                val configs = mcpServerManager.getAllServerConfigs()
                val connectionStatus = mcpServerManager.getConnectionStatus()

                val serverInfoList = configs.map { config ->
                    val connected = connectionStatus[config.id] ?: false
                    val enabled = mcpServerManager.isServerEnabled(config.id)

                    // Get tools, resources, and prompts lists if connected
                    val (tools, resources, prompts) = if (connected) {
                        Triple(
                            mcpServerManager.listTools(config.id),
                            mcpServerManager.listResources(config.id),
                            mcpServerManager.listPrompts(config.id)
                        )
                    } else {
                        Triple(emptyList(), emptyList(), emptyList())
                    }

                    MCPServerInfo(
                        id = config.id,
                        name = config.name,
                        description = config.description,
                        connected = connected,
                        enabled = enabled,
                        transport = config.transport,
                        toolsCount = tools.size,
                        resourcesCount = resources.size,
                        promptsCount = prompts.size,
                        tools = tools
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

        // Get server details by ID
        get("/servers/{serverId}") {
            try {
                val serverId = call.parameters["serverId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Server ID is required")
                )

                val config = mcpServerManager.getServerConfig(serverId) ?: return@get call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "Server not found")
                )

                val client = mcpServerManager.getClient(serverId)
                val connected = client?.isConnected() ?: false
                val enabled = mcpServerManager.isServerEnabled(serverId)

                // Get tools, resources, and prompts lists if connected
                val (tools, resources, prompts) = if (connected) {
                    Triple(
                        mcpServerManager.listTools(serverId),
                        mcpServerManager.listResources(serverId),
                        mcpServerManager.listPrompts(serverId)
                    )
                } else {
                    Triple(emptyList(), emptyList(), emptyList())
                }

                call.respond(
                    HttpStatusCode.OK,
                    MCPServerInfo(
                        id = config.id,
                        name = config.name,
                        description = config.description,
                        connected = connected,
                        enabled = enabled,
                        transport = config.transport,
                        toolsCount = tools.size,
                        resourcesCount = resources.size,
                        promptsCount = prompts.size,
                        tools = tools
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to get server details: ${e.message}")
                )
            }
        }

        // Enable a specific MCP server
        post("/servers/{serverId}/enable") {
            try {
                val serverId = call.parameters["serverId"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Server ID is required")
                )

                val success = mcpServerManager.enableServer(serverId)

                call.respond(
                    HttpStatusCode.OK,
                    MCPServerToggleResponse(
                        success = success,
                        message = if (success) "Server enabled successfully" else "Failed to enable server"
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to enable server: ${e.message}")
                )
            }
        }

        // Disable a specific MCP server
        post("/servers/{serverId}/disable") {
            try {
                val serverId = call.parameters["serverId"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Server ID is required")
                )

                val success = mcpServerManager.disableServer(serverId)

                call.respond(
                    HttpStatusCode.OK,
                    MCPServerToggleResponse(
                        success = success,
                        message = if (success) "Server disabled successfully" else "Failed to disable server"
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to disable server: ${e.message}")
                )
            }
        }

        // Reconnect to a server
        post("/servers/{serverId}/reconnect") {
            try {
                val serverId = call.parameters["serverId"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Server ID is required")
                )

                val success = mcpServerManager.reconnect(serverId)

                call.respond(
                    HttpStatusCode.OK,
                    mapOf("success" to success, "message" to if (success) "Reconnected successfully" else "Failed to reconnect")
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to reconnect: ${e.message}")
                )
            }
        }

        // List all tools
        get("/tools") {
            try {
                val serverId = call.request.queryParameters["serverId"]

                val tools = if (serverId != null) {
                    mcpServerManager.listTools(serverId)
                } else {
                    mcpServerManager.listAllTools()
                }

                call.respond(HttpStatusCode.OK, mapOf("tools" to tools))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to list tools: ${e.message}")
                )
            }
        }

        // Call a tool
        post("/tools/call") {
            try {
                val request = call.receive<MCPToolCallRequest>()
                val result = mcpServerManager.callTool(request.serverId, request.toolName, request.arguments)

                call.respond(HttpStatusCode.OK, result)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to call tool: ${e.message}")
                )
            }
        }

        // List all resources
        get("/resources") {
            try {
                val serverId = call.request.queryParameters["serverId"]

                val resources = if (serverId != null) {
                    mcpServerManager.listResources(serverId)
                } else {
                    mcpServerManager.listAllResources()
                }

                call.respond(HttpStatusCode.OK, mapOf("resources" to resources))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to list resources: ${e.message}")
                )
            }
        }

        // Read a resource
        post("/resources/read") {
            try {
                val request = call.receive<MCPResourceReadRequest>()
                val result = mcpServerManager.readResource(request.serverId, request.uri)

                call.respond(HttpStatusCode.OK, result)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to read resource: ${e.message}")
                )
            }
        }

        // List all prompts
        get("/prompts") {
            try {
                val serverId = call.request.queryParameters["serverId"]

                val prompts = if (serverId != null) {
                    mcpServerManager.listPrompts(serverId)
                } else {
                    mcpServerManager.listAllPrompts()
                }

                call.respond(HttpStatusCode.OK, mapOf("prompts" to prompts))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to list prompts: ${e.message}")
                )
            }
        }

        // Get a prompt
        post("/prompts/get") {
            try {
                val request = call.receive<MCPPromptGetRequest>()
                val result = mcpServerManager.getPrompt(request.serverId, request.promptName, request.arguments)

                call.respond(HttpStatusCode.OK, result)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to get prompt: ${e.message}")
                )
            }
        }
    }
}
