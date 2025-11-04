package com.example.config

data class ClaudeConfig(
    val apiKey: String,
    val apiUrl: String = "https://api.anthropic.com/v1/messages",
    val model: String = "claude-3-5-sonnet-20241022",
    val maxTokens: Int = 1024,
    val temperature: Double = 1.0,
    val apiVersion: String = "2023-06-01"
)

fun getClaudeConfig(): ClaudeConfig {
    val apiKey = System.getenv("CLAUDE_API_KEY")
        ?: throw IllegalStateException("CLAUDE_API_KEY environment variable is not set")

    return ClaudeConfig(
        apiKey = apiKey,
        model = System.getenv("CLAUDE_MODEL") ?: "claude-3-5-sonnet-20241022",
        maxTokens = System.getenv("CLAUDE_MAX_TOKENS")?.toIntOrNull() ?: 1024,
        temperature = System.getenv("CLAUDE_TEMPERATURE")?.toDoubleOrNull() ?: 1.0
    )
}
