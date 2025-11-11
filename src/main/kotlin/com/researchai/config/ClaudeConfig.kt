package com.researchai.config

data class ClaudeConfig(
    val apiKey: String,
    val apiUrl: String = "https://api.anthropic.com/v1/messages",
    val model: String = "claude-haiku-4-5-20251001",
    val maxTokens: Int = 8192,
    val temperature: Double = 1.0,
    val apiVersion: String = "2023-06-01"
)

fun getClaudeConfig(): ClaudeConfig {
    val apiKey = System.getenv("CLAUDE_API_KEY") ?: System.getProperty("CLAUDE_API_KEY")
        ?: throw IllegalStateException("CLAUDE_API_KEY environment variable is not set")

    return ClaudeConfig(
        apiKey = apiKey,
        model = System.getenv("CLAUDE_MODEL") ?: System.getProperty("CLAUDE_MODEL") ?: "claude-haiku-4-5-20251001",
        maxTokens = (System.getenv("CLAUDE_MAX_TOKENS") ?: System.getProperty("CLAUDE_MAX_TOKENS"))?.toIntOrNull() ?: 8192,
        temperature = (System.getenv("CLAUDE_TEMPERATURE") ?: System.getProperty("CLAUDE_TEMPERATURE"))?.toDoubleOrNull() ?: 1.0
    )
}
