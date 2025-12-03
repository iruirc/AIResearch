package com.researchai.cli.config

import java.io.File
import java.util.Properties

data class CliConfig(
    val serverUrl: String = "http://localhost:8080",
    val defaultModel: String? = null
) {
    companion object {
        private val configDir = File(System.getProperty("user.home"), ".researchai")
        private val configFile = File(configDir, "config.properties")

        fun load(): CliConfig {
            // Priority: 1. Environment variable, 2. Config file, 3. Default
            val envServerUrl = System.getenv("RESEARCHAI_SERVER_URL")

            if (!configFile.exists()) {
                return CliConfig(
                    serverUrl = envServerUrl ?: "http://localhost:8080"
                )
            }

            val props = Properties().apply {
                configFile.inputStream().use { load(it) }
            }

            return CliConfig(
                serverUrl = envServerUrl ?: props.getProperty("server.url", "http://localhost:8080"),
                defaultModel = props.getProperty("default.model")
            )
        }

        fun save(config: CliConfig) {
            configDir.mkdirs()

            val props = Properties().apply {
                setProperty("server.url", config.serverUrl)
                config.defaultModel?.let { setProperty("default.model", it) }
            }

            configFile.outputStream().use {
                props.store(it, "ResearchAI CLI Configuration")
            }
        }
    }
}
