package com.researchai.config

import com.researchai.domain.models.mcp.MCPServerConfig
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Loads MCP server configurations from a JSON file
 */
object MCPConfigLoader {
    private val logger = LoggerFactory.getLogger(MCPConfigLoader::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    /**
     * Load MCP server configurations from file
     * Default location: config/mcp-servers.json
     */
    fun loadMCPServers(configPath: String = "config/mcp-servers.json"): List<MCPServerConfig> {
        return try {
            val configFile = File(configPath)

            if (!configFile.exists()) {
                logger.warn("MCP config file not found: $configPath. Creating default config.")
                createDefaultConfig(configFile)
                return emptyList()
            }

            val configContent = configFile.readText()
            val servers = json.decodeFromString<MCPServersConfig>(configContent)

            logger.info("Loaded ${servers.servers.size} MCP server configurations from $configPath")

            // Substitute environment variables in config
            servers.servers.map { server ->
                server.copy(
                    command = server.command?.let { substituteEnvVars(it) },
                    args = server.args?.map { substituteEnvVars(it) },
                    env = server.env?.mapValues { substituteEnvVars(it.value) },
                    url = server.url?.let { substituteEnvVars(it) }
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to load MCP configuration: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Create a default configuration file with examples
     */
    private fun createDefaultConfig(configFile: File) {
        val defaultConfig = MCPServersConfig(
            servers = listOf(
                MCPServerConfig(
                    id = "filesystem",
                    name = "Filesystem Server",
                    description = "Access to local filesystem",
                    transport = "stdio",
                    command = "npx",
                    args = listOf("-y", "@modelcontextprotocol/server-filesystem", "/tmp"),
                    enabled = false
                ),
                MCPServerConfig(
                    id = "github",
                    name = "GitHub Server",
                    description = "Access to GitHub API",
                    transport = "stdio",
                    command = "npx",
                    args = listOf("-y", "@modelcontextprotocol/server-github"),
                    env = mapOf("GITHUB_TOKEN" to "\${GITHUB_TOKEN}"),
                    enabled = false
                ),
                MCPServerConfig(
                    id = "postgres",
                    name = "PostgreSQL Server",
                    description = "Access to PostgreSQL database",
                    transport = "stdio",
                    command = "npx",
                    args = listOf("-y", "@modelcontextprotocol/server-postgres", "\${DATABASE_URL}"),
                    enabled = false
                )
            )
        )

        try {
            configFile.parentFile?.mkdirs()
            configFile.writeText(json.encodeToString(MCPServersConfig.serializer(), defaultConfig))
            logger.info("Created default MCP configuration at: ${configFile.absolutePath}")
        } catch (e: Exception) {
            logger.error("Failed to create default MCP configuration: ${e.message}", e)
        }
    }

    /**
     * Substitute environment variables in strings (format: ${VAR_NAME})
     */
    private fun substituteEnvVars(value: String): String {
        var result = value
        val envVarPattern = """\$\{([A-Za-z0-9_]+)\}""".toRegex()

        envVarPattern.findAll(value).forEach { match ->
            val varName = match.groupValues[1]
            val envValue = System.getenv(varName) ?: System.getProperty(varName) ?: ""
            result = result.replace(match.value, envValue)
        }

        return result
    }

    /**
     * Save MCP server configurations to file
     */
    fun saveMCPServers(servers: List<MCPServerConfig>, configPath: String = "config/mcp-servers.json") {
        try {
            val configFile = File(configPath)
            configFile.parentFile?.mkdirs()

            val config = MCPServersConfig(servers)
            configFile.writeText(json.encodeToString(MCPServersConfig.serializer(), config))

            logger.info("Saved ${servers.size} MCP server configurations to $configPath")
        } catch (e: Exception) {
            logger.error("Failed to save MCP configuration: ${e.message}", e)
        }
    }
}

/**
 * Container for MCP server configurations
 */
@kotlinx.serialization.Serializable
data class MCPServersConfig(
    val servers: List<MCPServerConfig>
)

/**
 * Get MCP server configurations from environment or config file
 */
fun getMCPServers(): List<MCPServerConfig> {
    val configPath = System.getenv("MCP_CONFIG_PATH")
        ?: System.getProperty("MCP_CONFIG_PATH")
        ?: "config/mcp-servers.json"

    return MCPConfigLoader.loadMCPServers(configPath)
}
