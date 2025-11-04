package com.example.services

import com.example.config.ClaudeConfig
import com.example.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class ClaudeService(private val config: ClaudeConfig) {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            })
        }
    }

    suspend fun sendMessage(userMessage: String): String {
        return try {
            val claudeRequest = ClaudeRequest(
                model = config.model,
                maxTokens = config.maxTokens,
                messages = listOf(
                    ClaudeMessage(
                        role = "user",
                        content = userMessage
                    )
                ),
                temperature = config.temperature
            )

            val response: ClaudeResponse = client.post(config.apiUrl) {
                header("x-api-key", config.apiKey)
                header("anthropic-version", config.apiVersion)
                contentType(ContentType.Application.Json)
                setBody(claudeRequest)
            }.body()

            // Извлекаем текст из первого элемента content
            response.content.firstOrNull()?.text ?: "No response from Claude"

        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    fun close() {
        client.close()
    }
}
