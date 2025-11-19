package com.researchai.data.mcp

import com.researchai.domain.models.mcp.*
import com.researchai.persistence.MCPPreferencesStorage
import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonElement
import org.slf4j.LoggerFactory

/**
 * Manages multiple MCP server connections
 */
class MCPServerManager(
    private val serverConfigs: List<MCPServerConfig>,
    private val preferencesStorage: MCPPreferencesStorage
) {
    private val logger = LoggerFactory.getLogger(MCPServerManager::class.java)
    private val clients = mutableMapOf<String, MCPClientWrapper>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Initialize MCP servers based on user preferences
     * If preferences file doesn't exist, use enabled flag from config
     */
    suspend fun initialize() {
        logger.info("Initializing MCP servers...")

        val preferences = preferencesStorage.loadPreferences().getOrNull()
        val enabledServerIds = preferences?.enabledServers ?: emptyList()

        // If preferences exist, use them; otherwise fall back to config enabled flag
        val serversToInitialize = if (enabledServerIds.isNotEmpty()) {
            serverConfigs.filter { it.id in enabledServerIds }
        } else {
            // First time: save currently enabled servers to preferences
            val currentlyEnabled = serverConfigs.filter { it.enabled }
            if (currentlyEnabled.isNotEmpty()) {
                val initialPreferences = com.researchai.persistence.MCPPreferences(
                    enabledServers = currentlyEnabled.map { it.id }
                )
                preferencesStorage.savePreferences(initialPreferences)
            }
            currentlyEnabled
        }

        serversToInitialize.forEach { config ->
            try {
                val client = MCPClientWrapper(config)
                clients[config.id] = client

                // Connect in background
                scope.launch {
                    val connected = client.connect()
                    if (connected) {
                        logger.info("✅ Connected to MCP server: ${config.name} (${config.id})")
                    } else {
                        logger.warn("⚠️  Failed to connect to MCP server: ${config.name} (${config.id})")
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to initialize MCP server ${config.id}: ${e.message}", e)
            }
        }

        // Wait a bit for connections to establish
        delay(2000)

        val connectedCount = clients.values.count { it.isConnected() }
        logger.info("MCP initialization complete: $connectedCount/${clients.size} servers connected")
    }

    /**
     * Get a specific MCP client by server ID
     */
    fun getClient(serverId: String): MCPClientWrapper? {
        return clients[serverId]
    }

    /**
     * Get all connected MCP clients
     */
    fun getAllClients(): List<MCPClientWrapper> {
        return clients.values.filter { it.isConnected() }
    }

    /**
     * Get server configuration by ID
     */
    fun getServerConfig(serverId: String): MCPServerConfig? {
        return serverConfigs.find { it.id == serverId }
    }

    /**
     * Get all server configurations
     */
    fun getAllServerConfigs(): List<MCPServerConfig> {
        return serverConfigs
    }

    /**
     * List all tools from all connected servers
     */
    suspend fun listAllTools(): List<MCPTool> {
        return clients.values
            .filter { it.isConnected() }
            .flatMap { client ->
                try {
                    client.listTools()
                } catch (e: Exception) {
                    logger.error("Failed to list tools from ${client.serverName}: ${e.message}", e)
                    emptyList()
                }
            }
    }

    /**
     * List tools from a specific server
     */
    suspend fun listTools(serverId: String): List<MCPTool> {
        val client = clients[serverId] ?: return emptyList()
        return try {
            client.listTools()
        } catch (e: Exception) {
            logger.error("Failed to list tools from $serverId: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Call a tool on a specific server
     */
    suspend fun callTool(serverId: String, toolName: String, arguments: JsonElement): MCPToolCallResult {
        val client = clients[serverId]
            ?: return MCPToolCallResult(
                success = false,
                content = emptyList(),
                error = "Server '$serverId' not found"
            )

        if (!client.isConnected()) {
            return MCPToolCallResult(
                success = false,
                content = emptyList(),
                error = "Server '$serverId' is not connected"
            )
        }

        return client.callTool(toolName, arguments)
    }

    /**
     * List all resources from all connected servers
     */
    suspend fun listAllResources(): List<MCPResource> {
        return clients.values
            .filter { it.isConnected() }
            .flatMap { client ->
                try {
                    client.listResources()
                } catch (e: Exception) {
                    logger.error("Failed to list resources from ${client.serverName}: ${e.message}", e)
                    emptyList()
                }
            }
    }

    /**
     * List resources from a specific server
     */
    suspend fun listResources(serverId: String): List<MCPResource> {
        val client = clients[serverId] ?: return emptyList()
        return try {
            client.listResources()
        } catch (e: Exception) {
            logger.error("Failed to list resources from $serverId: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Read a resource from a specific server
     */
    suspend fun readResource(serverId: String, uri: String): MCPResourceReadResult {
        val client = clients[serverId]
            ?: return MCPResourceReadResult(
                success = false,
                contents = emptyList(),
                error = "Server '$serverId' not found"
            )

        if (!client.isConnected()) {
            return MCPResourceReadResult(
                success = false,
                contents = emptyList(),
                error = "Server '$serverId' is not connected"
            )
        }

        return client.readResource(uri)
    }

    /**
     * List all prompts from all connected servers
     */
    suspend fun listAllPrompts(): List<MCPPrompt> {
        return clients.values
            .filter { it.isConnected() }
            .flatMap { client ->
                try {
                    client.listPrompts()
                } catch (e: Exception) {
                    logger.error("Failed to list prompts from ${client.serverName}: ${e.message}", e)
                    emptyList()
                }
            }
    }

    /**
     * List prompts from a specific server
     */
    suspend fun listPrompts(serverId: String): List<MCPPrompt> {
        val client = clients[serverId] ?: return emptyList()
        return try {
            client.listPrompts()
        } catch (e: Exception) {
            logger.error("Failed to list prompts from $serverId: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Get a prompt from a specific server
     */
    suspend fun getPrompt(serverId: String, promptName: String, arguments: Map<String, String>): MCPPromptGetResult {
        val client = clients[serverId]
            ?: return MCPPromptGetResult(
                success = false,
                description = null,
                messages = emptyList(),
                error = "Server '$serverId' not found"
            )

        if (!client.isConnected()) {
            return MCPPromptGetResult(
                success = false,
                description = null,
                messages = emptyList(),
                error = "Server '$serverId' is not connected"
            )
        }

        return client.getPrompt(promptName, arguments)
    }

    /**
     * Reconnect to a specific server
     */
    suspend fun reconnect(serverId: String): Boolean {
        val client = clients[serverId] ?: return false

        return try {
            logger.info("Reconnecting to MCP server: ${client.serverName}")
            client.disconnect()
            delay(1000)
            val connected = client.connect()
            if (connected) {
                logger.info("✅ Reconnected to MCP server: ${client.serverName}")
            } else {
                logger.warn("⚠️  Failed to reconnect to MCP server: ${client.serverName}")
            }
            connected
        } catch (e: Exception) {
            logger.error("Failed to reconnect to server $serverId: ${e.message}", e)
            false
        }
    }

    /**
     * Shutdown all MCP connections
     */
    suspend fun shutdown() {
        logger.info("Shutting down MCP servers...")

        clients.values.forEach { client ->
            try {
                client.disconnect()
            } catch (e: Exception) {
                logger.error("Error disconnecting from ${client.serverName}: ${e.message}", e)
            }
        }

        clients.clear()
        scope.cancel()

        logger.info("MCP servers shutdown complete")
    }

    /**
     * Get connection status for all servers
     */
    fun getConnectionStatus(): Map<String, Boolean> {
        return clients.mapValues { (_, client) -> client.isConnected() }
    }

    /**
     * Enable and connect to a specific MCP server
     */
    suspend fun enableServer(serverId: String): Boolean {
        return try {
            // Check if server config exists
            val config = serverConfigs.find { it.id == serverId }
                ?: run {
                    logger.error("Server config not found: $serverId")
                    return false
                }

            // Save to preferences
            preferencesStorage.enableServer(serverId).getOrThrow()

            // If already connected, do nothing
            if (clients[serverId]?.isConnected() == true) {
                logger.info("Server $serverId is already connected")
                return true
            }

            // Create and connect client
            val client = MCPClientWrapper(config)
            clients[serverId] = client

            val connected = client.connect()
            if (connected) {
                logger.info("✅ Enabled and connected to MCP server: ${config.name} (${config.id})")
            } else {
                logger.warn("⚠️  Enabled but failed to connect to MCP server: ${config.name} (${config.id})")
            }
            connected
        } catch (e: Exception) {
            logger.error("Failed to enable server $serverId: ${e.message}", e)
            false
        }
    }

    /**
     * Disable and disconnect from a specific MCP server
     */
    suspend fun disableServer(serverId: String): Boolean {
        return try {
            // Save to preferences
            preferencesStorage.disableServer(serverId).getOrThrow()

            // Disconnect and remove client
            val client = clients[serverId]
            if (client != null) {
                client.disconnect()
                clients.remove(serverId)
                logger.info("🔌 Disabled and disconnected from MCP server: ${client.serverName} ($serverId)")
            } else {
                logger.info("Server $serverId was not connected")
            }
            true
        } catch (e: Exception) {
            logger.error("Failed to disable server $serverId: ${e.message}", e)
            false
        }
    }

    /**
     * Check if a server is enabled in user preferences
     */
    suspend fun isServerEnabled(serverId: String): Boolean {
        return preferencesStorage.isServerEnabled(serverId)
    }
}
