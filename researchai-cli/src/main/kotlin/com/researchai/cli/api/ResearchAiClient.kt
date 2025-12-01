package com.researchai.cli.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class ResearchAiClient(private val baseUrl: String) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 300_000 // 5 minutes for long AI responses
            connectTimeoutMillis = 10_000
        }
    }

    suspend fun sendMessage(message: String, sessionId: String?, model: String? = null): ChatResponse {
        val response = client.post("$baseUrl/chat") {
            contentType(ContentType.Application.Json)
            setBody(ChatRequest(message = message, sessionId = sessionId, model = model))
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            val errorMessage = try {
                json.decodeFromString<ErrorResponse>(errorBody).error
            } catch (e: Exception) {
                errorBody
            }
            throw RuntimeException(errorMessage)
        }

        return response.body()
    }

    suspend fun clearSession(sessionId: String) {
        client.post("$baseUrl/sessions/$sessionId/clear")
    }

    suspend fun checkHealth(): Boolean {
        return try {
            client.get("$baseUrl/health")
            true
        } catch (e: Exception) {
            false
        }
    }

    fun close() = client.close()
}

@Serializable
data class ChatRequest(
    val message: String,
    val sessionId: String? = null,
    val model: String? = null,
    val temperature: Double? = null,
    val maxTokens: Int? = null
)

@Serializable
data class ChatResponse(
    val response: String,
    val sessionId: String,
    val tokensUsed: Int? = null
)

@Serializable
data class ErrorResponse(
    val error: String
)
