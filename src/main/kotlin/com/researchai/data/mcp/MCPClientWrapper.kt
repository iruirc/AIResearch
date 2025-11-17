package com.researchai.data.mcp

import com.researchai.domain.models.mcp.*
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.shared.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.io.asSource
import kotlinx.io.asSink
import kotlinx.io.buffered
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Wrapper for MCP Client that handles connection and communication with MCP servers
 */
class MCPClientWrapper(
    private val config: MCPServerConfig
) {
    private val logger = LoggerFactory.getLogger(MCPClientWrapper::class.java)
    private var client: Client? = null
    private var process: Process? = null

    val serverId: String get() = config.id
    val serverName: String get() = config.name

    /**
     * Connect to the MCP server
     */
    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            logger.info("Connecting to MCP server: ${config.name} (${config.id})")

            when (config.getTransportType()) {
                MCPTransportType.STDIO -> connectStdio()
                MCPTransportType.SSE -> {
                    logger.warn("SSE transport not yet implemented for ${config.name}")
                    false
                }
                MCPTransportType.WEBSOCKET -> {
                    logger.warn("WebSocket transport not yet implemented for ${config.name}")
                    false
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to connect to MCP server ${config.name}: ${e.message}", e)
            false
        }
    }

    /**
     * Connect using STDIO transport (subprocess)
     */
    private suspend fun connectStdio(): Boolean = withContext(Dispatchers.IO) {
        try {
            val command = config.command ?: throw IllegalArgumentException("Command is required for STDIO transport")
            val args = config.args ?: emptyList()

            // Build process command
            val processCommand = mutableListOf(command).apply { addAll(args) }

            logger.info("Starting MCP server process: ${processCommand.joinToString(" ")}")

            // Create process builder
            val processBuilder = ProcessBuilder(processCommand).apply {
                // Set environment variables if provided
                config.env?.forEach { (key, value) ->
                    environment()[key] = value
                }

                // Redirect error stream for debugging
                redirectErrorStream(false)
            }

            // Start the process
            process = processBuilder.start()

            // Start a thread to read and log stderr
            Thread {
                process!!.errorStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        logger.warn("[${config.name} stderr] $line")
                    }
                }
            }.start()

            // Create MCP client
            client = Client(
                clientInfo = Implementation(
                    name = "ResearchAI",
                    version = "1.0.0"
                )
            )

            // Create STDIO transport with kotlinx-io Source/Sink
            val transport = StdioClientTransport(
                input = process!!.inputStream.asSource().buffered(),
                output = process!!.outputStream.asSink().buffered()
            )

            // Connect client to transport
            client!!.connect(transport)

            logger.info("Successfully connected to MCP server: ${config.name}")
            true
        } catch (e: Exception) {
            logger.error("Failed to connect via STDIO: ${e.message}", e)
            cleanup()
            false
        }
    }

    /**
     * Disconnect from the MCP server
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            logger.info("Disconnecting from MCP server: ${config.name}")
            cleanup()
        } catch (e: Exception) {
            logger.error("Error during disconnect: ${e.message}", e)
        }
    }

    /**
     * Clean up resources
     */
    private fun cleanup() {
        try {
            client = null
            process?.destroy()
            process = null
        } catch (e: Exception) {
            logger.error("Error during cleanup: ${e.message}", e)
        }
    }

    /**
     * Check if the client is connected
     */
    fun isConnected(): Boolean {
        return client != null && (process?.isAlive ?: false || config.getTransportType() != MCPTransportType.STDIO)
    }

    /**
     * List all tools provided by this server
     */
    suspend fun listTools(): List<MCPTool> = withContext(Dispatchers.IO) {
        try {
            val currentClient = client ?: throw IllegalStateException("Client not connected")

            val tools = currentClient.listTools()

            tools.tools.map { tool ->
                // TODO: Properly serialize Tool.Input to JsonElement
                // The SDK's Tool.Input type is not directly convertible to JsonElement
                // For now, we return an empty JSON object as a placeholder
                // This allows the system to work but tools won't have schema validation
                val inputSchema = buildJsonObject {}

                MCPTool(
                    name = tool.name,
                    description = tool.description ?: "",
                    inputSchema = inputSchema,
                    serverId = config.id
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to list tools from ${config.name}: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Call a tool on this server
     */
    suspend fun callTool(name: String, arguments: JsonElement): MCPToolCallResult = withContext(Dispatchers.IO) {
        try {
            val currentClient = client ?: throw IllegalStateException("Client not connected")

            // Convert JsonElement to Map for MCP SDK
            val argsMap = if (arguments is kotlinx.serialization.json.JsonObject) {
                arguments.jsonObject.mapValues { it.value }
            } else {
                emptyMap()
            }

            val result = currentClient.callTool(
                name = name,
                arguments = argsMap
            )

            // Convert result to MCPToolCallResult
            MCPToolCallResult(
                success = result?.isError?.not() ?: false,
                content = result?.content?.map { content ->
                    MCPContent(
                        type = content.type,
                        text = content.toString(), // Временное решение - возвращаем строковое представление
                        data = null,
                        mimeType = null
                    )
                } ?: emptyList(),
                error = null
            )
        } catch (e: Exception) {
            logger.error("Failed to call tool '$name' on ${config.name}: ${e.message}", e)
            MCPToolCallResult(
                success = false,
                content = emptyList(),
                error = e.message
            )
        }
    }

    /**
     * List all resources provided by this server
     */
    suspend fun listResources(): List<MCPResource> = withContext(Dispatchers.IO) {
        try {
            val currentClient = client ?: throw IllegalStateException("Client not connected")

            // Check if server supports resources capability
            val capabilities = currentClient.serverCapabilities
            if (capabilities?.resources?.listChanged != true && capabilities?.resources?.subscribe != true) {
                logger.debug("Server ${config.name} does not support resources capability")
                return@withContext emptyList()
            }

            val resources = currentClient.listResources()

            resources.resources.map { resource ->
                MCPResource(
                    uri = resource.uri,
                    name = resource.name,
                    description = resource.description,
                    mimeType = resource.mimeType,
                    serverId = config.id
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to list resources from ${config.name}: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Read a resource from this server
     * TODO: Implement after MCP SDK API stabilizes
     */
    suspend fun readResource(uri: String): MCPResourceReadResult = withContext(Dispatchers.IO) {
        logger.warn("readResource not yet implemented - waiting for MCP SDK API stabilization")
        MCPResourceReadResult(
            success = false,
            contents = emptyList(),
            error = "Not yet implemented - MCP SDK API pending"
        )
    }

    /**
     * List all prompts provided by this server
     */
    suspend fun listPrompts(): List<MCPPrompt> = withContext(Dispatchers.IO) {
        try {
            val currentClient = client ?: throw IllegalStateException("Client not connected")

            // Check if server supports prompts capability
            val capabilities = currentClient.serverCapabilities
            if (capabilities?.prompts?.listChanged != true) {
                logger.debug("Server ${config.name} does not support prompts capability")
                return@withContext emptyList()
            }

            val prompts = currentClient.listPrompts()

            prompts.prompts.map { prompt ->
                MCPPrompt(
                    name = prompt.name,
                    description = prompt.description,
                    arguments = prompt.arguments?.map { arg ->
                        MCPPromptArgument(
                            name = arg.name,
                            description = arg.description,
                            required = arg.required ?: false
                        )
                    } ?: emptyList(),
                    serverId = config.id
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to list prompts from ${config.name}: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Get a prompt from this server
     * TODO: Implement after MCP SDK API stabilizes
     */
    suspend fun getPrompt(name: String, arguments: Map<String, String>): MCPPromptGetResult = withContext(Dispatchers.IO) {
        logger.warn("getPrompt not yet implemented - waiting for MCP SDK API stabilization")
        MCPPromptGetResult(
            success = false,
            description = null,
            messages = emptyList(),
            error = "Not yet implemented - MCP SDK API pending"
        )
    }
}
